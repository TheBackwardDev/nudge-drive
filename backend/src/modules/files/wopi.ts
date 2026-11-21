import express, { Router } from 'express'
import jwt from 'jsonwebtoken'
import { google } from 'googleapis'
import { Readable } from 'node:stream'
import { GetObjectCommand, PutObjectCommand } from '@aws-sdk/client-s3'
import type { ConnectedAccount, File } from '@prisma/client'
import { env } from '../../config/env.js'
import { prisma } from '../../config/prisma.js'
import type { AuthRequest } from '../../middleware/auth.middleware.js'
import { getAuthedGoogleClient } from '../google/google.service.js'
import { createS3Client, getS3ConfigForAccount } from '../s3/s3.service.js'
import { normalizeHeaders } from './stream-google-file.js'

/**
 * In-app editing with a self-hosted Collabora Online (CODE) server.
 * Collabora talks to us over WOPI: CheckFileInfo / GetFile / PutFile.
 * Google Docs/Sheets/Slides are edited as .docx/.xlsx/.pptx and written back into the same
 * Google file (Drive converts them), so they stay Google files.
 */

type FileWithAccount = File & { connectedAccount: ConnectedAccount }

const COLLABORA_INTERNAL = process.env.COLLABORA_URL || 'http://collabora:9980'
const WOPI_BASE = process.env.WOPI_BASE || 'http://backend:4000/wopi/files'
const PUBLIC_ORIGIN = env.FRONTEND_URL.replace(/\/$/, '')
const TOKEN_TTL_S = 10 * 3600
const MAX_BYTES = 100 * 1024 * 1024

const GOOGLE_NATIVE: Record<string, { ext: string; mime: string }> = {
  'application/vnd.google-apps.document': { ext: 'docx', mime: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' },
  'application/vnd.google-apps.spreadsheet': { ext: 'xlsx', mime: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' },
  'application/vnd.google-apps.presentation': { ext: 'pptx', mime: 'application/vnd.openxmlformats-officedocument.presentationml.presentation' },
}

/** The name Collabora sees (its extension decides which editor opens). */
function editName(f: File) {
  const native = GOOGLE_NATIVE[f.mimeType]
  if (!native) return f.name
  return f.name.toLowerCase().endsWith('.' + native.ext) ? f.name : `${f.name}.${native.ext}`
}
const extOf = (name: string) => name.toLowerCase().split('.').pop() ?? ''

// ------------------------------------------------------------------ discovery

let discovery: { at: number; edit: Map<string, string>; view: Map<string, string> } | null = null
async function getDiscovery() {
  if (discovery && Date.now() - discovery.at < 3600_000) return discovery
  const xml = await (await fetch(`${COLLABORA_INTERNAL}/hosting/discovery`)).text()
  const edit = new Map<string, string>(); const view = new Map<string, string>()
  for (const m of xml.matchAll(/<action\s+([^>]*?)\/?>/g)) {
    const attrs = Object.fromEntries([...m[1]!.matchAll(/(\w+)="([^"]*)"/g)].map((a) => [a[1]!, a[2]!]))
    if (!attrs.ext || !attrs.urlsrc) continue
    if (attrs.name === 'edit') edit.set(attrs.ext, attrs.urlsrc)
    else if (attrs.name === 'view' && !view.has(attrs.ext)) view.set(attrs.ext, attrs.urlsrc)
  }
  discovery = { at: Date.now(), edit, view }
  return discovery
}

// ------------------------------------------------------------------ tokens

type WopiClaims = { sub: string; fid: string; typ: 'wopi' }
function signToken(userId: string, fileId: string) {
  return jwt.sign({ sub: userId, fid: fileId, typ: 'wopi' } satisfies WopiClaims, env.JWT_ACCESS_SECRET, { expiresIn: TOKEN_TTL_S })
}
function readToken(token: unknown, fileId: string): WopiClaims | null {
  if (typeof token !== 'string') return null
  try {
    const c = jwt.verify(token, env.JWT_ACCESS_SECRET) as WopiClaims
    return c.typ === 'wopi' && c.fid === fileId ? c : null
  } catch { return null }
}

async function loadFile(req: express.Request): Promise<{ file: FileWithAccount; userId: string } | null> {
  const id = String(req.params.id)
  const claims = readToken(req.query.access_token, id)
  if (!claims) return null
  const file = await prisma.file.findFirst({ where: { id, userId: claims.sub, status: 'active' }, include: { connectedAccount: true } })
  return file ? { file, userId: claims.sub } : null
}

// ------------------------------------------------------------------ content

async function readContent(f: FileWithAccount): Promise<Buffer> {
  if (f.provider === 's3') {
    const config = await getS3ConfigForAccount(f.connectedAccountId)
    const r = await createS3Client(config).send(new GetObjectCommand({ Bucket: config.bucket, Key: f.providerFileId }))
    return Buffer.from(await r.Body!.transformToByteArray())
  }
  const auth = await getAuthedGoogleClient(f.connectedAccount)
  const native = GOOGLE_NATIVE[f.mimeType]
  if (native) {
    const r = await google.drive({ version: 'v3', auth }).files.export({ fileId: f.providerFileId, mimeType: native.mime }, { responseType: 'arraybuffer' })
    return Buffer.from(r.data as ArrayBuffer)
  }
  const headers = normalizeHeaders(await auth.getRequestHeaders()) as Record<string, string>
  const r = await fetch(`https://www.googleapis.com/drive/v3/files/${f.providerFileId}?alt=media`, { headers })
  if (!r.ok) throw new Error(`Google returned ${r.status}`)
  return Buffer.from(await r.arrayBuffer())
}

async function writeContent(f: FileWithAccount, body: Buffer) {
  if (f.provider === 's3') {
    const config = await getS3ConfigForAccount(f.connectedAccountId)
    await createS3Client(config).send(new PutObjectCommand({ Bucket: config.bucket, Key: f.providerFileId, Body: body, ContentType: f.mimeType }))
    return
  }
  const auth = await getAuthedGoogleClient(f.connectedAccount)
  const mime = GOOGLE_NATIVE[f.mimeType]?.mime ?? f.mimeType
  await google.drive({ version: 'v3', auth }).files.update({ fileId: f.providerFileId, media: { mimeType: mime, body: Readable.from(body) }, fields: 'id' })
}

// ------------------------------------------------------------------ routes

/** Public WOPI endpoints, called by the Collabora server (authorised by the per-file access_token). */
export const wopiRouter = Router()

wopiRouter.get('/files/:id', async (req, res) => {
  const ctx = await loadFile(req).catch(() => null)
  if (!ctx) return res.sendStatus(401)
  const { file, userId } = ctx
  const user = await prisma.user.findUnique({ where: { id: userId }, select: { name: true } })
  return res.json({
    BaseFileName: editName(file),
    Size: Number(file.sizeBytes),
    OwnerId: userId,
    UserId: userId,
    UserFriendlyName: user?.name ?? 'Nudge user',
    Version: String(file.updatedAt.getTime()),
    LastModifiedTime: file.updatedAt.toISOString(),
    UserCanWrite: true,
    SupportsUpdate: true,
    SupportsLocks: false,
    UserCanNotWriteRelative: true,
    PostMessageOrigin: PUBLIC_ORIGIN,
    HideExportOption: false,
    EnableShare: false,
    HideUserList: 'true',
    DisableInactiveMessages: true,
  })
})

wopiRouter.get('/files/:id/contents', async (req, res) => {
  const ctx = await loadFile(req).catch(() => null)
  if (!ctx) return res.sendStatus(401)
  try {
    const buf = await readContent(ctx.file)
    res.setHeader('Content-Type', 'application/octet-stream')
    return res.send(buf)
  } catch (e: any) {
    console.error('[wopi] GetFile failed', e?.message)
    return res.sendStatus(500)
  }
})

wopiRouter.post('/files/:id/contents', express.raw({ type: '*/*', limit: MAX_BYTES }), async (req, res) => {
  const ctx = await loadFile(req).catch(() => null)
  if (!ctx) return res.sendStatus(401)
  const body = Buffer.isBuffer(req.body) ? req.body : Buffer.alloc(0)
  try {
    await writeContent(ctx.file, body)
    const updated = await prisma.file.update({ where: { id: ctx.file.id }, data: { sizeBytes: BigInt(body.length) } })
    return res.json({ LastModifiedTime: updated.updatedAt.toISOString() })
  } catch (e: any) {
    console.error('[wopi] PutFile failed', e?.message)
    return res.sendStatus(500)
  }
})

// Lock operations are not used (SupportsLocks=false) but answer politely if called.
wopiRouter.post('/files/:id', async (req, res) => {
  const ctx = await loadFile(req).catch(() => null)
  if (!ctx) return res.sendStatus(401)
  const op = String(req.header('X-WOPI-Override') || '').toUpperCase()
  if (['LOCK', 'UNLOCK', 'REFRESH_LOCK', 'GET_LOCK'].includes(op)) return res.sendStatus(200)
  return res.sendStatus(501)
})

/** POST /files/:id/office-session (signed-in user) → where to load the editor and with which token. */
export function registerOfficeSession(router: Router) {
  router.post('/:id/office-session', async (req: AuthRequest, res) => {
    try {
      const file = await prisma.file.findFirst({ where: { id: String(req.params.id), userId: req.user!.id, status: 'active' } })
      if (!file) return res.status(404).json({ code: 'FILE_NOT_FOUND', message: 'File not found.' })
      const ext = extOf(editName(file))
      const d = await getDiscovery().catch(() => null)
      if (!d) return res.status(503).json({ code: 'EDITOR_OFFLINE', message: 'The editor is starting up. Try again in a moment.' })
      const urlsrc = d.edit.get(ext)
      if (!urlsrc) return res.status(415).json({ code: 'EDIT_UNSUPPORTED', message: `.${ext} files can't be edited in the app.` })
      const token = signToken(req.user!.id, file.id)
      const src = encodeURIComponent(`${WOPI_BASE}/${file.id}`)
      const url = `${urlsrc}${urlsrc.includes('?') && !urlsrc.endsWith('?') && !urlsrc.endsWith('&') ? '&' : ''}WOPISrc=${src}&lang=en-US&closebutton=1`
      return res.json({ url, accessToken: token, accessTokenTtl: Date.now() + TOKEN_TTL_S * 1000, fileName: editName(file) })
    } catch (e: any) {
      return res.status(500).json({ code: 'EDITOR_FAILED', message: 'Could not open the editor.' })
    }
  })
}
