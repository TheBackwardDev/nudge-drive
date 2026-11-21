import type { Router } from 'express'
import { google } from 'googleapis'
import { GetObjectCommand } from '@aws-sdk/client-s3'
import * as XLSX from 'xlsx'
import mammoth from 'mammoth'
import { z } from 'zod'
import type { ConnectedAccount, File } from '@prisma/client'
import { prisma } from '../../config/prisma.js'
import type { AuthRequest } from '../../middleware/auth.middleware.js'
import { ensureGoogleAppFolder, getAuthedGoogleClient, PREVIEW_COPY_PREFIX } from '../google/google.service.js'
import { createS3Client, getS3ConfigForAccount } from '../s3/s3.service.js'
import { normalizeHeaders } from './stream-google-file.js'
import { cacheRead, cacheStore } from './preview-cache.js'

/**
 * Mobile-friendly document views and Google editing:
 *  - GET  /files/:id/sheet-data     every sheet as rows of formatted cell text (native spreadsheet viewer)
 *  - GET  /files/:id/document-html  a reflowable HTML version of a Word / Google Doc
 *  - GET  /files/:id/edit-link      URL that opens the file in Google Docs / Sheets / Slides
 *  - POST /files/create-document    new Google Doc / Sheet / Slides / Drawing / Form
 */

type FileWithAccount = File & { connectedAccount: ConnectedAccount }

const GOOGLE = {
  document: 'application/vnd.google-apps.document',
  spreadsheet: 'application/vnd.google-apps.spreadsheet',
  presentation: 'application/vnd.google-apps.presentation',
  drawing: 'application/vnd.google-apps.drawing',
  form: 'application/vnd.google-apps.form',
} as const
const XLSX_MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
const MAX_SOURCE_BYTES = 40 * 1024 * 1024
const MAX_ROWS = 5000
const MAX_COLS = 100
const MAX_SHEETS = 30

const ext = (name: string) => name.toLowerCase().split('.').pop() ?? ''

export function isSpreadsheet(f: Pick<File, 'mimeType' | 'name'>) {
  return f.mimeType === GOOGLE.spreadsheet || /spreadsheet|excel|csv|tab-separated/.test(f.mimeType) || ['xls', 'xlsx', 'xlsm', 'ods', 'csv', 'tsv'].includes(ext(f.name))
}
export function isWordDoc(f: Pick<File, 'mimeType' | 'name'>) {
  return f.mimeType === GOOGLE.document || /wordprocessingml|msword|opendocument\.text|rtf/.test(f.mimeType) || ['doc', 'docx', 'odt', 'rtf'].includes(ext(f.name))
}

async function findFile(req: AuthRequest): Promise<FileWithAccount> {
  return prisma.file.findFirstOrThrow({ where: { id: String(req.params.id), userId: req.user!.id }, include: { connectedAccount: true } })
}

/** Changes whenever the file's content changes (also after editing it in Google Docs), so caches never go stale. */
export async function contentVersion(file: FileWithAccount): Promise<string> {
  if (file.provider === 's3') return String(file.updatedAt.getTime())
  const auth = await getAuthedGoogleClient(file.connectedAccount)
  const meta = await google.drive({ version: 'v3', auth }).files.get({ fileId: file.providerFileId, fields: 'version,modifiedTime' })
  return String(meta.data.version ?? meta.data.modifiedTime ?? file.updatedAt.getTime())
}

async function rawBytes(file: FileWithAccount): Promise<Buffer> {
  if (file.sizeBytes > BigInt(MAX_SOURCE_BYTES)) throw httpError(413, 'FILE_TOO_LARGE', 'This file is too big to preview. Open it with another app instead.')
  if (file.provider === 's3') {
    const config = await getS3ConfigForAccount(file.connectedAccountId)
    const r = await createS3Client(config).send(new GetObjectCommand({ Bucket: config.bucket, Key: file.providerFileId }))
    return Buffer.from(await r.Body!.transformToByteArray())
  }
  const auth = await getAuthedGoogleClient(file.connectedAccount)
  const headers = normalizeHeaders(await auth.getRequestHeaders()) as Record<string, string>
  const r = await fetch(`https://www.googleapis.com/drive/v3/files/${file.providerFileId}?alt=media`, { headers })
  if (!r.ok) throw httpError(502, 'GOOGLE_FILE_STREAM_FAILED', `Google returned ${r.status}`)
  return Buffer.from(await r.arrayBuffer())
}

async function googleExport(file: FileWithAccount, googleId: string, mimeType: string, as: 'buffer' | 'text') {
  const auth = await getAuthedGoogleClient(file.connectedAccount)
  const drive = google.drive({ version: 'v3', auth })
  const r = await drive.files.export({ fileId: googleId, mimeType }, { responseType: as === 'buffer' ? 'arraybuffer' : 'text' })
  return as === 'buffer' ? Buffer.from(r.data as ArrayBuffer) : String(r.data)
}

/** Converts a non-Google file with Google's converter, exports it, and deletes the temporary copy. */
async function convertWithGoogle<T>(file: FileWithAccount, target: string, run: (copyId: string) => Promise<T>): Promise<T> {
  const auth = await getAuthedGoogleClient(file.connectedAccount)
  const drive = google.drive({ version: 'v3', auth })
  const copy = await drive.files.copy({ fileId: file.providerFileId, requestBody: { name: `${PREVIEW_COPY_PREFIX}${file.id}`, mimeType: target }, fields: 'id' })
  try {
    return await run(copy.data.id!)
  } finally {
    await drive.files.delete({ fileId: copy.data.id! }).catch(() => {})
  }
}

function httpError(status: number, code: string, message: string) {
  return Object.assign(new Error(message), { status, statusCode: status, code, expose: true })
}

function sendError(res: any, e: any) {
  const status = e?.status && e.status >= 400 && e.status < 600 ? e.status : 500
  return res.status(status).json({ code: e?.code && typeof e.code === 'string' ? e.code : 'PREVIEW_FAILED', message: e?.expose ? e.message : 'Could not open this file.' })
}

type SheetJson = { name: string; rows: string[][]; colWidths: (number | null)[]; truncated: boolean }

function workbookToJson(buf: Buffer, csvLike: boolean): { sheets: SheetJson[] } {
  const wb = XLSX.read(buf, { type: 'buffer', cellDates: true, sheetRows: MAX_ROWS + 1, dense: true, ...(csvLike ? { raw: false } : {}) })
  const sheets: SheetJson[] = []
  for (const name of wb.SheetNames.slice(0, MAX_SHEETS)) {
    const ws = wb.Sheets[name]
    if (!ws) continue
    const rows = (XLSX.utils.sheet_to_json(ws, { header: 1, raw: false, defval: '', blankrows: true }) as unknown[][])
      .slice(0, MAX_ROWS)
      .map((r) => r.slice(0, MAX_COLS).map((v) => (v === null || v === undefined ? '' : String(v))))
    // Drop trailing empty rows/columns so the grid is no bigger than the data.
    while (rows.length && rows[rows.length - 1]!.every((v) => v === '')) rows.pop()
    const width = rows.reduce((m, r) => { let n = r.length; while (n > 0 && r[n - 1] === '') n--; return Math.max(m, n) }, 0)
    const trimmed = rows.map((r) => { const out = r.slice(0, width); while (out.length < width) out.push(''); return out })
    const cols = (ws['!cols'] ?? []) as { wpx?: number; wch?: number }[]
    const colWidths = Array.from({ length: width }, (_, i) => cols[i]?.wpx ?? (cols[i]?.wch ? cols[i]!.wch! * 7 : null))
    const range = ws['!ref'] ? XLSX.utils.decode_range(ws['!ref']) : null
    const truncated = !!range && (range.e.r + 1 > MAX_ROWS || range.e.c + 1 > MAX_COLS)
    sheets.push({ name, rows: trimmed, colWidths, truncated })
  }
  return { sheets }
}

const mobileDocCss = `
img{max-width:100%!important;height:auto!important}
table{border-collapse:collapse;max-width:100%}
td,th{border:1px solid #d6d1e6;padding:4px 6px;vertical-align:top}
`

export function registerOfficeRoutes(router: Router) {
  router.get('/:id/sheet-data', async (req: AuthRequest, res) => {
    try {
      const file = await findFile(req)
      if (!isSpreadsheet(file)) return res.status(415).json({ code: 'NOT_A_SPREADSHEET', message: 'This file is not a spreadsheet.' })
      const key = `sheet-${file.id}-${await contentVersion(file)}.json`
      const hit = await cacheRead(key)
      if (hit) return res.type('application/json').send(hit)
      const buf = file.mimeType === GOOGLE.spreadsheet ? (await googleExport(file, file.providerFileId, XLSX_MIME, 'buffer')) as Buffer : await rawBytes(file)
      const csvLike = /csv|tab-separated/.test(file.mimeType) || ['csv', 'tsv'].includes(ext(file.name))
      const json = Buffer.from(JSON.stringify(workbookToJson(buf, csvLike)))
      await cacheStore(key, json).catch(() => {})
      return res.type('application/json').send(json)
    } catch (e) {
      return sendError(res, e)
    }
  })

  router.get('/:id/document-html', async (req: AuthRequest, res) => {
    try {
      const file = await findFile(req)
      if (!isWordDoc(file)) return res.status(415).json({ code: 'NOT_A_DOCUMENT', message: 'This file is not a text document.' })
      const key = `html-${file.id}-${await contentVersion(file)}.html`
      const hit = await cacheRead(key)
      if (hit) return res.type('text/html; charset=utf-8').send(hit)
      let html: string
      if (file.mimeType === GOOGLE.document) {
        html = (await googleExport(file, file.providerFileId, 'text/html', 'text')) as string
      } else if (ext(file.name) === 'docx' || file.mimeType.includes('wordprocessingml')) {
        const out = await mammoth.convertToHtml({ buffer: await rawBytes(file) })
        html = `<html><head><style>${mobileDocCss}</style></head><body>${out.value}</body></html>`
      } else if (file.provider === 'google_drive') {
        html = await convertWithGoogle(file, GOOGLE.document, async (id) => (await googleExport(file, id, 'text/html', 'text')) as string)
      } else {
        return res.status(415).json({ code: 'PREVIEW_UNSUPPORTED', message: 'Old Word files on S3 can only be opened with another app.' })
      }
      const buf = Buffer.from(html)
      await cacheStore(key, buf).catch(() => {})
      return res.type('text/html; charset=utf-8').send(buf)
    } catch (e) {
      return sendError(res, e)
    }
  })

  router.get('/:id/edit-link', async (req: AuthRequest, res) => {
    try {
      const file = await findFile(req)
      if (file.provider !== 'google_drive') return res.status(415).json({ code: 'EDIT_NEEDS_GOOGLE', message: 'Editing works for files stored on Google Drive.' })
      const auth = await getAuthedGoogleClient(file.connectedAccount)
      const meta = await google.drive({ version: 'v3', auth }).files.get({ fileId: file.providerFileId, fields: 'webViewLink' })
      let url = meta.data.webViewLink
      if (!url) return res.status(404).json({ code: 'NO_EDIT_LINK', message: 'Google did not return a link for this file.' })
      const email = file.connectedAccount.email
      if (email && email.includes('@')) url += (url.includes('?') ? '&' : '?') + `authuser=${encodeURIComponent(email)}`
      return res.json({ url, account: email })
    } catch (e) {
      return sendError(res, e)
    }
  })

  const createSchema = z.object({
    kind: z.enum(['document', 'spreadsheet', 'presentation', 'drawing', 'form']),
    name: z.string().trim().min(1).max(200),
    folderId: z.string().nullable().optional(),
    accountId: z.string().nullable().optional(),
  })

  router.post('/create-document', async (req: AuthRequest, res) => {
    try {
      const body = createSchema.parse(req.body)
      const userId = req.user!.id
      const accounts = await prisma.connectedAccount.findMany({
        where: { userId, provider: 'google_drive', status: 'connected', ...(body.accountId ? { id: body.accountId } : {}) },
        include: { storageAccount: true },
      })
      if (accounts.length === 0) return res.status(409).json({ code: 'NO_GOOGLE_ACCOUNT', message: 'Connect a Google Drive account to create Google files.' })
      const account = accounts.sort((a, b) => Number((b.storageAccount?.availableBytes ?? 0n) - (a.storageAccount?.availableBytes ?? 0n)))[0]!
      const folder = body.folderId ? await prisma.folder.findFirst({ where: { id: body.folderId, userId } }) : null
      const parent = folder?.providerFolderId && folder.connectedAccountId === account.id ? folder.providerFolderId : await ensureGoogleAppFolder(account)
      const auth = await getAuthedGoogleClient(account)
      const created = await google.drive({ version: 'v3', auth }).files.create({
        requestBody: { name: body.name, mimeType: GOOGLE[body.kind], parents: [parent] },
        fields: 'id,name,mimeType,webViewLink',
      })
      const file = await prisma.file.create({
        data: {
          userId, connectedAccountId: account.id, folderId: folder?.id ?? null, provider: 'google_drive',
          providerFileId: created.data.id!, name: created.data.name ?? body.name, mimeType: created.data.mimeType ?? GOOGLE[body.kind], sizeBytes: 0n,
        },
        include: { connectedAccount: { select: { id: true, email: true, provider: true } }, folder: { select: { id: true, name: true } } },
      })
      let url = created.data.webViewLink ?? null
      if (url && account.email.includes('@')) url += (url.includes('?') ? '&' : '?') + `authuser=${encodeURIComponent(account.email)}`
      return res.status(201).json({ file: { ...file, sizeBytes: file.sizeBytes.toString() }, url })
    } catch (e: any) {
      if (e?.name === 'ZodError') return res.status(400).json({ code: 'VALIDATION_ERROR', message: 'Give the file a name.' })
      return sendError(res, e)
    }
  })
}
