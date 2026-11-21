import type { Router } from 'express'
import type { ConnectedAccount } from '@prisma/client'
import { PassThrough, Readable } from 'node:stream'
import { z } from 'zod'
import { prisma } from '../../config/prisma.js'
import type { AuthRequest } from '../../middleware/auth.middleware.js'
import { createAuditLog } from '../../utils/audit.js'
import { encryptText } from '../../utils/crypto.js'
import { exchange, NativeGoogleError } from '../auth/google-native.js'
import { ensureGoogleAppFolder, getAuthedGoogleClient, syncGoogleQuota } from '../google/google.service.js'
import { buildS3ObjectKey, getS3ConfigForAccount, syncS3Quota, uploadS3Object } from '../s3/s3.service.js'
import { destinations, driveFor, fail, jobJson, jobs, pickDestination, sourceAccount, startExclusive, type Destination, type Job } from './free-up.js'

/**
 * Free up space, Google Photos edition. Most full 15 GB accounts are full of Google Photos, which the
 * Drive API can't see. Google only lets apps read photos the user picks in Google's own picker
 * (Photos Picker API), and no app may delete from Google Photos. So: the user picks, Nudge copies the
 * originals to their other drives (into a "Google Photos · name" folder), and the app then walks the
 * user through deleting them in Google Photos to get the space back.
 */

export const PHOTOS_SCOPE = 'https://www.googleapis.com/auth/photospicker.mediaitems.readonly'
const PICKER = 'https://photospicker.googleapis.com/v1'
/** Picker download URLs stop working after 60 minutes; re-list well before that. */
const URL_TTL_MS = 45 * 60_000
/** Used to choose a drive when Google doesn't say how big a file is. */
const UNKNOWN_SIZE = 64n * 1024n * 1024n

class PhotosError extends Error {
  constructor(public status: number, public code: string, message: string) { super(message) }
}

type PickedItem = {
  id: string
  createTime?: string
  type?: string
  mediaFile?: { baseUrl?: string; mimeType?: string; filename?: string; mediaFileMetadata?: { videoMetadata?: { processingStatus?: string } } }
}
type PickerSession = { id: string; pickerUri?: string; mediaItemsSet?: boolean; expireTime?: string; pollingConfig?: { pollInterval?: string; timeoutIn?: string } }

/** Sessions this server created, so one user can't read another user's picks. */
const sessions = new Map<string, { userId: string; accountId: string; expires: number }>()
const SESSION_TTL = 6 * 3600_000

function rememberSession(id: string, userId: string, accountId: string) {
  const now = Date.now()
  for (const [k, v] of sessions) if (v.expires < now) sessions.delete(k)
  sessions.set(id, { userId, accountId, expires: now + SESSION_TTL })
}

async function accessToken(account: ConnectedAccount) {
  const auth = await getAuthedGoogleClient(account)
  const { token } = await auth.getAccessToken()
  if (!token) throw new PhotosError(403, 'PHOTOS_ACCESS_NEEDED', 'Nudge needs permission to read the photos you pick.')
  return token
}

async function picker<T>(account: ConnectedAccount, path: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(PICKER + path, { ...init, headers: { Authorization: `Bearer ${await accessToken(account)}`, 'Content-Type': 'application/json' } })
  const body = (await res.json().catch(() => ({}))) as { error?: { message?: string; status?: string } }
  if (!res.ok) {
    const message = body.error?.message ?? res.statusText
    console.warn('[photos] picker', init.method ?? 'GET', path.split('?')[0], res.status, message)
    if (/has not been used|is disabled|SERVICE_DISABLED/i.test(message)) {
      throw new PhotosError(503, 'PHOTOS_API_DISABLED', 'Google Photos isn\'t switched on for this Nudge server yet. (Admin: enable the "Google Photos Picker API" in Google Cloud.)')
    }
    if (res.status === 401 || res.status === 403) throw new PhotosError(403, 'PHOTOS_ACCESS_NEEDED', 'Nudge needs permission to read the photos you pick.')
    if (res.status === 404) throw new PhotosError(404, 'PHOTOS_SESSION_GONE', 'That Google Photos selection expired. Please pick again.')
    if (body.error?.status === 'FAILED_PRECONDITION') throw new PhotosError(409, 'PHOTOS_NOT_PICKED', 'Pick your photos and videos in Google Photos first.')
    throw new PhotosError(502, 'PHOTOS_FAILED', 'Google Photos didn\'t answer. Please try again.')
  }
  return body as T
}

/** "5s" / "3.5s" → ms */
const duration = (v?: string, fallback = 4000) => {
  const n = v ? parseFloat(v) : NaN
  return Number.isFinite(n) ? Math.max(2000, Math.round(n * 1000)) : fallback
}

function sessionJson(s: PickerSession) {
  return { id: s.id, pickerUri: s.pickerUri ?? null, mediaItemsSet: !!s.mediaItemsSet, pollIntervalMs: duration(s.pollingConfig?.pollInterval), expireTime: s.expireTime ?? null }
}

async function listPicked(account: ConnectedAccount, sessionId: string) {
  const items: PickedItem[] = []
  let pageToken: string | undefined
  do {
    const qs = new URLSearchParams({ sessionId, pageSize: '100', ...(pageToken ? { pageToken } : {}) })
    const page = await picker<{ mediaItems?: PickedItem[]; nextPageToken?: string }>(account, `/mediaItems?${qs}`)
    items.push(...(page.mediaItems ?? []))
    pageToken = page.nextPageToken || undefined
  } while (pageToken)
  return items
}

function isVideo(item: PickedItem) {
  return item.type === 'VIDEO' || (item.mediaFile?.mimeType ?? '').startsWith('video/')
}

/** The Nudge folder the copies land in (Nudge-side only, like every Nudge folder). */
export const photosFolderName = (email: string) => `Google Photos · ${email.split('@')[0]}`

async function photosFolder(userId: string, source: ConnectedAccount) {
  const name = photosFolderName(source.email)
  const existing = await prisma.folder.findFirst({ where: { userId, name, parentId: null, deletedAt: null } })
  return existing ?? prisma.folder.create({
    data: { userId, name, color: '#f4b400', iconUrl: 'https://api.iconify.design/lucide:images.svg', connectedAccountId: null, providerFolderId: null },
  })
}

function counter(source: Readable) {
  let bytes = 0n
  const body = new PassThrough()
  source.on('data', (chunk: Buffer) => { bytes += BigInt(chunk.length) })
  source.on('error', (error) => body.destroy(error))
  source.pipe(body)
  return { body, bytes: () => bytes }
}

async function runPhotosJob(job: Job, source: ConnectedAccount, sessionId: string, all: Destination[], chosenId: string | null) {
  const folder = await photosFolder(job.userId, source)
  job.folderName = folder.name
  let items = await listPicked(source, sessionId)
  let listedAt = Date.now()
  // Copies from an earlier run (same name + same moment) are skipped, so picking twice is harmless.
  const already = new Set((await prisma.file.findMany({ where: { userId: job.userId, folderId: folder.id, status: 'active' }, select: { name: true, takenAt: true } }))
    .map((f) => `${f.name}|${f.takenAt?.getTime() ?? ''}`))
  const queue = items
    .filter((i) => i.mediaFile?.baseUrl)
    .sort((a, b) => Number(isVideo(b)) - Number(isVideo(a))) // videos first: they are the big ones
  job.totalFiles = queue.length
  let refreshing: Promise<void> | null = null
  const freshUrl = async (item: PickedItem) => {
    if (Date.now() - listedAt > URL_TTL_MS) {
      refreshing ??= listPicked(source, sessionId).then((fresh) => { items = fresh; listedAt = Date.now() }).finally(() => { refreshing = null })
      await refreshing
    }
    return items.find((i) => i.id === item.id)?.mediaFile?.baseUrl ?? item.mediaFile!.baseUrl!
  }
  let stopForSpace = false
  const seen = (d: Date | null) => {
    if (!d || isNaN(d.getTime())) return
    if (!job.takenFrom || d < job.takenFrom) job.takenFrom = d
    if (!job.takenTo || d > job.takenTo) job.takenTo = d
  }

  const worker = async () => {
    for (;;) {
      if (job.cancel || stopForSpace) return
      const item = queue.shift()
      if (!item) return
      const file = item.mediaFile!
      const video = isVideo(item)
      const name = file.filename || `${video ? 'video' : 'photo'}-${item.id.slice(0, 8)}`
      const mimeType = file.mimeType || (video ? 'video/mp4' : 'image/jpeg')
      const takenAt = item.createTime ? new Date(item.createTime) : null
      if (already.has(`${name}|${takenAt?.getTime() ?? ''}`)) { job.skipped += 1; job.doneFiles += 1; seen(takenAt); continue }
      if (video && file.mediaFileMetadata?.videoMetadata?.processingStatus && file.mediaFileMetadata.videoMetadata.processingStatus !== 'READY') {
        job.failures.push({ name, reason: 'Google Photos is still processing this video. Try it again later.' })
        continue
      }
      job.current.push(name)
      let download: Readable | null = null
      try {
        const res = await fetch(`${await freshUrl(item)}=${video ? 'dv' : 'd'}`, { headers: { Authorization: `Bearer ${await accessToken(source)}` } })
        if (!res.ok || !res.body) throw new Error(`download failed (${res.status})`)
        download = Readable.fromWeb(res.body as import('node:stream/web').ReadableStream)
        const length = res.headers.get('content-length')
        const size = length ? BigInt(length) : UNKNOWN_SIZE
        const dest = pickDestination(all, { mimeType, sizeBytes: size }, chosenId)
        if (!dest) {
          download.destroy()
          job.failures.push({ name, reason: 'None of your other drives has enough free space.' })
          job.outOfSpace = true
          if (!all.some((d) => d.available === null || d.available > UNKNOWN_SIZE)) stopForSpace = true
          continue
        }
        if (dest.available !== null) dest.available -= size
        const counted = counter(download)
        const id = crypto.randomUUID()
        let providerFileId: string
        try {
          if (dest.account.provider === 's3') {
            const config = await getS3ConfigForAccount(dest.account.id, job.userId)
            providerFileId = buildS3ObjectKey(config, job.userId, id, name)
            await uploadS3Object(config, providerFileId, counted.body, mimeType)
          } else {
            dest.drive ??= await driveFor(dest.account)
            dest.folderId ??= await ensureGoogleAppFolder(dest.account)
            const created = await dest.drive.files.create({
              requestBody: { name, parents: [dest.folderId], ...(takenAt ? { modifiedTime: takenAt.toISOString() } : {}) },
              media: { mimeType, body: counted.body },
              fields: 'id',
            })
            if (!created.data.id) throw new Error('Google did not return the new file.')
            providerFileId = created.data.id
          }
        } catch (error) {
          if (dest.available !== null) dest.available += size
          throw error
        }
        const bytes = counted.bytes()
        if (dest.available !== null) dest.available += size - bytes // true size instead of the guess
        await prisma.file.create({
          data: {
            id, userId: job.userId, connectedAccountId: dest.account.id, provider: dest.account.provider, providerFileId,
            name, mimeType, sizeBytes: bytes, status: 'active', folderId: folder.id, takenAt,
          },
        })
        already.add(`${name}|${takenAt?.getTime() ?? ''}`)
        seen(takenAt)
        job.doneFiles += 1
        job.movedBytes += bytes
        const d = job.destinations.get(dest.account.id) ?? { id: dest.account.id, email: dest.account.email, provider: dest.account.provider, files: 0, bytes: 0n }
        d.files += 1
        d.bytes += bytes
        job.destinations.set(dest.account.id, d)
      } catch (error) {
        download?.destroy()
        const message = error instanceof Error ? error.message : String(error)
        console.error('[photos] copy failed', item.id, message)
        job.failures.push({ name, reason: /storageQuotaExceeded|quota/i.test(message) ? 'The destination drive is full.' : 'Could not be copied. It is still in Google Photos.' })
      } finally {
        job.current = job.current.filter((n) => n !== name)
      }
    }
  }

  await Promise.all([worker(), worker(), worker()])
  for (const d of job.destinations.values()) await (d.provider === 's3' ? syncS3Quota(d.id) : syncGoogleQuota(d.id)).catch(() => undefined)
  await createAuditLog(job.userId, 'COPY_GOOGLE_PHOTOS', 'connected_account', job.sourceId, { files: job.doneFiles, skipped: job.skipped, bytes: job.movedBytes.toString(), failed: job.failures.length })
  await picker(source, `/sessions/${sessionId}`, { method: 'DELETE' }).catch(() => undefined)
  sessions.delete(sessionId)
}

function photosFail(res: import('express').Response, next: import('express').NextFunction, error: unknown) {
  if (error instanceof PhotosError || error instanceof NativeGoogleError) return fail(res, error.status, error.code, error.message)
  return next(error)
}

export function registerPhotos(router: Router) {
  /** The app got a one-time code from Google's consent sheet that adds "read the photos I pick" to this account. */
  router.post('/free-up/:accountId/photos/grant', async (req: AuthRequest, res, next) => {
    try {
      const account = await sourceAccount(req)
      const { code } = z.object({ code: z.string().min(10).max(2048) }).parse(req.body)
      const { tokens, profile } = await exchange(code)
      if (profile.id !== account.providerAccountId) {
        return fail(res, 400, 'PHOTOS_WRONG_ACCOUNT', `That was a different Google account. Please choose ${account.email}.`)
      }
      const granted = (tokens.scope ?? '').split(' ').filter(Boolean)
      if (!granted.includes(PHOTOS_SCOPE)) return fail(res, 400, 'PHOTOS_NOT_ALLOWED', 'Nudge needs permission to see the photos you pick. Please try again and allow it.')
      await prisma.connectedAccount.update({
        where: { id: account.id },
        data: {
          accessTokenEncrypted: encryptText(tokens.access_token),
          ...(tokens.refresh_token ? { refreshTokenEncrypted: encryptText(tokens.refresh_token) } : {}),
          tokenExpiresAt: new Date(Date.now() + (tokens.expires_in ?? 3600) * 1000),
          scopes: granted,
        },
      })
      return res.json({ status: 'ok' })
    } catch (error) {
      return photosFail(res, next, error)
    }
  })

  /** Opens a Google Photos picker for this account; the app shows pickerUri. */
  router.post('/free-up/:accountId/photos/session', async (req: AuthRequest, res, next) => {
    try {
      const account = await sourceAccount(req)
      const session = await picker<PickerSession>(account, '/sessions', { method: 'POST', body: JSON.stringify({ pickingConfig: { maxItemCount: '2000' } }) })
      rememberSession(session.id, req.user!.id, account.id)
      return res.json({ session: sessionJson(session) })
    } catch (error) {
      return photosFail(res, next, error)
    }
  })

  router.get('/free-up/:accountId/photos/session/:sessionId', async (req: AuthRequest, res, next) => {
    try {
      const account = await sourceAccount(req)
      const sessionId = String(req.params.sessionId)
      const known = sessions.get(sessionId)
      if (!known || known.userId !== req.user!.id || known.accountId !== account.id) return fail(res, 404, 'PHOTOS_SESSION_GONE', 'That Google Photos selection expired. Please pick again.')
      return res.json({ session: sessionJson(await picker<PickerSession>(account, `/sessions/${encodeURIComponent(sessionId)}`)) })
    } catch (error) {
      return photosFail(res, next, error)
    }
  })

  /** Copies everything picked in that session to the user's other drives. */
  router.post('/free-up/:accountId/photos/start', async (req: AuthRequest, res, next) => {
    try {
      const account = await sourceAccount(req)
      const body = z.object({ sessionId: z.string().min(1), destinationId: z.string().nullable().optional() }).parse(req.body)
      const started = await startExclusive(account.id, () => startPhotos(req, res, account, body))
      if (started === null) return res.json({ job: jobJson(jobs.get(account.id)) })
      return started
    } catch (error) {
      return photosFail(res, next, error)
    }
  })

  async function startPhotos(req: AuthRequest, res: import('express').Response, account: Awaited<ReturnType<typeof sourceAccount>>, body: { sessionId: string; destinationId?: string | null }) {
    {
      const known = sessions.get(body.sessionId)
      if (!known || known.userId !== req.user!.id || known.accountId !== account.id) return fail(res, 404, 'PHOTOS_SESSION_GONE', 'That Google Photos selection expired. Please pick again.')
      const all = await destinations(req.user!.id, account.id)
      if (body.destinationId && !all.some((d) => d.account.id === body.destinationId)) return fail(res, 400, 'FREE_UP_BAD_DESTINATION', 'That drive is not connected any more.')
      if (all.length === 0) return fail(res, 400, 'FREE_UP_NO_DESTINATION', 'Connect another Google Drive or an S3 bucket first. Your photos need somewhere to go.')
      const state = await picker<PickerSession>(account, `/sessions/${encodeURIComponent(body.sessionId)}`)
      if (!state.mediaItemsSet) return fail(res, 409, 'PHOTOS_NOT_PICKED', 'Pick your photos and videos in Google Photos first.')

      const job: Job = {
        id: crypto.randomUUID(), kind: 'photos', skipped: 0, folderName: null, userId: req.user!.id, sourceId: account.id, state: 'running',
        totalFiles: 0, totalBytes: 0n, doneFiles: 0, movedBytes: 0n, current: [], destinations: new Map(), failures: [], outOfSpace: false,
        usedBefore: null, usedNow: null, cancel: false, startedAt: new Date().toISOString(),
      }
      jobs.set(account.id, job)
      runPhotosJob(job, account, body.sessionId, all, body.destinationId ?? null)
        .then(() => { job.state = job.cancel ? 'cancelled' : 'done' })
        .catch((error: unknown) => {
          job.state = 'failed'
          job.error = error instanceof PhotosError ? error.message : 'Copying stopped. Everything is still in Google Photos.'
          console.error('[photos] job failed', error)
        })
        .finally(() => { job.finishedAt = new Date().toISOString() })
      return res.status(202).json({ job: jobJson(job) })
    }
  }
}
