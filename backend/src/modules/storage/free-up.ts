import type { Router, Response, NextFunction } from 'express'
import type { ConnectedAccount, File } from '@prisma/client'
import { google, type drive_v3 } from 'googleapis'
import type { Readable } from 'node:stream'
import { z } from 'zod'
import { prisma } from '../../config/prisma.js'
import type { AuthRequest } from '../../middleware/auth.middleware.js'
import { createAuditLog } from '../../utils/audit.js'
import { ensureGoogleAppFolder, getAuthedGoogleClient, getImportStatus, PREVIEW_COPY_PREFIX, syncGoogleQuota } from '../google/google.service.js'
import { buildS3ObjectKey, deleteS3Key, getS3ConfigForAccount, syncS3Quota, uploadS3Object } from '../s3/s3.service.js'
import { PHOTOS_SCOPE, photosFolderName, registerPhotos } from './free-up-photos.js'

/**
 * "Free up space": empties a full Google Drive by moving its files to the user's other drives.
 *
 * Google → Google moves happen inside Google: the source shares the file with the destination
 * account, the destination makes its own copy (files.copy, no bytes pass through this server),
 * the file row is re-pointed at the copy, and the original is deleted for good (not trashed, so
 * the space comes back at once). Google → S3 streams through the server. The file keeps its id,
 * folder, shares and previews in Nudge, so nothing changes for the user except where it lives.
 */

type Category = 'photos' | 'videos' | 'documents' | 'other'
const CATEGORIES: Category[] = ['photos', 'videos', 'documents', 'other']
const GOOGLE_APPS = 'application/vnd.google-apps.'
/** Kept free on every destination so it never ends up full itself. */
const SAFETY_MARGIN = 200n * 1024n * 1024n

export function categoryOf(mimeType: string): Category {
  if (mimeType.startsWith('image/')) return 'photos'
  if (mimeType.startsWith('video/')) return 'videos'
  if (mimeType.startsWith(GOOGLE_APPS) || mimeType.startsWith('text/') || mimeType === 'application/pdf' || mimeType === 'application/rtf' ||
    /msword|officedocument|ms-excel|ms-powerpoint|opendocument|epub/.test(mimeType)) return 'documents'
  return 'other'
}

export type Destination = { account: ConnectedAccount; available: bigint | null; folderId?: string; drive?: drive_v3.Drive }
type JobDest = { id: string; email: string; provider: string; files: number; bytes: bigint }
type Failure = { name: string; reason: string }
export type Job = {
  id: string
  /** move = Drive files moved off this Drive; photos = Google Photos picks copied to other drives. */
  kind: 'move' | 'photos'
  userId: string
  sourceId: string
  state: 'running' | 'done' | 'failed' | 'cancelled'
  totalFiles: number
  totalBytes: bigint
  doneFiles: number
  movedBytes: bigint
  current: string[]
  destinations: Map<string, JobDest>
  failures: Failure[]
  outOfSpace: boolean
  /** Photos already copied by an earlier run. */
  skipped: number
  folderName: string | null
  /** When the copied photos were taken, so the user knows what to select in Google Photos. */
  takenFrom?: Date
  takenTo?: Date
  usedBefore: bigint | null
  usedNow: bigint | null
  cancel: boolean
  error?: string
  startedAt: string
  finishedAt?: string
}
export const jobs = new Map<string, Job>() // by source account id
/** Accounts whose /start is between its "already running?" check and jobs.set (it awaits quota calls in between). */
const starting = new Set<string>()

/** Runs start() unless a job for this account is running or starting; returns null when one is. */
export async function startExclusive<T>(accountId: string, start: () => Promise<T>): Promise<T | null> {
  if (jobs.get(accountId)?.state === 'running' || starting.has(accountId)) return null
  starting.add(accountId)
  try {
    return await start()
  } finally {
    starting.delete(accountId)
  }
}

/** Stops the running job of an account (used when the account is disconnected). */
export function cancelJob(accountId: string) {
  const job = jobs.get(accountId)
  if (job?.state === 'running') job.cancel = true
}

/** Deletes a copy that was made but is not needed (the original changed while it was being copied). */
async function removeCopy(dest: Destination, newId: string) {
  if (dest.account.provider === 's3') await deleteS3Key(dest.account.id, newId)
  else await (dest.drive ?? (await driveFor(dest.account))).files.delete({ fileId: newId })
}

const str = (v: bigint | null | undefined) => (v === null || v === undefined ? null : v.toString())

export function jobJson(job: Job | undefined | null) {
  if (!job) return null
  return {
    id: job.id, kind: job.kind, skipped: job.skipped, folderName: job.folderName, takenFrom: job.takenFrom?.toISOString() ?? null, takenTo: job.takenTo?.toISOString() ?? null, state: job.state, totalFiles: job.totalFiles, totalBytes: job.totalBytes.toString(), doneFiles: job.doneFiles,
    movedBytes: job.movedBytes.toString(), current: job.current, outOfSpace: job.outOfSpace, error: job.error ?? null,
    freedBytes: job.usedBefore !== null && job.usedNow !== null && job.usedBefore > job.usedNow ? (job.usedBefore - job.usedNow).toString() : null,
    destinations: [...job.destinations.values()].map((d) => ({ ...d, bytes: d.bytes.toString() })),
    failures: job.failures.slice(0, 50), failedCount: job.failures.length, startedAt: job.startedAt, finishedAt: job.finishedAt ?? null,
  }
}

export async function sourceAccount(req: AuthRequest) {
  return prisma.connectedAccount.findFirstOrThrow({ where: { id: String(req.params.accountId), userId: req.user!.id, provider: 'google_drive', status: 'connected' }, include: { storageAccount: true } })
}

export function driveFor(account: ConnectedAccount) {
  return getAuthedGoogleClient(account).then((auth) => google.drive({ version: 'v3', auth }))
}

/** Up-to-date free space of every other drive the user could move files to. */
export async function destinations(userId: string, sourceId: string): Promise<Destination[]> {
  const accounts = await prisma.connectedAccount.findMany({ where: { userId, status: 'connected', provider: { in: ['google_drive', 's3'] }, id: { not: sourceId } } })
  await Promise.allSettled(accounts.map((a) => (a.provider === 's3' ? syncS3Quota(a.id) : syncGoogleQuota(a.id))))
  const quotas = await prisma.storageAccount.findMany({ where: { connectedAccountId: { in: accounts.map((a) => a.id) } } })
  const byId = new Map(quotas.map((q) => [q.connectedAccountId, q]))
  return accounts.map((account) => {
    const q = byId.get(account.id)
    const available = q?.availableBytes ?? null
    // A Google account always reports a limit; missing numbers mean the sync failed, so treat it as full.
    return { account, available: available === null && account.provider === 'google_drive' ? 0n : available }
  })
}

export function fits(d: Destination, size: bigint) {
  return d.available === null || d.available - size >= SAFETY_MARGIN
}

/** Auto: the Google drive with the most room (copies stay inside Google); S3 when no Google drive fits. */
export function pickDestination(all: Destination[], file: Pick<File, 'mimeType' | 'sizeBytes'>, chosenId: string | null): Destination | null {
  const googleOnly = file.mimeType.startsWith(GOOGLE_APPS)
  const usable = all.filter((d) => fits(d, file.sizeBytes) && (!googleOnly || d.account.provider === 'google_drive'))
  if (chosenId) return usable.find((d) => d.account.id === chosenId) ?? null
  const googles = usable.filter((d) => d.account.provider === 'google_drive').sort((a, b) => Number((b.available ?? 0n) - (a.available ?? 0n)))
  if (googles[0]) return googles[0]
  return usable.sort((a, b) => (a.available === null ? -1 : b.available === null ? 1 : Number(b.available - a.available)))[0] ?? null
}

async function downloadStream(drive: drive_v3.Drive, fileId: string) {
  const res = await drive.files.get({ fileId, alt: 'media' }, { responseType: 'stream' })
  return res.data as unknown as Readable
}

/** Copies one file to the destination; returns the new provider file id. */
async function copyFile(src: drive_v3.Drive, file: File, dest: Destination, userId: string): Promise<string> {
  if (dest.account.provider === 's3') {
    const config = await getS3ConfigForAccount(dest.account.id, userId)
    const key = buildS3ObjectKey(config, userId, file.id, file.name)
    await uploadS3Object(config, key, await downloadStream(src, file.providerFileId), file.mimeType)
    return key
  }
  dest.drive ??= await driveFor(dest.account)
  dest.folderId ??= await ensureGoogleAppFolder(dest.account)
  const drive = dest.drive
  const original = await src.files.get({ fileId: file.providerFileId, fields: 'size,permissions(type,role)' }).catch(() => null)
  let newId: string | null = null
  try {
    // Server-side copy: share with the destination account, which then copies it into its own Drive.
    await src.permissions.create({ fileId: file.providerFileId, sendNotificationEmail: false, requestBody: { type: 'user', role: 'reader', emailAddress: dest.account.email } })
    const copy = await drive.files.copy({ fileId: file.providerFileId, fields: 'id,size', requestBody: { name: file.name, parents: [dest.folderId] } })
    newId = copy.data.id ?? null
    const want = original?.data.size
    if (newId && want && copy.data.size && copy.data.size !== want) {
      await drive.files.delete({ fileId: newId }).catch(() => undefined)
      newId = null
    }
  } catch (error) {
    if (file.mimeType.startsWith(GOOGLE_APPS)) throw error
    console.warn('[free-up] server-side copy failed, streaming instead:', file.id, error instanceof Error ? error.message : error)
  }
  if (!newId) {
    const created = await drive.files.create({
      requestBody: { name: file.name, parents: [dest.folderId] },
      media: { mimeType: file.mimeType, body: await downloadStream(src, file.providerFileId) },
      fields: 'id',
    })
    newId = created.data.id ?? null
  }
  if (!newId) throw new Error('Google did not return the new file.')
  // Keep the same link sharing the original had (the app's "Drive link" and editors rely on it).
  for (const p of original?.data.permissions ?? []) {
    if (p.type === 'anyone' && p.role) await drive.permissions.create({ fileId: newId, requestBody: { type: 'anyone', role: p.role } }).catch(() => undefined)
  }
  return newId
}

async function runJob(job: Job, files: File[], all: Destination[], chosenId: string | null) {
  const source = await prisma.connectedAccount.findUniqueOrThrow({ where: { id: job.sourceId } })
  const src = await driveFor(source)
  const queue = [...files]
  let lastQuota = Date.now()
  let stopForSpace = false

  const worker = async () => {
    for (;;) {
      if (job.cancel || stopForSpace) return
      const file = queue.shift()
      if (!file) return
      const dest = pickDestination(all, file, chosenId)
      if (!dest) {
        // Nothing fits this file; smaller ones may still fit, so only stop when nothing fits the smallest.
        job.failures.push({ name: file.name, reason: 'None of your other drives has enough free space.' })
        job.outOfSpace = true
        const smallest = queue.reduce<bigint | null>((m, f) => (m === null || f.sizeBytes < m ? f.sizeBytes : m), null)
        if (smallest === null || !all.some((d) => fits(d, smallest))) stopForSpace = true
        continue
      }
      if (dest.available !== null) dest.available -= file.sizeBytes // reserve
      job.current.push(file.name)
      try {
        const newId = await copyFile(src, file, dest, job.userId)
        // Re-point the row only if it is still the same active file on this Drive (it may have been trashed,
        // deleted or edited while it was copying). Otherwise the copy is not needed.
        const moved = await prisma.file.updateMany({
          where: { id: file.id, connectedAccountId: job.sourceId, providerFileId: file.providerFileId, status: 'active' },
          data: { connectedAccountId: dest.account.id, provider: dest.account.provider, providerFileId: newId },
        })
        if (moved.count === 0) {
          await removeCopy(dest, newId).catch((error) => console.error('[free-up] could not remove unneeded copy', newId, error))
          if (dest.available !== null) dest.available += file.sizeBytes
          job.failures.push({ name: file.name, reason: 'Skipped: it changed while it was being moved.' })
          continue
        }
        try {
          await src.files.delete({ fileId: file.providerFileId })
        } catch (error) {
          // Keep the leftover original in the Nudge trash: the next sync won't import it as a second copy,
          // and "delete forever" there retries removing it from this Drive.
          await prisma.file.create({
            data: {
              userId: job.userId, connectedAccountId: job.sourceId, provider: 'google_drive', providerFileId: file.providerFileId, name: file.name,
              mimeType: file.mimeType, sizeBytes: file.sizeBytes, status: 'deleted', deletedAt: new Date(), folderId: file.folderId, takenAt: file.takenAt,
            },
          }).catch(() => undefined)
          job.failures.push({ name: file.name, reason: 'Moved, but the original could not be removed from this Drive. It is in your Nudge trash: delete it there to free its space.' })
        }
        job.doneFiles += 1
        job.movedBytes += file.sizeBytes
        const d = job.destinations.get(dest.account.id) ?? { id: dest.account.id, email: dest.account.email, provider: dest.account.provider, files: 0, bytes: 0n }
        d.files += 1
        d.bytes += file.sizeBytes
        job.destinations.set(dest.account.id, d)
      } catch (error) {
        if (dest.available !== null) dest.available += file.sizeBytes
        const message = error instanceof Error ? error.message : String(error)
        console.error('[free-up] move failed', file.id, message)
        job.failures.push({ name: file.name, reason: /storageQuotaExceeded|quota/i.test(message) ? 'The destination drive is full.' : 'Could not be moved. It is still safe on this Drive.' })
      } finally {
        job.current = job.current.filter((n) => n !== file.name)
      }
      if (Date.now() - lastQuota > 15_000) {
        lastQuota = Date.now()
        syncGoogleQuota(job.sourceId).then((q) => { job.usedNow = q.usedBytes }).catch(() => undefined)
      }
    }
  }

  await Promise.all([worker(), worker(), worker()])
  const q = await syncGoogleQuota(job.sourceId).catch(() => null)
  if (q) job.usedNow = q.usedBytes
  for (const d of job.destinations.values()) {
    await (d.provider === 's3' ? syncS3Quota(d.id) : syncGoogleQuota(d.id)).catch(() => undefined)
  }
  await createAuditLog(job.userId, 'FREE_UP_SPACE', 'connected_account', job.sourceId, { files: job.doneFiles, bytes: job.movedBytes.toString(), failed: job.failures.length })
}

const startSchema = z.object({
  categories: z.array(z.enum(['photos', 'videos', 'documents', 'other'])).min(1),
  targetBytes: z.coerce.bigint().positive().nullable().optional(),
  destinationId: z.string().nullable().optional(),
})

export function fail(res: Response, status: number, code: string, message: string) {
  return res.status(status).json({ code, message })
}

export function registerFreeUp(router: Router) {
  registerPhotos(router)

  /** What fills this Drive, what can be moved, and where it could go. */
  router.get('/free-up/:accountId', async (req: AuthRequest, res, next) => {
    try {
      const account = await sourceAccount(req)
      const drive = await driveFor(account)
      const about = await drive.about.get({ fields: 'storageQuota' })
      const q = about.data.storageQuota ?? {}
      const big = (v?: string | null) => (v ? BigInt(v) : 0n)
      const usage = big(q.usage)
      const inDrive = big(q.usageInDrive)
      const driveTrash = big(q.usageInDriveTrash)
      await syncGoogleQuota(account.id).catch(() => undefined)

      const files = await prisma.file.findMany({
        where: { userId: req.user!.id, connectedAccountId: account.id, status: { in: ['active', 'deleted'] }, NOT: { name: { startsWith: PREVIEW_COPY_PREFIX } } },
        select: { id: true, name: true, mimeType: true, sizeBytes: true, status: true },
      })
      const active = files.filter((f) => f.status === 'active')
      const trashed = files.filter((f) => f.status === 'deleted')
      const categories = CATEGORIES.map((key) => {
        const list = active.filter((f) => categoryOf(f.mimeType) === key)
        return { key, count: list.length, bytes: list.reduce((s, f) => s + f.sizeBytes, 0n).toString() }
      })
      const largest = [...active].sort((a, b) => Number(b.sizeBytes - a.sizeBytes)).slice(0, 25)
        .map((f) => ({ id: f.id, name: f.name, mimeType: f.mimeType, sizeBytes: f.sizeBytes.toString(), category: categoryOf(f.mimeType) }))
      const dests = await destinations(req.user!.id, account.id)
      // Google Photos copied out earlier (they still need deleting in Google Photos to free the space).
      const photosDir = await prisma.folder.findFirst({ where: { userId: req.user!.id, name: photosFolderName(account.email), parentId: null, deletedAt: null }, select: { id: true } })
      const copied = photosDir ? await prisma.file.aggregate({ where: { userId: req.user!.id, folderId: photosDir.id, status: 'active' }, _count: { _all: true }, _sum: { sizeBytes: true }, _min: { takenAt: true }, _max: { takenAt: true } }) : null

      return res.json({
        account: { id: account.id, email: account.email, displayName: account.displayName, avatarUrl: account.avatarUrl, fullSync: account.fullSync },
        photosAccess: Array.isArray(account.scopes) && (account.scopes as unknown[]).includes(PHOTOS_SCOPE),
        quota: {
          totalBytes: str(q.limit ? BigInt(q.limit) : null), usedBytes: usage.toString(),
          driveBytes: (inDrive > driveTrash ? inDrive - driveTrash : 0n).toString(), driveTrashBytes: driveTrash.toString(),
          // Gmail and Google Photos share the same 15 GB but aren't reachable through Drive.
          otherBytes: (usage > inDrive ? usage - inDrive : 0n).toString(),
        },
        knownBytes: active.reduce((s, f) => s + f.sizeBytes, 0n).toString(),
        nudgeTrash: { count: trashed.length, bytes: trashed.reduce((s, f) => s + f.sizeBytes, 0n).toString() },
        categories,
        largest,
        destinations: dests.map((d) => ({ id: d.account.id, email: d.account.email, displayName: d.account.displayName, avatarUrl: d.account.avatarUrl, provider: d.account.provider, availableBytes: str(d.available) })),
        importing: getImportStatus(account.id),
        photosCopied: copied && copied._count._all > 0 ? {
          count: copied._count._all, bytes: (copied._sum.sizeBytes ?? 0n).toString(),
          takenFrom: copied._min.takenAt?.toISOString() ?? null, takenTo: copied._max.takenAt?.toISOString() ?? null,
        } : null,
        job: jobJson(jobs.get(account.id)),
      })
    } catch (error) {
      return next(error)
    }
  })

  /** Empties Google's Drive trash and Nudge's trash for this account, permanently. */
  router.post('/free-up/:accountId/empty-trash', async (req: AuthRequest, res, next) => {
    try {
      const account = await sourceAccount(req)
      const drive = await driveFor(account)
      const before = (await syncGoogleQuota(account.id).catch(() => null))?.usedBytes ?? null
      await drive.files.emptyTrash({})
      const trashed = await prisma.file.findMany({ where: { userId: req.user!.id, connectedAccountId: account.id, status: 'deleted' } })
      const removed: string[] = []
      for (const f of trashed) {
        try {
          await drive.files.delete({ fileId: f.providerFileId })
          removed.push(f.id)
        } catch (error) {
          // Already gone from Drive (404) is fine: the row just goes too.
          if ((error as { code?: number }).code === 404) removed.push(f.id)
        }
      }
      if (removed.length) await prisma.file.deleteMany({ where: { id: { in: removed }, userId: req.user!.id, status: 'deleted' } })
      const after = (await syncGoogleQuota(account.id).catch(() => null))?.usedBytes ?? null
      await createAuditLog(req.user!.id, 'EMPTY_DRIVE_TRASH', 'connected_account', account.id, { nudgeFiles: removed.length })
      return res.json({ status: 'ok', removedFiles: removed.length, freedBytes: before !== null && after !== null && before > after ? (before - after).toString() : '0' })
    } catch (error) {
      return next(error)
    }
  })

  router.post('/free-up/:accountId/start', async (req: AuthRequest, res, next) => {
    try {
      const account = await sourceAccount(req)
      const body = startSchema.parse(req.body)
      const started = await startExclusive(account.id, () => startMove(req, res, account, body))
      if (started === null) return res.json({ job: jobJson(jobs.get(account.id)) })
      return started
    } catch (error) {
      return next(error)
    }
  })

  async function startMove(req: AuthRequest, res: Response, account: Awaited<ReturnType<typeof sourceAccount>>, body: z.infer<typeof startSchema>) {
    {
      const wanted = new Set<Category>(body.categories)

      const all = await destinations(req.user!.id, account.id)
      if (body.destinationId && !all.some((d) => d.account.id === body.destinationId)) return fail(res, 400, 'FREE_UP_BAD_DESTINATION', 'That drive is not connected any more.')
      if (all.length === 0) return fail(res, 400, 'FREE_UP_NO_DESTINATION', 'Connect another Google Drive or an S3 bucket first. Files need somewhere to go.')

      // Biggest first: space comes back fastest and fewer files need moving.
      const candidates = (await prisma.file.findMany({
        where: { userId: req.user!.id, connectedAccountId: account.id, status: 'active', NOT: { name: { startsWith: PREVIEW_COPY_PREFIX } } },
        orderBy: { sizeBytes: 'desc' },
      })).filter((f) => wanted.has(categoryOf(f.mimeType)) && (f.sizeBytes > 0n || f.mimeType.startsWith(GOOGLE_APPS)))
      const files: File[] = []
      let total = 0n
      for (const f of candidates) {
        if (body.targetBytes && total >= body.targetBytes) break
        files.push(f)
        total += f.sizeBytes
      }
      if (files.length === 0) return fail(res, 400, 'FREE_UP_NOTHING', 'There are no files of that kind on this Drive.')

      const usedBefore = (await syncGoogleQuota(account.id).catch(() => null))?.usedBytes ?? account.storageAccount?.usedBytes ?? null
      const job: Job = {
        id: crypto.randomUUID(), kind: 'move', skipped: 0, folderName: null, userId: req.user!.id, sourceId: account.id, state: 'running', totalFiles: files.length, totalBytes: total,
        doneFiles: 0, movedBytes: 0n, current: [], destinations: new Map(), failures: [], outOfSpace: false,
        usedBefore, usedNow: usedBefore, cancel: false, startedAt: new Date().toISOString(),
      }
      jobs.set(account.id, job)
      runJob(job, files, all, body.destinationId ?? null)
        .then(() => { job.state = job.cancel ? 'cancelled' : 'done' })
        .catch((error: unknown) => { job.state = 'failed'; job.error = error instanceof Error ? error.message : String(error); console.error('[free-up] job failed', error) })
        .finally(() => { job.finishedAt = new Date().toISOString() })
      return res.status(202).json({ job: jobJson(job) })
    }
  }

  router.get('/free-up/:accountId/status', async (req: AuthRequest, res: Response, next: NextFunction) => {
    try {
      const account = await sourceAccount(req)
      return res.json({ job: jobJson(jobs.get(account.id)) })
    } catch (error) {
      return next(error)
    }
  })

  router.post('/free-up/:accountId/cancel', async (req: AuthRequest, res, next) => {
    try {
      const account = await sourceAccount(req)
      cancelJob(account.id)
      return res.json({ job: jobJson(jobs.get(account.id)) })
    } catch (error) {
      return next(error)
    }
  })

  /** Clears a finished job so the next visit starts fresh. */
  router.delete('/free-up/:accountId/job', async (req: AuthRequest, res, next) => {
    try {
      const account = await sourceAccount(req)
      if (jobs.get(account.id)?.state !== 'running') jobs.delete(account.id)
      return res.json({ status: 'ok' })
    } catch (error) {
      return next(error)
    }
  })
}

