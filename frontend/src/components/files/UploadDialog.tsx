import { useEffect, useRef, useState, type DragEvent } from 'react'
import clsx from 'clsx'
import { CloudUpload, FolderUp, X } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Dialog } from '@/components/ui/Dialog'
import { Select } from '@/components/ui/Field'
import { entriesFromDrop, entriesFromFolderInput, useUploads, type TreeEntry } from '@/context/UploadContext'
import { useToast } from '@/context/ToastContext'
import { api, errorMessage } from '@/lib/api'
import { folderPath, providerLabel } from '@/lib/files'
import { formatBytes, plural } from '@/lib/format'
import type { ConnectedAccount, Folder } from '@/lib/types'

type Props = { open: boolean; onClose: () => void; folderId: string | null; folders: Folder[]; pick?: 'files' | 'folder' }

/** Pick files or a whole folder, choose where they go (folder + drive) and start the upload. */
export function UploadDialog({ open, onClose, folderId, folders, pick }: Props) {
  return (
    <Dialog open={open} onClose={onClose} title="Upload" description="Files go to whichever drive your upload rule picks, unless you choose one.">
      <UploadBody key={String(open)} folderId={folderId} folders={folders} onClose={onClose} pick={pick} />
    </Dialog>
  )
}

function UploadBody({ folderId, folders, onClose, pick }: Omit<Props, 'open'>) {
  const { upload, uploadTree } = useUploads()
  const toast = useToast()
  const [entries, setEntries] = useState<TreeEntry[]>([])
  const [target, setTarget] = useState(folderId ?? '')
  const [accountId, setAccountId] = useState('')
  const [accounts, setAccounts] = useState<ConnectedAccount[]>([])
  const [dragging, setDragging] = useState(false)
  const [busy, setBusy] = useState(false)
  const filesRef = useRef<HTMLInputElement>(null)
  const dirRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    api.accounts.list().then((r) => setAccounts(r.accounts)).catch(() => undefined)
  }, [])

  useEffect(() => {
    // `webkitdirectory` isn't in React's input typings.
    dirRef.current?.setAttribute('webkitdirectory', '')
    // Opened from "Upload files" / "Upload a folder": go straight to the picker (the click still counts as the user's gesture).
    if (pick === 'files') filesRef.current?.click()
    if (pick === 'folder') dirRef.current?.click()
  }, [pick])

  const add = (more: TreeEntry[]) => setEntries((cur) => [...cur, ...more])
  const total = entries.reduce((n, e) => n + e.file.size, 0)
  const hasDirs = entries.some((e) => e.dirs.length > 0)

  async function onDrop(e: DragEvent) {
    e.preventDefault()
    setDragging(false)
    add(await entriesFromDrop(e.dataTransfer))
  }

  async function start() {
    setBusy(true)
    try {
      const dest = target || null
      const acc = accountId || null
      if (hasDirs) await uploadTree(entries, dest, acc)
      else upload(entries.map((e) => e.file), dest, acc)
      onClose()
    } catch (err) {
      toast.error(errorMessage(err, "Couldn't create the folders for this upload"))
    } finally {
      setBusy(false)
    }
  }

  const folderLabel = (f: Folder) => folderPath(folders, f.id).map((p) => p.name).join(' / ')

  return (
    <div className="grid grid-cols-1 gap-4">
      <div
        onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        className={clsx('grid place-items-center gap-3 rounded-[24px] border-2 border-dashed px-4 py-7 text-center transition-colors', dragging ? 'border-iris bg-mist' : 'border-lilac bg-glass-soft')}
      >
        <span className="flex h-14 w-14 items-center justify-center rounded-[20px] bg-primary text-white"><CloudUpload size={26} aria-hidden /></span>
        <p className="text-[15px] font-semibold text-ink">Drop files or folders here</p>
        <div className="flex flex-wrap justify-center gap-2">
          <Button size="sm" variant="soft" icon={CloudUpload} onClick={() => filesRef.current?.click()}>Choose files</Button>
          <Button size="sm" variant="soft" icon={FolderUp} onClick={() => dirRef.current?.click()}>Choose a folder</Button>
        </div>
        <input ref={filesRef} type="file" multiple className="sr-only" tabIndex={-1} aria-hidden onChange={(e) => { if (e.target.files) add(Array.from(e.target.files).map((file) => ({ file, dirs: [] }))); e.target.value = '' }} />
        <input ref={dirRef} type="file" multiple className="sr-only" tabIndex={-1} aria-hidden onChange={(e) => { if (e.target.files) add(entriesFromFolderInput(e.target.files)); e.target.value = '' }} />
      </div>

      {entries.length > 0 && (
        <div className="rounded-[20px] bg-mist p-2">
          <p className="flex justify-between px-2 pb-1.5 pt-1 text-[13px] font-semibold text-ink">
            <span>{plural(entries.length, 'file')}{hasDirs ? ' · keeps folder structure' : ''}</span>
            <span className="text-muted">{formatBytes(total)}</span>
          </p>
          <ul className="grid max-h-44 gap-1 overflow-y-auto">
            {entries.map((e, i) => (
              <li key={`${e.file.name}-${i}`} className="flex items-center gap-2 rounded-[14px] bg-surface py-1.5 pl-3 pr-1 text-[13px]">
                <span className="min-w-0 flex-1 truncate text-ink" title={[...e.dirs, e.file.name].join('/')}>{[...e.dirs, e.file.name].join(' / ')}</span>
                <span className="shrink-0 text-[12px] text-muted">{formatBytes(e.file.size)}</span>
                <button type="button" aria-label={`Remove ${e.file.name}`} onClick={() => setEntries((cur) => cur.filter((_, j) => j !== i))} className="press flex h-8 w-8 items-center justify-center rounded-full text-muted hover:bg-mist hover:text-danger"><X size={15} aria-hidden /></button>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <Select label="Save in" value={target} onChange={(e) => setTarget(e.target.value)}>
          <option value="">My Files</option>
          {folders.map((f) => <option key={f.id} value={f.id}>{folderLabel(f)}</option>)}
        </Select>
        <Select label="Drive" value={accountId} onChange={(e) => setAccountId(e.target.value)} hint={target ? 'Folders that live on a drive keep their files there.' : undefined}>
          <option value="">Automatic (upload rule)</option>
          {accounts.map((a) => <option key={a.id} value={a.id}>{a.displayName || a.email} · {providerLabel(a.provider)}</option>)}
        </Select>
      </div>

      <div className="flex justify-end gap-2.5 pt-1">
        <Button variant="secondary" onClick={onClose} disabled={busy}>Cancel</Button>
        <Button icon={CloudUpload} onClick={start} loading={busy} disabled={entries.length === 0}>{entries.length > 1 ? `Upload ${entries.length} files` : 'Upload'}</Button>
      </div>
    </div>
  )
}
