import type { Response, Router } from 'express'
import { google } from 'googleapis'
import { cachePath, cacheStore } from './preview-cache.js'
import { contentVersion } from './office.js'
import { prisma } from '../../config/prisma.js'
import type { AuthRequest } from '../../middleware/auth.middleware.js'
import { getAuthedGoogleClient, PREVIEW_COPY_PREFIX } from '../google/google.service.js'
import { streamProviderFile } from './stream-file.js'
import { normalizeHeaders } from './stream-google-file.js'

/**
 * Fast previews for the mobile app:
 *  - GET /files/:id/thumbnail?s=400  small JPEG (Google's own thumbnail, cached on disk)
 *  - GET /files/:id/document-preview  a PDF of any office/Google document, converted by Google
 */

const DAY = 86_400

const GOOGLE_DOC = 'application/vnd.google-apps.document'
const GOOGLE_SHEET = 'application/vnd.google-apps.spreadsheet'
const GOOGLE_SLIDES = 'application/vnd.google-apps.presentation'
const GOOGLE_DRAWING = 'application/vnd.google-apps.drawing'

/** Office-type files Google can convert, and what to convert them into. */
function conversionTarget(mime: string, name: string): string | null {
  const ext = name.toLowerCase().split('.').pop() ?? ''
  if (/spreadsheet|excel|csv/.test(mime) || ['xls', 'xlsx', 'xlsm', 'ods', 'csv', 'tsv'].includes(ext)) return GOOGLE_SHEET
  if (/presentation|powerpoint/.test(mime) || ['ppt', 'pptx', 'odp'].includes(ext)) return GOOGLE_SLIDES
  if (/word|opendocument\.text|rtf/.test(mime) || ['doc', 'docx', 'odt', 'rtf'].includes(ext)) return GOOGLE_DOC
  return null
}

function notAvailable(res: Response, code: string, message: string) {
  return res.status(404).json({ code, message })
}

async function fetchBuffer(url: string, headers: Record<string, string>) {
  const r = await fetch(url, { headers })
  if (!r.ok) throw Object.assign(new Error(`Google returned ${r.status}`), { status: r.status })
  return { buf: Buffer.from(await r.arrayBuffer()), type: r.headers.get('content-type') ?? 'application/octet-stream' }
}

async function findFile(req: AuthRequest) {
  return prisma.file.findFirstOrThrow({
    where: { id: String(req.params.id), userId: req.user!.id },
    include: { connectedAccount: true },
  })
}

/** Many thumbnails are requested at once when a grid scrolls; keep the same request from hitting Google twice. */
const inflight = new Map<string, Promise<{ buf: Buffer; type: string } | null>>()

export function registerPreviewRoutes(router: Router) {
  router.get('/:id/thumbnail', async (req: AuthRequest, res, next) => {
    try {
      const size = Math.min(1600, Math.max(64, Math.round(Number(req.query.s) || 400)))
      const file = await findFile(req)
      res.setHeader('Cache-Control', `private, max-age=${7 * DAY}`)

      if (file.provider === 's3') {
        if (!file.mimeType.startsWith('image/')) return notAvailable(res, 'NO_THUMBNAIL', 'This file has no thumbnail.')
        return streamProviderFile(file, undefined, res, { disposition: 'inline' })
      }

      const key = `thumb-${file.id}-${size}`
      const hit = await cachePath(key)
      if (hit) return res.type('image/jpeg').sendFile(hit)

      let job = inflight.get(key)
      if (!job) {
        job = (async () => {
          const auth = await getAuthedGoogleClient(file.connectedAccount)
          const drive = google.drive({ version: 'v3', auth })
          const meta = await drive.files.get({ fileId: file.providerFileId, fields: 'thumbnailLink' })
          const link = meta.data.thumbnailLink
          if (!link) return null
          const headers = normalizeHeaders(await auth.getRequestHeaders()) as Record<string, string>
          // Links end in "=s220"; ask Google for the size the phone needs.
          const sized = /=s\d+[^/]*$/.test(link) ? link.replace(/=s\d+[^/]*$/, `=s${size}`) : `${link}=s${size}`
          const out = await fetchBuffer(sized, headers).catch(() => fetchBuffer(link, headers))
          await cacheStore(key, out.buf).catch(() => {})
          return out
        })().finally(() => inflight.delete(key))
        inflight.set(key, job)
      }
      const out = await job
      if (!out) return notAvailable(res, 'NO_THUMBNAIL', 'Google has not made a thumbnail for this file yet.')
      return res.type(out.type.startsWith('image/') ? out.type : 'image/jpeg').send(out.buf)
    } catch (error) {
      return next(error)
    }
  })

  router.get('/:id/document-preview', async (req: AuthRequest, res, next) => {
    try {
      const file = await findFile(req)
      res.setHeader('Cache-Control', `private, max-age=${DAY}`)
      if (file.mimeType === 'application/pdf') return streamProviderFile(file, req.headers.range, res, { disposition: 'inline' })
      if (file.provider === 's3') return notAvailable(res, 'PREVIEW_UNSUPPORTED', 'Previews of this file type need a Google Drive account.')

      const key = `doc-${file.id}-${await contentVersion(file)}.pdf`
      const hit = await cachePath(key)
      if (hit) return res.type('application/pdf').sendFile(hit)

      const auth = await getAuthedGoogleClient(file.connectedAccount)
      const drive = google.drive({ version: 'v3', auth })
      const headers = normalizeHeaders(await auth.getRequestHeaders()) as Record<string, string>

      const exportPdf = async (googleId: string, googleMime: string) => {
        if (googleMime === GOOGLE_SHEET) {
          // Landscape, fit to page width, with gridlines: reads much better on a phone than the default export.
          const q = 'format=pdf&portrait=false&fitw=true&gridlines=true&sheetnames=true&printtitle=false&pagenum=UNDEFINED&size=A4'
          return (await fetchBuffer(`https://docs.google.com/spreadsheets/d/${googleId}/export?${q}`, headers)).buf
        }
        const r = await drive.files.export({ fileId: googleId, mimeType: 'application/pdf' }, { responseType: 'arraybuffer' })
        return Buffer.from(r.data as ArrayBuffer)
      }

      let pdf: Buffer
      if ([GOOGLE_DOC, GOOGLE_SHEET, GOOGLE_SLIDES, GOOGLE_DRAWING].includes(file.mimeType)) {
        pdf = await exportPdf(file.providerFileId, file.mimeType)
      } else {
        const target = conversionTarget(file.mimeType, file.name)
        if (!target) return notAvailable(res, 'PREVIEW_UNSUPPORTED', 'This file type cannot be previewed.')
        // Let Google convert a temporary copy, export it as PDF, then delete the copy.
        const copy = await drive.files.copy({
          fileId: file.providerFileId,
          requestBody: { name: `${PREVIEW_COPY_PREFIX}${file.id}`, mimeType: target },
          fields: 'id',
        })
        const copyId = copy.data.id!
        try {
          pdf = await exportPdf(copyId, target)
        } finally {
          await drive.files.delete({ fileId: copyId }).catch(() => {})
        }
      }
      await cacheStore(key, pdf).catch(() => {})
      return res.type('application/pdf').send(pdf)
    } catch (error) {
      return next(error)
    }
  })
}
