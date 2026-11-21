import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { Copy, Download, Eye, FolderInput, FolderOpen, Info, Link2, Palette, Pencil, PenLine, Trash2, Upload, UserPlus } from 'lucide-react'
import { ConfirmDialog } from '@/components/ui/Dialog'
import type { MenuItem } from '@/components/ui/Menu'
import { DetailsPanel } from '@/components/files/DetailsPanel'
import { Editor } from '@/components/files/Editor'
import { FolderDialog, FolderPicker, TextPrompt, type FolderLook } from '@/components/files/FolderDialogs'
import { InviteDialog, ShareDialog, type InviteTarget } from '@/components/files/ShareDialogs'
import { UploadDialog } from '@/components/files/UploadDialog'
import { Viewer } from '@/components/files/Viewer'
import { useToast } from '@/context/ToastContext'
import { api, authedFetch, errorMessage, previewUrl } from '@/lib/api'
import { canEditInApp, descendantIds, isGoogleNative } from '@/lib/files'
import { copyText, emitChange, useOnChange } from '@/lib/hooks'
import { plural } from '@/lib/format'
import { getAccessToken } from '@/lib/session'
import type { FileItem, Folder } from '@/lib/types'

export type DocKind = 'document' | 'spreadsheet' | 'presentation'
const DOC_LABEL: Record<DocKind, string> = { document: 'Google Doc', spreadsheet: 'Google Sheet', presentation: 'Google Slides' }

type FileActionsApi = {
  folders: Folder[]
  open: (file: FileItem, siblings?: FileItem[]) => void
  download: (file: FileItem) => void
  downloadZip: (files: FileItem[]) => Promise<void>
  share: (file: FileItem) => void
  invite: (target: InviteTarget) => void
  details: (file: FileItem) => void
  rename: (file: FileItem) => void
  move: (files: FileItem[], onDone?: () => void) => void
  moveTo: (fileIds: string[], folderId: string | null) => Promise<void>
  trash: (files: FileItem[], onDone?: () => void) => Promise<void>
  edit: (file: FileItem) => void
  newFolder: (parentId: string | null) => void
  editFolder: (folder: Folder) => void
  moveFolder: (folder: Folder) => void
  deleteFolder: (folder: Folder) => void
  copyFolderLink: (folder: Folder) => void
  upload: (folderId: string | null, pick?: 'files' | 'folder') => void
  newDocument: (kind: DocKind, folderId: string | null) => void
  fileMenu: (file: FileItem, siblings?: FileItem[]) => MenuItem[]
  folderMenu: (folder: Folder, onOpen: () => void) => MenuItem[]
}

const Ctx = createContext<FileActionsApi | null>(null)

/** Hosts every file / folder dialog once, so any screen (and the viewer) can trigger them. */
export function FileActionsProvider({ children }: { children: ReactNode }) {
  const toast = useToast()
  const [folders, setFolders] = useState<Folder[]>([])
  const [viewer, setViewer] = useState<{ files: FileItem[]; index: number } | null>(null)
  const [editor, setEditor] = useState<FileItem | null>(null)
  const [sharing, setSharing] = useState<FileItem | null>(null)
  const [inviting, setInviting] = useState<InviteTarget | null>(null)
  const [detail, setDetail] = useState<FileItem | null>(null)
  const [renaming, setRenaming] = useState<FileItem | null>(null)
  const [folderForm, setFolderForm] = useState<{ parentId: string | null; folder?: Folder } | null>(null)
  const [moving, setMoving] = useState<{ files?: FileItem[]; folder?: Folder; onDone?: () => void } | null>(null)
  const [deleting, setDeleting] = useState<Folder | null>(null)
  const [uploadTo, setUploadTo] = useState<{ folderId: string | null; pick?: 'files' | 'folder' } | null>(null)
  const [newDoc, setNewDoc] = useState<{ kind: DocKind; folderId: string | null } | null>(null)

  const loadFolders = useCallback(async () => {
    if (!getAccessToken()) return
    try {
      setFolders((await api.folders.all()).folders)
    } catch {
      /* screens show their own errors */
    }
  }, [])
  useEffect(() => { void loadFolders() }, [loadFolders])
  useOnChange(['folders'], loadFolders)

  const fail = useCallback((e: unknown, fallback?: string) => toast.error(errorMessage(e, fallback)), [toast])

  const download = useCallback(async (file: FileItem) => {
    try {
      const a = document.createElement('a')
      if (isGoogleNative(file)) {
        // Google files are exported (PDF / Office); the server names the download with the right extension.
        const res = await authedFetch(`/files/${encodeURIComponent(file.id)}/download`)
        const name = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(res.headers.get('Content-Disposition') ?? '')?.[1]
        a.href = URL.createObjectURL(await res.blob())
        a.download = name ? decodeURIComponent(name) : file.name
        window.setTimeout(() => URL.revokeObjectURL(a.href), 30_000)
      } else {
        // Same-origin short-lived URL: the browser streams it straight to disk.
        a.href = await previewUrl(file.id)
        a.download = file.name
      }
      document.body.appendChild(a)
      a.click()
      a.remove()
      toast.show(`Downloading ${file.name}`)
    } catch (e) {
      fail(e, 'Download failed')
    }
  }, [toast, fail])

  const downloadZip = useCallback(async (files: FileItem[]) => {
    if (files.length === 0) return
    toast.show(`Preparing a zip of ${plural(files.length, 'file')}…`)
    try {
      const res = await authedFetch('/files/batch-download', { method: 'POST', body: { fileIds: files.slice(0, 100).map((f) => f.id) } })
      const url = URL.createObjectURL(await res.blob())
      const a = document.createElement('a')
      a.href = url
      a.download = 'nudge-drive.zip'
      a.click()
      window.setTimeout(() => URL.revokeObjectURL(url), 30_000)
      if (files.length > 100) toast.show('Zips hold up to 100 files; the first 100 were included.')
    } catch (e) {
      fail(e, 'Zip download failed')
    }
  }, [toast, fail])

  const trash = useCallback(async (files: FileItem[], onDone?: () => void) => {
    const ids = files.map((f) => f.id)
    try {
      for (let i = 0; i < ids.length; i += 100) await api.files.trash(ids.slice(i, i + 100))
      emitChange('files', 'storage')
      onDone?.()
      setViewer((v) => {
        if (!v) return v
        const rest = v.files.filter((f) => !ids.includes(f.id))
        return rest.length ? { files: rest, index: Math.min(v.index, rest.length - 1) } : null
      })
      toast.show(files.length === 1 ? `Moved “${files[0].name}” to trash` : `Moved ${files.length} files to trash`, {
        action: {
          label: 'Undo',
          onClick: async () => {
            try {
              for (let i = 0; i < ids.length; i += 100) await api.files.restore(ids.slice(i, i + 100))
              emitChange('files', 'storage')
              toast.show('Restored')
            } catch (e) {
              fail(e)
            }
          },
        },
      })
    } catch (e) {
      fail(e)
    }
  }, [toast, fail])

  const moveTo = useCallback(async (fileIds: string[], folderId: string | null) => {
    try {
      for (let i = 0; i < fileIds.length; i += 100) await api.files.move(fileIds.slice(i, i + 100), folderId)
      emitChange('files')
      const name = folderId ? folders.find((f) => f.id === folderId)?.name ?? 'folder' : 'My Files'
      toast.show(`Moved ${fileIds.length === 1 ? '1 file' : `${fileIds.length} files`} to ${name}`, { tone: 'success' })
    } catch (e) {
      fail(e)
      throw e
    }
  }, [folders, toast, fail])

  const edit = useCallback((file: FileItem) => {
    if (canEditInApp(file)) {
      setViewer(null)
      setEditor(file)
      return
    }
    if (isGoogleNative(file) && file.provider === 'google_drive') {
      // Open the tab now (inside the click) so pop-up blockers allow it, then point it at Google.
      const w = window.open('', '_blank')
      api.files.editLink(file.id)
        .then((r) => {
          if (w) w.location.href = r.url
          else window.location.href = r.url
        })
        .catch((e) => {
          w?.close()
          fail(e)
        })
      return
    }
    toast.show("This file type can't be edited in the browser.")
  }, [toast, fail])

  const copyFolderLink = useCallback(async (folder: Folder) => {
    const url = folder.providerFolderId ? `https://drive.google.com/drive/folders/${folder.providerFolderId}` : `${window.location.origin}/files?folder=${folder.id}`
    if (await copyText(url)) toast.show(folder.providerFolderId ? 'Google Drive folder link copied' : 'Folder link copied', { tone: 'success' })
  }, [toast])

  const open = useCallback((file: FileItem, siblings?: FileItem[]) => {
    const list = siblings && siblings.some((f) => f.id === file.id) ? siblings : [file]
    setViewer({ files: list, index: list.findIndex((f) => f.id === file.id) })
  }, [])

  const fileMenu = useCallback((file: FileItem, siblings?: FileItem[]): MenuItem[] => {
    const editable = canEditInApp(file) || (isGoogleNative(file) && file.provider === 'google_drive')
    return [
      { label: 'Open', icon: Eye, onSelect: () => open(file, siblings) },
      { label: 'Download', icon: Download, onSelect: () => void download(file) },
      { label: 'Share link', icon: Link2, onSelect: () => setSharing(file) },
      { label: 'Invite people', icon: UserPlus, onSelect: () => setInviting({ type: 'file', id: file.id, name: file.name }) },
      ...(editable ? [{ label: isGoogleNative(file) ? 'Edit in Google' : 'Edit', icon: PenLine, onSelect: () => edit(file) }] : []),
      'divider',
      { label: 'Rename', icon: Pencil, onSelect: () => setRenaming(file) },
      { label: 'Move', icon: FolderInput, onSelect: () => setMoving({ files: [file] }) },
      { label: 'Details', icon: Info, onSelect: () => setDetail(file) },
      'divider',
      { label: 'Move to trash', icon: Trash2, danger: true, onSelect: () => void trash([file]) },
    ]
  }, [open, download, edit, trash])

  const folderMenu = useCallback((folder: Folder, onOpen: () => void): MenuItem[] => [
    { label: 'Open', icon: FolderOpen, onSelect: onOpen },
    { label: 'Upload here', icon: Upload, onSelect: () => setUploadTo({ folderId: folder.id }) },
    { label: 'Rename & colour', icon: Palette, onSelect: () => setFolderForm({ parentId: folder.parentId, folder }) },
    { label: 'Move', icon: FolderInput, onSelect: () => setMoving({ folder }) },
    { label: 'Invite people', icon: UserPlus, onSelect: () => setInviting({ type: 'folder', id: folder.id, name: folder.name }) },
    { label: 'Copy link', icon: Copy, onSelect: () => void copyFolderLink(folder) },
    'divider',
    { label: 'Delete folder', icon: Trash2, danger: true, onSelect: () => setDeleting(folder) },
  ], [copyFolderLink])

  async function saveFolder(v: FolderLook) {
    if (!folderForm) return
    try {
      if (folderForm.folder) {
        await api.folders.update(folderForm.folder.id, { name: v.name, color: v.color, iconUrl: v.iconUrl })
        toast.show('Folder updated', { tone: 'success' })
      } else {
        await api.folders.create({ name: v.name, color: v.color, iconUrl: v.iconUrl, parentId: folderForm.parentId })
        toast.show(`Folder “${v.name}” created`, { tone: 'success' })
      }
      emitChange('folders')
      setFolderForm(null)
    } catch (e) {
      fail(e)
    }
  }

  async function renameFile(name: string) {
    if (!renaming) return
    try {
      await api.files.update(renaming.id, { name })
      emitChange('files')
      toast.show('Renamed', { tone: 'success' })
      setRenaming(null)
    } catch (e) {
      fail(e)
    }
  }

  async function pickDestination(folderId: string | null) {
    if (!moving) return
    if (moving.folder) {
      try {
        await api.folders.update(moving.folder.id, { parentId: folderId })
        emitChange('folders')
        toast.show(`Moved “${moving.folder.name}”`, { tone: 'success' })
      } catch (e) {
        fail(e)
        throw e
      }
    } else if (moving.files) {
      await moveTo(moving.files.map((f) => f.id), folderId)
      moving.onDone?.()
    }
  }

  async function createDoc(name: string) {
    if (!newDoc) return
    const w = window.open('', '_blank')
    try {
      const r = await api.files.createDocument(newDoc.kind, name, newDoc.folderId)
      emitChange('files')
      toast.show(`Created “${r.file.name}”`, { tone: 'success' })
      if (r.url && w) w.location.href = r.url
      else w?.close()
      setNewDoc(null)
    } catch (e) {
      w?.close()
      fail(e)
    }
  }

  const value = useMemo<FileActionsApi>(() => ({
    folders,
    open,
    download: (f) => void download(f),
    downloadZip,
    share: setSharing,
    invite: setInviting,
    details: setDetail,
    rename: setRenaming,
    move: (files, onDone) => setMoving({ files, onDone }),
    moveTo,
    trash,
    edit,
    newFolder: (parentId) => setFolderForm({ parentId }),
    editFolder: (folder) => setFolderForm({ parentId: folder.parentId, folder }),
    moveFolder: (folder) => setMoving({ folder }),
    deleteFolder: setDeleting,
    copyFolderLink: (f) => void copyFolderLink(f),
    upload: (folderId, pick) => setUploadTo({ folderId, pick }),
    newDocument: (kind, folderId) => setNewDoc({ kind, folderId }),
    fileMenu,
    folderMenu,
  }), [folders, open, download, downloadZip, moveTo, trash, edit, copyFolderLink, fileMenu, folderMenu])

  const detailActions = { open, download: (f: FileItem) => void download(f), share: setSharing, edit, rename: setRenaming, move: (files: FileItem[]) => setMoving({ files }), trash: (files: FileItem[]) => void trash(files) }

  return (
    <Ctx.Provider value={value}>
      {children}
      {viewer && (
        <Viewer
          files={viewer.files}
          index={viewer.index}
          onIndex={(index) => setViewer((v) => (v ? { ...v, index } : v))}
          onClose={() => setViewer(null)}
          actions={{ download: (f) => void download(f), share: setSharing, details: setDetail, edit, trash: (f) => void trash([f]) }}
        />
      )}
      {editor && <Editor file={editor} onClose={() => { setEditor(null); emitChange('files') }} />}
      <ShareDialog file={sharing} onClose={() => setSharing(null)} />
      <InviteDialog target={inviting} onClose={() => setInviting(null)} />
      <DetailsPanel file={detail} onClose={() => setDetail(null)} actions={detailActions} />
      <TextPrompt open={!!renaming} onClose={() => setRenaming(null)} title="Rename file" label="File name" initial={renaming?.name ?? ''} confirm="Rename" onSubmit={renameFile} selectBase requireChange />
      <TextPrompt open={!!newDoc} onClose={() => setNewDoc(null)} title={newDoc ? `New ${DOC_LABEL[newDoc.kind]}` : ''} label="Name" initial={newDoc ? `Untitled ${DOC_LABEL[newDoc.kind].replace('Google ', '').toLowerCase()}` : ''} confirm="Create" onSubmit={createDoc} />
      <FolderDialog open={!!folderForm} onClose={() => setFolderForm(null)} initial={folderForm?.folder} title={folderForm?.folder ? 'Edit folder' : 'New folder'} onSubmit={saveFolder} />
      <FolderPicker
        open={!!moving}
        onClose={() => setMoving(null)}
        title={moving?.folder ? `Move “${moving.folder.name}”` : moving?.files ? (moving.files.length === 1 ? `Move “${moving.files[0].name}”` : `Move ${moving.files.length} files`) : 'Move'}
        folders={folders}
        exclude={moving?.folder ? descendantIds(folders, moving.folder.id) : undefined}
        startAt={moving?.folder ? moving.folder.parentId : moving?.files?.[0]?.folderId ?? null}
        onPick={pickDestination}
        onNewFolder={(parentId) => setFolderForm({ parentId })}
      />
      <ConfirmDialog
        open={!!deleting}
        onClose={() => setDeleting(null)}
        title={`Delete “${deleting?.name ?? ''}”?`}
        danger
        confirmLabel="Delete folder"
        body={
          <>
            <p>The folder, every folder inside it and all their files are removed.</p>
            <p className="mt-2 font-semibold text-danger">Files inside are also deleted from Google Drive. This can't be undone there.</p>
          </>
        }
        onConfirm={async () => {
          if (!deleting) return
          try {
            await api.folders.remove(deleting.id)
            emitChange('folders', 'files', 'storage')
            toast.show(`Deleted “${deleting.name}”`)
          } catch (e) {
            fail(e)
            throw e
          }
        }}
      />
      <UploadDialog open={!!uploadTo} onClose={() => setUploadTo(null)} folderId={uploadTo?.folderId ?? null} folders={folders} pick={uploadTo?.pick} />
    </Ctx.Provider>
  )
}

export function useFileActions() {
  const ctx = useContext(Ctx)
  if (!ctx) throw new Error('useFileActions must be used inside FileActionsProvider')
  return ctx
}
