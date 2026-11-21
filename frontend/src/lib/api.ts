import { clearSession, getAccessToken, getRefreshToken, setAccessToken } from '@/lib/session'
import type {
  ApiKey, AuditLog, AuthResponse, ConnectedAccount, FileFilters, FileItem, Folder, FreeUpJob, FreeUpPlan, ImportStatus, Invite, OfficeSession,
  PhotosSession, RoutingMode, RoutingPolicy, SharedLink, SheetData, StorageBreakdown, StorageSummary, User,
} from '@/lib/types'

/** The backend lives under /api on the same origin (Caddy routes it). */
export const API_URL = '/api'

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

type Options = Omit<RequestInit, 'body'> & { body?: unknown; auth?: boolean; retry?: boolean }

let refreshing: Promise<boolean> | null = null

/** One refresh at a time, shared by every request that hit a 401 together. */
function refreshAccessToken() {
  refreshing ??= (async () => {
    const refreshToken = getRefreshToken()
    if (!refreshToken) return false
    try {
      const res = await fetch(`${API_URL}/auth/refresh`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken }) })
      if (!res.ok) return false
      const data = (await res.json()) as { accessToken: string }
      setAccessToken(data.accessToken)
      return true
    } catch {
      return false
    }
  })().finally(() => {
    refreshing = null
  })
  return refreshing
}

/** fetch() with the bearer token, one silent refresh on 401, and typed errors. Returns the raw Response. */
export async function authedFetch(path: string, options: Options = {}): Promise<Response> {
  const { body, auth = true, retry = true, ...init } = options
  const headers = new Headers(init.headers)
  const token = getAccessToken()
  if (auth && token) headers.set('Authorization', `Bearer ${token}`)
  let payload: BodyInit | undefined
  if (body instanceof FormData || body instanceof Blob) payload = body
  else if (body !== undefined) {
    headers.set('Content-Type', 'application/json')
    payload = JSON.stringify(body)
  }
  let res: Response
  try {
    res = await fetch(`${API_URL}${path}`, { ...init, headers, body: payload })
  } catch {
    throw new ApiError(0, 'NETWORK', "Can't reach the server. Check your connection and try again.")
  }
  if (res.status === 401 && auth && retry && token && (await refreshAccessToken())) {
    return authedFetch(path, { ...options, retry: false })
  }
  if (!res.ok) {
    const data = (await res.json().catch(() => null)) as { code?: string; message?: string } | null
    const code = data?.code ?? `HTTP_${res.status}`
    if (res.status === 401 && auth && token) clearSession()
    if (code === 'EMAIL_NOT_VERIFIED') window.dispatchEvent(new Event('nd:verify-email'))
    throw new ApiError(res.status, code, friendlyMessage(code, data?.message, res.status))
  }
  return res
}

/**
 * POST multipart form data with upload progress (fetch can't report it): the same bearer token, one silent
 * refresh on 401 and typed errors as authedFetch. Aborting `signal` aborts the request (AbortError).
 */
export function uploadForm<T>(path: string, form: FormData, onProgress: (sent: number, total: number) => void, signal: AbortSignal, retry = true): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    if (signal.aborted) return reject(new DOMException('Cancelled', 'AbortError'))
    const xhr = new XMLHttpRequest()
    const token = getAccessToken()
    const onAbort = () => xhr.abort()
    const settle = () => signal.removeEventListener('abort', onAbort)
    xhr.open('POST', `${API_URL}${path}`)
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgress(e.loaded, e.total)
    }
    xhr.onabort = () => {
      settle()
      reject(new DOMException('Cancelled', 'AbortError'))
    }
    xhr.onerror = () => {
      settle()
      reject(new ApiError(0, 'NETWORK', "Can't reach the server. Check your connection and try again."))
    }
    xhr.onload = () => {
      settle()
      let data: unknown = null
      try {
        data = JSON.parse(xhr.responseText)
      } catch {
        /* not JSON */
      }
      const fail = () => {
        const body = data as { code?: string; message?: string } | null
        const code = body?.code ?? `HTTP_${xhr.status}`
        if (xhr.status === 401 && token) clearSession()
        if (code === 'EMAIL_NOT_VERIFIED') window.dispatchEvent(new Event('nd:verify-email'))
        reject(new ApiError(xhr.status, code, friendlyMessage(code, body?.message, xhr.status)))
      }
      if (xhr.status === 401 && retry && token) {
        void refreshAccessToken().then((ok) => (ok ? resolve(uploadForm<T>(path, form, onProgress, signal, false)) : fail()))
        return
      }
      if (xhr.status >= 200 && xhr.status < 300) resolve(data as T)
      else fail()
    }
    signal.addEventListener('abort', onAbort)
    xhr.send(form)
  })
}

function friendlyMessage(code: string, message: string | undefined, status: number) {
  if (code === 'VALIDATION_ERROR' || code === 'ZodError') return message && !message.startsWith('[') ? message : 'Please check the details and try again.'
  if (message && !message.startsWith('[') && message.length < 300) return message
  if (status === 404) return 'That item no longer exists.'
  if (status >= 500) return 'The server ran into a problem. Please try again.'
  return 'Something went wrong. Please try again.'
}

export async function request<T>(path: string, options: Options = {}): Promise<T> {
  const res = await authedFetch(path, options)
  return (await res.json()) as T
}

const get = <T>(path: string) => request<T>(path)
const post = <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body: body ?? {} })
const patch = <T>(path: string, body: unknown) => request<T>(path, { method: 'PATCH', body })
const del = <T>(path: string, body?: unknown) => request<T>(path, { method: 'DELETE', body })
const enc = encodeURIComponent

function query(params: Record<string, string | undefined | null>) {
  const q = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) if (v) q.set(k, v)
  const s = q.toString()
  return s ? `?${s}` : ''
}

export const api = {
  auth: {
    login: (email: string, password: string) => request<AuthResponse>('/auth/login', { method: 'POST', auth: false, body: { email, password } }),
    register: (name: string, email: string, password: string, captchaToken?: string) =>
      request<AuthResponse>('/auth/register', { method: 'POST', auth: false, body: { name, email, password, captchaToken } }),
    googleUrl: () => request<{ url: string }>('/auth/google/url', { auth: false }),
    googleExchange: (token: string) => request<AuthResponse>('/auth/google/exchange', { method: 'POST', auth: false, body: { token } }),
    me: () => get<{ user: User }>('/auth/me'),
    logout: () => post<{ status: string }>('/auth/logout'),
    verifyEmail: (code: string) => post<{ status: string; emailVerified: boolean }>('/auth/verify-email', { code }),
    resendVerification: () => post<{ status: string; emailVerified?: boolean }>('/auth/resend-verification'),
    sendDeleteCode: () => post<{ status: string; email: string }>('/auth/delete-account/code'),
    deleteAccount: (confirm: { password: string } | { code: string }) => post<{ status: 'deleted' }>('/auth/delete-account', confirm),
  },
  files: {
    list: (f: FileFilters = {}) => get<{ files: FileItem[] }>(`/files${query(f)}`),
    get: (id: string) => get<{ file: FileItem }>(`/files/${enc(id)}`),
    update: (id: string, body: { name?: string; folderId?: string | null }) => patch<{ file: FileItem }>(`/files/${enc(id)}`, body),
    trash: (ids: string[]) => del<{ deleted: number }>('/files/batch', { fileIds: ids }),
    move: (ids: string[], folderId: string | null) => patch<{ moved: number }>('/files/batch', { fileIds: ids, folderId }),
    trashed: () => get<{ files: FileItem[] }>('/files/trash'),
    restore: (ids: string[]) => post<{ restored: number }>('/files/batch/restore', { fileIds: ids }),
    deleteForever: (ids: string[]) => del<{ deleted: number; failed: { fileId: string; message: string }[] }>('/files/batch/permanent', { fileIds: ids }),
    share: (id: string) => post<{ url: string; shareId: string }>(`/files/${enc(id)}/share`),
    unshare: (id: string) => del<{ status: string }>(`/files/${enc(id)}/share`),
    sharedLinks: () => get<{ shares: SharedLink[] }>('/files/shared-links'),
    makeDrivePublic: (id: string) => post<{ url: string }>(`/files/${enc(id)}/public-permission`),
    previewToken: (id: string) => post<{ path: string; url: string }>(`/files/${enc(id)}/preview-token`),
    syncGoogle: () => post<{ results: { created: number; updated: number; deleted: number }[] }>('/files/sync-google'),
    sheetData: (id: string) => get<SheetData>(`/files/${enc(id)}/sheet-data`),
    editLink: (id: string) => get<{ url: string; account: string }>(`/files/${enc(id)}/edit-link`),
    officeSession: (id: string) => post<OfficeSession>(`/files/${enc(id)}/office-session`),
    createDocument: (kind: 'document' | 'spreadsheet' | 'presentation', name: string, folderId: string | null) =>
      post<{ file: FileItem; url: string | null }>('/files/create-document', { kind, name, folderId }),
  },
  folders: {
    list: (parentId?: string | null) => get<{ folders: Folder[] }>(`/folders${query({ parentId: parentId ?? undefined })}`),
    all: () => get<{ folders: Folder[] }>('/folders?all=1'),
    create: (body: { name: string; color?: string; iconUrl?: string | null; parentId?: string | null }) => post<{ folder: Folder }>('/folders', body),
    update: (id: string, body: { name?: string; color?: string; iconUrl?: string | null; parentId?: string | null }) => patch<{ folder: Folder }>(`/folders/${enc(id)}`, body),
    remove: (id: string) => del<{ status: string }>(`/folders/${enc(id)}`),
  },
  storage: {
    summary: () => get<StorageSummary>('/storage/summary'),
    breakdown: () => get<StorageBreakdown>('/storage/breakdown'),
    routing: () => get<{ policy: RoutingPolicy }>('/storage/routing-policy'),
    setRouting: (mode: RoutingMode, priorityAccountIds: string[]) => patch<{ policy: RoutingPolicy }>('/storage/routing-policy', { mode, priorityAccountIds }),
  },
  accounts: {
    list: () => get<{ accounts: ConnectedAccount[] }>('/connected-accounts'),
    googleConnectUrl: () => get<{ url: string }>('/connected-accounts/google/connect-url'),
    /** Consent that adds "read the Google Photos I pick" to that Google account (Free up space). */
    googlePhotosConnectUrl: (email: string) => get<{ url: string }>(`/connected-accounts/google/connect-url${query({ photos: '1', hint: email })}`),
    connectS3: (body: { name: string; bucket: string; region: string; endpoint?: string; accessKeyId: string; secretAccessKey: string; forcePathStyle: boolean; quotaBytes: string | null }) =>
      post<{ account: ConnectedAccount }>('/connected-accounts/s3', body),
    syncQuota: (id: string) => post<unknown>(`/connected-accounts/${enc(id)}/sync-quota`),
    importDrive: (id: string) => post<ImportStatus>(`/connected-accounts/${enc(id)}/import-drive`),
    importStatus: (id: string) => get<ImportStatus>(`/connected-accounts/${enc(id)}/import-status`),
    disconnect: (id: string) => del<{ status: string }>(`/connected-accounts/${enc(id)}`),
  },
  /** Free up space on a full Google Drive (moves its files / copies Google Photos to the other drives). */
  freeUp: {
    plan: (id: string) => get<FreeUpPlan>(`/storage/free-up/${enc(id)}`),
    emptyTrash: (id: string) => post<{ removedFiles: number; freedBytes: string }>(`/storage/free-up/${enc(id)}/empty-trash`),
    start: (id: string, categories: string[], targetBytes: number | null, destinationId: string | null) =>
      post<{ job: FreeUpJob | null }>(`/storage/free-up/${enc(id)}/start`, { categories, targetBytes: targetBytes === null ? null : String(targetBytes), destinationId }),
    status: (id: string) => get<{ job: FreeUpJob | null }>(`/storage/free-up/${enc(id)}/status`),
    cancel: (id: string) => post<{ job: FreeUpJob | null }>(`/storage/free-up/${enc(id)}/cancel`),
    clear: (id: string) => del<{ status: string }>(`/storage/free-up/${enc(id)}/job`),
    photosSession: (id: string) => post<{ session: PhotosSession }>(`/storage/free-up/${enc(id)}/photos/session`),
    photosSessionStatus: (id: string, sessionId: string) => get<{ session: PhotosSession }>(`/storage/free-up/${enc(id)}/photos/session/${enc(sessionId)}`),
    photosStart: (id: string, sessionId: string, destinationId: string | null) =>
      post<{ job: FreeUpJob | null }>(`/storage/free-up/${enc(id)}/photos/start`, { sessionId, destinationId }),
  },
  invites: {
    list: () => get<{ sent: Invite[]; received: Invite[] }>('/invites'),
    create: (email: string, role: 'viewer' | 'editor', targetType: 'file' | 'folder', targetId: string) => post<{ invite: Invite }>('/invites', { email, role, targetType, targetId }),
    revoke: (id: string) => del<{ status: string }>(`/invites/${enc(id)}`),
  },
  apiKeys: {
    list: () => get<{ apiKeys: ApiKey[] }>('/api-keys'),
    create: (name: string) => post<{ apiKey: ApiKey; secret: string }>('/api-keys', { name }),
    revoke: (id: string) => del<{ status: string }>(`/api-keys/${enc(id)}`),
  },
  activity: () => get<{ logs: AuditLog[] }>('/audit-logs'),
  /** Server-admin only (the server answers 403 ADMIN_ONLY for everyone else). */
  system: {
    googleConfig: () => get<{ exists: boolean; clientId?: string; redirectUri?: string; hasSecret?: boolean; defaultRedirectUri: string }>('/system/google-config'),
    saveGoogleConfig: (clientId: string, clientSecret: string | undefined, redirectUri: string) => post<{ message: string }>('/system/google-config', { clientId, clientSecret, redirectUri }),
    update: () => post<{ message: string }>('/system/update'),
    updateLog: () => get<{ log: string }>('/system/update-log'),
    restore: (file: File) => {
      const form = new FormData()
      form.append('file', file)
      return request<{ message: string }>('/system/restore', { method: 'POST', body: form })
    },
  },
  publicFile: (token: string) => request<{ file: { id: string; name: string; mimeType: string; sizeBytes: string; createdAt: string } }>(`/public/files/${enc(token)}`, { auth: false }),
}

/** A short-lived (10 min) URL that streams the file without auth headers: usable by <img>, <video>, iframes and downloads. */
export async function previewUrl(fileId: string) {
  const { path } = await api.files.previewToken(fileId)
  return `${API_URL}${path}`
}

export const publicPreviewUrl = (token: string) => `${API_URL}/public/files/${enc(token)}/preview`
export const publicDownloadUrl = (token: string) => `${API_URL}/public/files/${enc(token)}/download`

export function errorMessage(error: unknown, fallback = 'Something went wrong. Please try again.') {
  return error instanceof Error && error.message ? error.message : fallback
}
