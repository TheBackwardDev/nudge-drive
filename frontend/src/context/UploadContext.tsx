import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react'
import { ApiError, api, authedFetch, errorMessage, request, uploadForm } from '@/lib/api'
import { emitChange } from '@/lib/hooks'
import { postNotice } from '@/lib/notices'
import { plural } from '@/lib/format'

export type TransferState = 'queued' | 'running' | 'done' | 'failed' | 'cancelled'
export type Transfer = {
  id: string
  name: string
  size: number
  progress: number
  state: TransferState
  error?: string
  folderId: string | null
  accountId: string | null
}

/** A file plus the folder path it had inside a dropped / picked folder (["Trip", "Day 1"]). */
export type TreeEntry = { file: File; dirs: string[] }

type UploadApi = {
  transfers: Transfer[]
  active: number
  panelOpen: boolean
  setPanelOpen: (open: boolean) => void
  upload: (files: File[], folderId: string | null, accountId?: string | null) => void
  uploadTree: (entries: TreeEntry[], parentId: string | null, accountId?: string | null) => Promise<void>
  retry: (id: string) => void
  cancel: (id: string) => void
  clearFinished: () => void
}

const UploadContext = createContext<UploadApi | null>(null)

const CHUNK = 5 * 1024 * 1024 // multiple of 256 KB, as Google's resumable API requires
const PARALLEL = 2

type Job = { file: File; sessionId?: string; abort?: AbortController }

export function UploadProvider({ children }: { children: ReactNode }) {
  const [transfers, setTransfers] = useState<Transfer[]>([])
  const [panelOpen, setPanelOpen] = useState(false)
  const jobs = useRef(new Map<string, Job>())
  const list = useRef<Transfer[]>([])
  const running = useRef(0)
  const seq = useRef(0)
  // Results of the current burst of uploads, reported once in the inbox when it settles.
  const batch = useRef({ done: 0, failed: 0, name: '' })

  const commit = useCallback((next: Transfer[]) => {
    list.current = next
    setTransfers(next)
  }, [])

  const patch = useCallback((id: string, p: Partial<Transfer>) => {
    commit(list.current.map((t) => (t.id === id ? { ...t, ...p } : t)))
  }, [commit])

  const runOne = useCallback(async (t: Transfer) => {
    const job = jobs.current.get(t.id)
    if (!job) return
    const abort = new AbortController()
    job.abort = abort
    patch(t.id, { state: 'running', error: undefined })
    const file = job.file
    try {
      let offset = 0
      if (job.sessionId) {
        const status = await request<{ status: string; offset: string }>(`/uploads/resumable/status/${job.sessionId}`)
        if (status.status === 'completed') offset = file.size
        else offset = Number(status.offset) || 0
      } else {
        const media = file.type.startsWith('image/') || file.type.startsWith('video/')
        const mimeType = file.type || 'application/octet-stream'
        // Best guess at when a photo/video was taken, for the Photos timeline.
        const takenAt = media && file.lastModified ? new Date(file.lastModified).toISOString() : undefined
        const init = await request<{ sessionId: string; provider?: string }>('/uploads/resumable/init', {
          method: 'POST',
          body: {
            fileName: file.name,
            mimeType,
            sizeBytes: String(file.size),
            folderId: t.folderId ?? undefined,
            targetAccountId: t.accountId ?? undefined,
            takenAt,
          },
        })
        if (init.provider && init.provider !== 'google_drive') {
          // Only Google takes resumable chunks; other drives (S3) get the whole file in one multipart POST, like the apps.
          // There is no session to resume, so a retry starts over.
          const form = new FormData()
          // The server reads sizeBytes before the file, so the fields go first.
          form.append('sizeBytes', String(file.size))
          form.append('fileName', file.name)
          form.append('mimeType', mimeType)
          if (t.folderId) form.append('folderId', t.folderId)
          if (takenAt) form.append('takenAt', takenAt)
          form.append('file', file, file.name)
          const res = await uploadForm<{ failed?: { fileName?: string; code?: string; message?: string }[] }>(
            '/uploads',
            form,
            (sent, total) => patch(t.id, { progress: Math.min(0.99, sent / Math.max(1, total)) }),
            abort.signal,
          )
          const failed = res?.failed?.[0]
          if (failed) throw new ApiError(400, failed.code ?? 'UPLOAD_FAILED', failed.message ?? 'Upload failed')
          offset = file.size
        } else job.sessionId = init.sessionId
      }
      while (offset < file.size) {
        if (abort.signal.aborted) throw new DOMException('Cancelled', 'AbortError')
        const end = Math.min(offset + CHUNK, file.size)
        const res = await authedFetch(`/uploads/resumable/chunk/${job.sessionId}`, {
          method: 'PUT',
          headers: { 'Content-Range': `bytes ${offset}-${end - 1}/${file.size}` },
          body: file.slice(offset, end),
          signal: abort.signal,
        })
        const data = (await res.json()) as { status: string; offset?: string }
        if (data.status === 'completed') break
        offset = Number(data.offset ?? end)
        patch(t.id, { progress: Math.min(0.99, offset / file.size) })
      }
      patch(t.id, { state: 'done', progress: 1 })
      batch.current.done++
      batch.current.name = t.name
      jobs.current.delete(t.id)
      emitChange('files', 'storage')
    } catch (e) {
      if (abort.signal.aborted || (e instanceof DOMException && e.name === 'AbortError')) {
        patch(t.id, { state: 'cancelled' })
        jobs.current.delete(t.id)
      } else {
        const message = e instanceof ApiError ? e.message : errorMessage(e, 'Upload failed')
        patch(t.id, { state: 'failed', error: message })
        batch.current.failed++
      }
    }
  }, [patch])

  const pump = useCallback(() => {
    while (running.current < PARALLEL) {
      const next = list.current.find((t) => t.state === 'queued')
      if (!next) {
        if (running.current === 0) {
          const { done, failed, name } = batch.current
          batch.current = { done: 0, failed: 0, name: '' }
          if (done > 0) postNotice({ type: 'upload', title: done === 1 ? `Uploaded ${name}` : `Uploaded ${plural(done, 'file')}`, body: 'Saved to the drive your upload rule picked.', to: '/files' })
          if (failed > 0) postNotice({ type: 'uploadFailed', title: `${plural(failed, 'upload')} failed`, body: 'Open the uploads panel to try again.', to: '/files' })
        }
        return
      }
      running.current++
      // Mark as running right away so the next loop iteration picks a different file.
      patch(next.id, { state: 'running' })
      void runOne(next).finally(() => {
        running.current--
        pump()
      })
    }
  }, [patch, runOne])

  const upload = useCallback((files: File[], folderId: string | null, accountId: string | null = null) => {
    if (files.length === 0) return
    const added: Transfer[] = files.map((file) => {
      const id = `t${++seq.current}`
      jobs.current.set(id, { file })
      return { id, name: file.name, size: file.size, progress: 0, state: 'queued', folderId, accountId }
    })
    commit([...list.current, ...added])
    setPanelOpen(true)
    pump()
  }, [commit, pump])

  const uploadTree = useCallback(async (entries: TreeEntry[], parentId: string | null, accountId: string | null = null) => {
    // Create each folder once, parents before children, then queue the files inside them.
    const ids = new Map<string, string | null>([['', parentId]])
    const paths = [...new Set(entries.flatMap((e) => e.dirs.map((_, i) => e.dirs.slice(0, i + 1).join('/'))))].sort((a, b) => a.split('/').length - b.split('/').length)
    for (const path of paths) {
      const parts = path.split('/')
      const parent = ids.get(parts.slice(0, -1).join('/')) ?? null
      const { folder } = await api.folders.create({ name: parts[parts.length - 1], parentId: parent })
      ids.set(path, folder.id)
    }
    if (paths.length) emitChange('folders')
    const byFolder = new Map<string | null, File[]>()
    for (const e of entries) {
      const fid = ids.get(e.dirs.join('/')) ?? parentId
      byFolder.set(fid, [...(byFolder.get(fid) ?? []), e.file])
    }
    byFolder.forEach((files, fid) => upload(files, fid, accountId))
  }, [upload])

  const retry = useCallback((id: string) => {
    if (!jobs.current.has(id)) return
    patch(id, { state: 'queued', error: undefined })
    setPanelOpen(true)
    pump()
  }, [patch, pump])

  const cancel = useCallback((id: string) => {
    const job = jobs.current.get(id)
    const t = list.current.find((x) => x.id === id)
    if (!t) return
    if (t.state === 'running') job?.abort?.abort()
    else {
      jobs.current.delete(id)
      patch(id, { state: 'cancelled' })
    }
  }, [patch])

  const clearFinished = useCallback(() => {
    const keep = list.current.filter((t) => t.state === 'queued' || t.state === 'running' || t.state === 'failed')
    commit(keep)
    if (keep.length === 0) setPanelOpen(false)
  }, [commit])

  const active = transfers.filter((t) => t.state === 'queued' || t.state === 'running').length

  return (
    <UploadContext.Provider value={{ transfers, active, panelOpen, setPanelOpen, upload, uploadTree, retry, cancel, clearFinished }}>
      {children}
    </UploadContext.Provider>
  )
}

export function useUploads() {
  const ctx = useContext(UploadContext)
  if (!ctx) throw new Error('useUploads must be used inside UploadProvider')
  return ctx
}

// ------------------------------------------------------------------------------ reading dropped folders

type FsEntry = { isFile: boolean; isDirectory: boolean; name: string }
type FsFileEntry = FsEntry & { file: (ok: (f: File) => void, fail: (e: unknown) => void) => void }
type FsDirEntry = FsEntry & { createReader: () => { readEntries: (ok: (e: FsEntry[]) => void, fail: (e: unknown) => void) => void } }

async function walk(entry: FsEntry, dirs: string[], out: TreeEntry[]) {
  if (entry.isFile) {
    const file = await new Promise<File>((ok, fail) => (entry as FsFileEntry).file(ok, fail))
    out.push({ file, dirs })
  } else if (entry.isDirectory) {
    const reader = (entry as FsDirEntry).createReader()
    // readEntries returns results in batches; keep reading until it returns none.
    for (;;) {
      const batch = await new Promise<FsEntry[]>((ok, fail) => reader.readEntries(ok, fail))
      if (batch.length === 0) break
      for (const child of batch) await walk(child, [...dirs, entry.name], out)
    }
  }
}

/** Files (with folder structure) from a drag-and-drop. */
export async function entriesFromDrop(dt: DataTransfer): Promise<TreeEntry[]> {
  const roots = Array.from(dt.items)
    .filter((i) => i.kind === 'file')
    .map((i) => (i as DataTransferItem & { webkitGetAsEntry?: () => FsEntry | null }).webkitGetAsEntry?.() ?? null)
  if (roots.length === 0 || roots.some((r) => r === null)) return Array.from(dt.files).map((file) => ({ file, dirs: [] }))
  const out: TreeEntry[] = []
  for (const r of roots) await walk(r as FsEntry, [], out)
  return out
}

/** Files from an <input webkitdirectory>, keeping their folder structure. */
export function entriesFromFolderInput(files: FileList): TreeEntry[] {
  return Array.from(files).map((file) => {
    const rel = (file as File & { webkitRelativePath?: string }).webkitRelativePath || file.name
    return { file, dirs: rel.split('/').slice(0, -1) }
  })
}
