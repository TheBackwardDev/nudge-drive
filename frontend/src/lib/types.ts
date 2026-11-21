/** Shapes returned by the Nudge Drive API (backend/src/modules/**). Byte counts arrive as strings (BigInt). */

export type User = {
  id: string
  name: string
  email: string
  status?: string
  emailVerified?: boolean
}

export type AuthResponse = { accessToken: string; refreshToken: string; user: User }

export type Provider = 'google_drive' | 's3'

export type FileItem = {
  id: string
  name: string
  mimeType: string
  sizeBytes: string
  provider: Provider | string
  status: string
  folderId: string | null
  connectedAccountId: string
  createdAt: string
  updatedAt: string
  deletedAt?: string | null
  takenAt?: string | null
  connectedAccount?: { id: string; email: string; provider: Provider | string }
  folder?: { id: string; name: string } | null
}

export type Folder = {
  id: string
  name: string
  color: string
  iconUrl?: string | null
  parentId: string | null
  providerFolderId: string | null
  createdAt: string
  updatedAt: string
}

export type StorageAccount = {
  totalBytes: string | null
  usedBytes: string
  availableBytes: string | null
  trashBytes?: string | null
  lastSyncedAt: string | null
  lastError?: string | null
}

export type ConnectedAccount = {
  id: string
  provider: Provider | string
  email: string
  displayName?: string | null
  avatarUrl?: string | null
  status: string
  fullSync?: boolean
  createdAt?: string
  storageAccount?: StorageAccount | null
}

export type StorageSummary = { totalBytes: string; usedBytes: string; availableBytes: string }
export type StorageBreakdown = { photo: string; video: string; document: string }

export type RoutingMode = 'most_available' | 'round_robin' | 'priority'
export type RoutingPolicy = { mode: RoutingMode; priorityAccountIds: string[]; roundRobinCursor: number }

export type ImportStatus = {
  state: 'idle' | 'running' | 'done' | 'failed'
  folders: number
  created: number
  updated: number
  error?: string
}

export type InviteTarget = { id: string; name: string; type: 'file' | 'folder'; mimeType?: string; sizeBytes?: string; folderId?: string | null }
export type Invite = {
  id: string
  email: string
  role: 'viewer' | 'editor' | string
  status: string
  targetType: 'file' | 'folder'
  targetId: string
  target: InviteTarget | null
  createdAt: string
  acceptedAt: string | null
  revokedAt?: string | null
  user: { id: string; name: string; email: string } | null
}

export type SharedLink = { id: string; url: string | null; createdAt: string; expiresAt: string | null; file: FileItem }

export type ApiKey = {
  id: string
  name: string
  keyPrefix: string
  scopes: string[]
  status: string
  lastUsedAt: string | null
  expiresAt: string | null
  revokedAt: string | null
  createdAt: string
}

export type AuditLog = {
  id: string
  action: string
  entityType: string
  entityId: string | null
  metadata: unknown
  createdAt: string
}

export type SheetData = { sheets: { name: string; rows: string[][]; colWidths: (number | null)[]; truncated: boolean }[] }

export type OfficeSession = { url: string; accessToken: string; accessTokenTtl: number; fileName: string }

export type FileFilters = {
  q?: string
  folderId?: string
  accountId?: string
  minSize?: string
  maxSize?: string
  startDate?: string
  endDate?: string
}

/* ---------- Free up space (move files off a full Google Drive, copy Google Photos out) ---------- */

export type FreeUpCategoryKey = 'photos' | 'videos' | 'documents' | 'other'

export type FreeUpQuota = { totalBytes: string | null; usedBytes: string; driveBytes: string; driveTrashBytes: string; otherBytes: string }
export type FreeUpCategory = { key: FreeUpCategoryKey | string; count: number; bytes: string }
export type FreeUpFile = { id: string; name: string; mimeType: string; sizeBytes: string; category: FreeUpCategoryKey | string }
/** `availableBytes` null = no size limit. */
export type FreeUpDestination = { id: string; email: string; displayName: string | null; avatarUrl: string | null; provider: Provider | string; availableBytes: string | null }
export type FreeUpAccount = { id: string; email: string; displayName: string | null; avatarUrl: string | null; fullSync: boolean }
/** Google Photos already copied out of this account (the originals still need deleting in Google Photos). */
export type PhotosCopied = { count: number; bytes: string; takenFrom: string | null; takenTo: string | null }

export type FreeUpJob = {
  id: string
  /** move = Drive files moved off this Drive; photos = Google Photos picks copied to the other drives. */
  kind: 'move' | 'photos'
  state: 'running' | 'done' | 'failed' | 'cancelled'
  totalFiles: number
  totalBytes: string
  doneFiles: number
  movedBytes: string
  current: string[]
  outOfSpace: boolean
  error: string | null
  freedBytes: string | null
  destinations: { id: string; email: string; provider: Provider | string; files: number; bytes: string }[]
  failures: { name: string; reason: string }[]
  failedCount: number
  /** Photos already copied by an earlier run. */
  skipped: number
  folderName: string | null
  /** When the copied photos were taken (ISO), to find them again in Google Photos. */
  takenFrom: string | null
  takenTo: string | null
}

export type FreeUpPlan = {
  account: FreeUpAccount
  /** Nudge may read the Google Photos the user picks on this account. */
  photosAccess: boolean
  photosCopied: PhotosCopied | null
  quota: FreeUpQuota
  knownBytes: string
  nudgeTrash: { count: number; bytes: string }
  categories: FreeUpCategory[]
  largest: FreeUpFile[]
  destinations: FreeUpDestination[]
  importing: ImportStatus | null
  job: FreeUpJob | null
}

export type PhotosSession = { id: string; pickerUri: string | null; mediaItemsSet: boolean; pollIntervalMs: number }
