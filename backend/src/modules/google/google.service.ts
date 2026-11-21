import { google } from 'googleapis'
import { FileStatus } from '../../utils/states.js'
import type { ConnectedAccount, ProviderConfig } from '@prisma/client'
import { prisma } from '../../config/prisma.js'
import { decryptText, encryptText } from '../../utils/crypto.js'

const googleDriveFolderMimeType = 'application/vnd.google-apps.folder'
/** Root folder in each connected Google Drive that new uploads go into. Set GOOGLE_APP_FOLDER to keep an existing folder name. */
const appFolderName = process.env.GOOGLE_APP_FOLDER?.trim() || 'Nudge Drive'
/** Temporary converted copies made for in-app previews; never imported as files. */
export const PREVIEW_COPY_PREFIX = 'nudge-preview-'

export function createOAuthClient(config: ProviderConfig) {
  return new google.auth.OAuth2(decryptText(config.clientIdEncrypted), decryptText(config.clientSecretEncrypted), config.redirectUri)
}

export async function getAuthedGoogleClient(account: ConnectedAccount) {
  if (!account.accessTokenEncrypted || !account.refreshTokenEncrypted || !account.tokenExpiresAt) throw new Error('Google account tokens are missing.')
  if (!account.providerConfigId) throw new Error('Google provider config is missing.')
  const config = await prisma.providerConfig.findUniqueOrThrow({ where: { id: account.providerConfigId } })
  const client = createOAuthClient(config)
  client.setCredentials({
    access_token: decryptText(account.accessTokenEncrypted),
    refresh_token: decryptText(account.refreshTokenEncrypted),
    expiry_date: account.tokenExpiresAt.getTime(),
  })

  if (account.tokenExpiresAt.getTime() < Date.now() + 60_000) {
    const result = await client.refreshAccessToken()
    const credentials = result.credentials
    if (credentials.access_token) {
      await prisma.connectedAccount.update({
        where: { id: account.id },
        data: {
          accessTokenEncrypted: encryptText(credentials.access_token),
          tokenExpiresAt: new Date(credentials.expiry_date ?? Date.now() + 3600_000),
        },
      })
      client.setCredentials(credentials)
    }
  }

  return client
}

export async function syncGoogleQuota(accountId: string) {
  const account = await prisma.connectedAccount.findUniqueOrThrow({ where: { id: accountId } })
  const auth = await getAuthedGoogleClient(account)
  const drive = google.drive({ version: 'v3', auth })
  const about = await drive.about.get({ fields: 'storageQuota,user' })
  const quota = about.data.storageQuota
  const total = quota?.limit ? BigInt(quota.limit) : null
  const used = quota?.usage ? BigInt(quota.usage) : 0n
  return prisma.storageAccount.upsert({
    where: { connectedAccountId: accountId },
    create: {
      connectedAccountId: accountId,
      totalBytes: total,
      usedBytes: used,
      availableBytes: total === null ? null : total - used,
      trashBytes: quota?.usageInDriveTrash ? BigInt(quota.usageInDriveTrash) : null,
      lastSyncedAt: new Date(),
    },
    update: {
      totalBytes: total,
      usedBytes: used,
      availableBytes: total === null ? null : total - used,
      trashBytes: quota?.usageInDriveTrash ? BigInt(quota.usageInDriveTrash) : null,
      lastSyncedAt: new Date(),
    },
  })
}

function escapeDriveQueryValue(value: string) {
  return value.replace(/\\/g, '\\\\').replace(/'/g, "\\'")
}

export async function ensureGoogleAppFolder(account: ConnectedAccount) {
  const auth = await getAuthedGoogleClient(account)
  const drive = google.drive({ version: 'v3', auth })
  const queryName = escapeDriveQueryValue(appFolderName)
  const existing = await drive.files.list({
    q: `name = '${queryName}' and mimeType = '${googleDriveFolderMimeType}' and 'root' in parents and trashed = false`,
    spaces: 'drive',
    fields: 'files(id,name)',
    pageSize: 1,
  })
  const folderId = existing.data.files?.[0]?.id ?? (await drive.files.create({
    requestBody: { name: appFolderName, mimeType: googleDriveFolderMimeType, parents: ['root'] },
    fields: 'id',
  })).data.id

  if (!folderId) throw new Error('Failed to create Google Drive app folder.')
  return folderId
}

export type GoogleAppFolderSyncResult = {
  accountId: string
  created: number
  updated: number
  deleted: number
}

type DriveFileMetadata = {
  id: string
  name: string
  mimeType: string
  sizeBytes: bigint
  parentId: string
  createdAt: Date | null
  takenAt: Date | null
}

/** EXIF "2021:05:01 10:20:30" (camera local time, no zone) -> Date. */
function parseExifTime(v: string | null | undefined): Date | null {
  const m = v?.match(/^(\d{4})[:-](\d{2})[:-](\d{2})[ T](\d{2}):(\d{2}):(\d{2})/)
  if (!m) return null
  const d = new Date(Date.UTC(+m[1]!, +m[2]! - 1, +m[3]!, +m[4]!, +m[5]!, +m[6]!))
  return isNaN(d.getTime()) || d.getUTCFullYear() < 1990 || d.getTime() > Date.now() + 86_400_000 ? null : d
}

// One sync per account at a time: two overlapping runs would both see a file as new and create it twice.
// A caller arriving while one runs queues one more run (that run sees everything it wants); later callers share it.
const syncQueue = new Map<string, { tail: Promise<unknown>; queued: Promise<GoogleAppFolderSyncResult> | null }>()

export function syncGoogleAppFolderFiles(accountId: string, userId: string): Promise<GoogleAppFolderSyncResult> {
  const slot = syncQueue.get(accountId)
  if (slot?.queued) return slot.queued
  const run: Promise<GoogleAppFolderSyncResult> = (slot?.tail ?? Promise.resolve())
    .catch(() => undefined)
    .then(() => {
      const s = syncQueue.get(accountId)
      if (s?.queued === run) s.queued = null
      return runGoogleSync(accountId, userId)
    })
  syncQueue.set(accountId, { tail: run, queued: slot ? run : null })
  run.catch(() => undefined).finally(() => { if (syncQueue.get(accountId)?.tail === run) syncQueue.delete(accountId) })
  return run
}

async function runGoogleSync(accountId: string, userId: string): Promise<GoogleAppFolderSyncResult> {
  const account = await prisma.connectedAccount.findFirstOrThrow({ where: { id: accountId, userId, provider: 'google_drive', status: 'connected' } })
  const auth = await getAuthedGoogleClient(account)
  const drive = google.drive({ version: 'v3', auth })
  const appFolderId = await ensureGoogleAppFolder(account)
  // Full-sync accounts mirror the whole Drive: refresh the folder tree first.
  const rootRowId = account.fullSync ? (await importDriveFolders(account, drive, appFolderId)).rootRowId : null

  const userFolders = await prisma.folder.findMany({
    where: { userId, connectedAccountId: account.id, deletedAt: null },
    select: { id: true, providerFolderId: true }
  })
  const parentIds = [
    appFolderId,
    ...userFolders.map((f) => f.providerFolderId).filter((id): id is string => !!id)
  ]

  const driveFiles: DriveFileMetadata[] = []
  let pageToken: string | undefined

  const parentsQuery = parentIds.map((id) => `'${id}' in parents`).join(' or ')
  const q = account.fullSync
    ? `mimeType != '${googleDriveFolderMimeType}' and trashed = false and 'me' in owners and not name contains '${PREVIEW_COPY_PREFIX}'`
    : `(${parentsQuery}) and mimeType != '${googleDriveFolderMimeType}' and trashed = false and not name contains '${PREVIEW_COPY_PREFIX}'`

  do {
    const response = await drive.files.list({
      q,
      spaces: 'drive',
      fields: 'nextPageToken,files(id,name,mimeType,size,parents,createdTime,imageMediaMetadata/time)',
      pageSize: 1000,
      pageToken,
    })
    for (const file of response.data.files ?? []) {
      if (!file.id || !file.name || !file.mimeType) continue
      const parentId = file.parents?.[0] ?? appFolderId
      const createdAt = file.createdTime ? new Date(file.createdTime) : null
      driveFiles.push({
        id: file.id, name: file.name, mimeType: file.mimeType, sizeBytes: BigInt(file.size ?? 0), parentId,
        createdAt: createdAt && !isNaN(createdAt.getTime()) ? createdAt : null,
        takenAt: parseExifTime(file.imageMediaMetadata?.time),
      })
    }
    pageToken = response.data.nextPageToken ?? undefined
  } while (pageToken)

  const existingFiles = await prisma.file.findMany({ where: { userId, connectedAccountId: account.id, provider: 'google_drive' } })
  const existingByProviderId = new Map(existingFiles.map((file) => [file.providerFileId, file]))
  const driveFileIds = new Set(driveFiles.map((file) => file.id))
  let created = 0
  let updated = 0
  let deleted = 0

  const folderIdMap = new Map(userFolders.map((f) => [f.providerFolderId, f.id]))

  for (const driveFile of driveFiles) {
    const existing = existingByProviderId.get(driveFile.id)
    // Files in the app folder live wherever the user put them in Nudge (folders are Nudge-side), so keep that.
    const dbFolderId = driveFile.parentId === appFolderId ? (existing?.folderId ?? null) : (folderIdMap.get(driveFile.parentId) ?? rootRowId)
    if (!existing) {
      await prisma.file.create({
        data: {
          userId, connectedAccountId: account.id, provider: 'google_drive', providerFileId: driveFile.id, name: driveFile.name, mimeType: driveFile.mimeType,
          sizeBytes: driveFile.sizeBytes, status: 'active', folderId: dbFolderId,
          // The date the file really arrived on Drive (not the import date), and when a photo was taken.
          ...(driveFile.createdAt ? { createdAt: driveFile.createdAt } : {}), takenAt: driveFile.takenAt,
        },
      })
      created += 1
      continue
    }

    const dateWrong = !!driveFile.createdAt && Math.abs(existing.createdAt.getTime() - driveFile.createdAt.getTime()) > 120_000
    const takenMissing = !existing.takenAt && !!driveFile.takenAt
    // Only a file that had vanished from Drive comes back. One the user put in the Nudge trash stays there.
    const revive = existing.status === FileStatus.Missing
    const needsUpdate = existing.name !== driveFile.name || existing.mimeType !== driveFile.mimeType || existing.sizeBytes !== driveFile.sizeBytes || revive || existing.folderId !== dbFolderId || dateWrong || takenMissing
    if (needsUpdate) {
      // Guarded by the status we read, so a trash or delete made while this sync ran is not undone.
      const result = await prisma.file.updateMany({
        where: { id: existing.id, status: existing.status },
        data: {
          name: driveFile.name, mimeType: driveFile.mimeType, sizeBytes: driveFile.sizeBytes, folderId: dbFolderId,
          ...(revive ? { status: FileStatus.Active, deletedAt: null } : {}),
          ...(dateWrong ? { createdAt: driveFile.createdAt! } : {}), ...(takenMissing ? { takenAt: driveFile.takenAt } : {}),
        },
      })
      updated += result.count
    }
  }

  // Deleted outside Nudge: hide them (not the Nudge trash, where "restore" would give back a file that no longer exists).
  const missingActiveIds = existingFiles.filter((file) => file.status === FileStatus.Active && !driveFileIds.has(file.providerFileId)).map((file) => file.id)
  if (missingActiveIds.length > 0) {
    const result = await prisma.file.updateMany({ where: { id: { in: missingActiveIds }, userId, status: FileStatus.Active }, data: { status: FileStatus.Missing, deletedAt: new Date() } })
    deleted = result.count
  }

  await syncGoogleQuota(account.id).catch(() => undefined)
  return { accountId: account.id, created, updated, deleted }
}

// ---- Full Google Drive import (Nudge Drive) ----
export type DriveImportStatus = { state: 'running' | 'done' | 'failed'; folders: number; created: number; updated: number; error?: string; startedAt: string }
const driveImports = new Map<string, DriveImportStatus>()

export function getImportStatus(accountId: string) { return driveImports.get(accountId) ?? null }

/** Starts (or returns the running) import of every file the user owns on that Google Drive. */
export function startGoogleDriveImport(accountId: string, userId: string): DriveImportStatus {
  const current = driveImports.get(accountId)
  if (current?.state === 'running') return current
  const status: DriveImportStatus = { state: 'running', folders: 0, created: 0, updated: 0, startedAt: new Date().toISOString() }
  driveImports.set(accountId, status)
  let wasFull = true
  ;(async () => {
    const before = await prisma.connectedAccount.findUniqueOrThrow({ where: { id: accountId }, select: { fullSync: true } })
    wasFull = before.fullSync
    if (!wasFull) await prisma.connectedAccount.update({ where: { id: accountId }, data: { fullSync: true } })
    const result = await syncGoogleAppFolderFiles(accountId, userId)
    status.created = result.created
    status.updated = result.updated
    status.state = 'done'
  })().catch(async (error: unknown) => {
    // A first import that failed: clear the flag so the app offers the import again (and doesn't report "done").
    if (!wasFull) await prisma.connectedAccount.update({ where: { id: accountId }, data: { fullSync: false } }).catch(() => undefined)
    status.state = 'failed'
    status.error = error instanceof Error ? error.message : String(error)
    console.error('Drive import failed', error)
  })
  return status
}

/** Mirrors every Drive folder the user owns as a folder row; returns the row standing for "My Drive". */
async function importDriveFolders(account: ConnectedAccount, drive: ReturnType<typeof google.drive>, appFolderId: string) {
  const rootId = (await drive.files.get({ fileId: 'root', fields: 'id' })).data.id
  if (!rootId) throw new Error('Could not read the Google Drive root folder.')
  const userId = account.userId
  const status = driveImports.get(account.id)

  let rootRow = await prisma.folder.findFirst({ where: { userId, connectedAccountId: account.id, providerFolderId: rootId, deletedAt: null } })
  if (!rootRow) {
    rootRow = await prisma.folder.create({
      data: {
        userId, connectedAccountId: account.id, provider: 'google_drive', providerFolderId: rootId,
        name: `Google Drive · ${account.email.split('@')[0]}`, color: '#8b72f5', iconUrl: 'https://api.iconify.design/lucide:hard-drive.svg',
      },
    })
  }

  const driveFolders: { id: string; name: string; parent: string | null; createdAt: Date | null }[] = []
  let pageToken: string | undefined
  do {
    const response = await drive.files.list({
      q: `mimeType = '${googleDriveFolderMimeType}' and trashed = false and 'me' in owners`,
      spaces: 'drive', fields: 'nextPageToken,files(id,name,parents,createdTime)', pageSize: 1000, pageToken,
    })
    for (const f of response.data.files ?? []) {
      if (f.id && f.name && f.id !== appFolderId) driveFolders.push({ id: f.id, name: f.name, parent: f.parents?.[0] ?? null, createdAt: f.createdTime ? new Date(f.createdTime) : null })
    }
    pageToken = response.data.nextPageToken ?? undefined
  } while (pageToken)

  const existing = await prisma.folder.findMany({ where: { userId, connectedAccountId: account.id, deletedAt: null } })
  const byDriveId = new Map(existing.filter((f) => f.providerFolderId).map((f) => [f.providerFolderId!, f]))
  for (const df of driveFolders) {
    const row = byDriveId.get(df.id)
    if (!row) {
      const created = await prisma.folder.create({ data: { userId, connectedAccountId: account.id, provider: 'google_drive', providerFolderId: df.id, name: df.name, color: '#3b82f6', ...(df.createdAt ? { createdAt: df.createdAt } : {}) } })
      byDriveId.set(df.id, created)
      if (status) status.folders += 1
    } else if (row.name !== df.name || (df.createdAt && Math.abs(row.createdAt.getTime() - df.createdAt.getTime()) > 120_000)) {
      await prisma.folder.update({ where: { id: row.id }, data: { name: df.name, ...(df.createdAt ? { createdAt: df.createdAt } : {}) } })
    }
  }
  // Link parents: app folder -> top level, My Drive -> the "Google Drive · name" folder, unknown -> that folder too.
  for (const df of driveFolders) {
    const row = byDriveId.get(df.id)!
    const wanted = df.parent === appFolderId ? null : df.parent === rootId || !df.parent ? rootRow.id : (byDriveId.get(df.parent)?.id ?? rootRow.id)
    if (row.parentId !== wanted && row.id !== wanted) await prisma.folder.update({ where: { id: row.id }, data: { parentId: wanted } })
  }
  return { rootRowId: rootRow.id }
}
