import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { Copy, Download, Eye, FolderInput, Link2, Link2Off, Pencil, PenLine, RefreshCw, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Dialog } from '@/components/ui/Dialog'
import { Pill } from '@/components/ui/Surface'
import { FileThumb } from '@/components/files/FileVisuals'
import { InvitePanel } from '@/components/files/ShareDialogs'
import { useToast } from '@/context/ToastContext'
import { api, errorMessage } from '@/lib/api'
import { canEditInApp, fileKind, isGoogleNative, providerLabel } from '@/lib/files'
import { copyText, emitChange, useOnChange } from '@/lib/hooks'
import { formatBytes, fullDate } from '@/lib/format'
import type { FileItem } from '@/lib/types'

export type DetailActions = {
  open: (f: FileItem) => void
  download: (f: FileItem) => void
  share: (f: FileItem) => void
  edit: (f: FileItem) => void
  rename: (f: FileItem) => void
  move: (f: FileItem[]) => void
  trash: (f: FileItem[]) => void
}

/** Side panel (bottom sheet on phones) with a preview, facts and sharing state for one file. */
export function DetailsPanel({ file, onClose, actions }: { file: FileItem | null; onClose: () => void; actions: DetailActions }) {
  return (
    <Dialog open={!!file} onClose={onClose} title="Details" variant="drawer" hideTitle>
      {file && <DetailsBody key={file.id} file={file} actions={actions} onClose={onClose} />}
    </Dialog>
  )
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex justify-between gap-4 py-2.5">
      <dt className="shrink-0 text-[13px] text-muted">{label}</dt>
      <dd className="min-w-0 break-words text-right text-[13.5px] font-semibold text-ink">{children}</dd>
    </div>
  )
}

/** The file's public link, as far as this panel knows. */
type LinkState = { kind: 'loading' } | { kind: 'unknown' } | { kind: 'private' } | { kind: 'public'; url: string; turningOff: boolean }

function DetailsBody({ file, actions, onClose }: { file: FileItem; actions: DetailActions; onClose: () => void }) {
  const toast = useToast()
  const [link, setLink] = useState<LinkState>({ kind: 'loading' })
  const kind = fileKind(file)
  const editable = canEditInApp(file) || (isGoogleNative(file) && file.provider === 'google_drive')

  const loadLink = useCallback(async () => {
    try {
      const { shares } = await api.files.sharedLinks()
      const url = shares.find((s) => s.file.id === file.id)?.url
      setLink(url ? { kind: 'public', url, turningOff: false } : { kind: 'private' })
    } catch {
      // Not knowing is not the same as private.
      setLink((cur) => (cur.kind === 'loading' ? { kind: 'unknown' } : cur))
    }
  }, [file.id])

  useEffect(() => {
    void loadLink()
  }, [loadLink])
  useOnChange(['shares'], loadLink)

  async function turnOff() {
    if (link.kind !== 'public' || link.turningOff) return
    setLink({ ...link, turningOff: true })
    try {
      await api.files.unshare(file.id)
      setLink({ kind: 'private' })
      emitChange('shares')
      toast.show('Public link turned off')
    } catch (e) {
      setLink((cur) => (cur.kind === 'public' ? { ...cur, turningOff: false } : cur))
      toast.error(errorMessage(e))
    }
  }

  const act = (fn: () => void) => () => {
    onClose()
    fn()
  }

  return (
    <div className="grid grid-cols-1 gap-5">
      <button type="button" onClick={act(() => actions.open(file))} aria-label={`Open ${file.name}`} className="press group relative block aspect-[4/3] w-full overflow-hidden rounded-[24px]">
        <FileThumb file={file} fill radius={24} />
        <span className="absolute inset-x-3 bottom-3 rounded-full bg-black/45 px-3 py-1.5 text-center text-[12px] font-semibold text-white opacity-90 backdrop-blur transition-opacity group-hover:opacity-100">
          {kind.key === 'video' ? 'Click to play' : kind.key === 'image' ? 'Click to view full screen' : 'Click to open'}
        </span>
      </button>
      <div>
        <h2 className="break-words text-[20px] font-semibold leading-snug text-ink">{file.name}</h2>
        <p className="mt-1 text-[13px] text-muted">{formatBytes(file.sizeBytes)} · Added {fullDate(file.createdAt)}</p>
      </div>
      <div className="flex flex-wrap gap-2">
        <Button size="sm" icon={Eye} onClick={act(() => actions.open(file))}>Open</Button>
        <Button size="sm" variant="secondary" icon={Download} onClick={() => actions.download(file)}>Download</Button>
        <Button size="sm" variant="secondary" icon={Link2} onClick={act(() => actions.share(file))}>Share link</Button>
        {editable && <Button size="sm" variant="secondary" icon={PenLine} onClick={act(() => actions.edit(file))}>Edit</Button>}
        <Button size="sm" variant="secondary" icon={Pencil} onClick={act(() => actions.rename(file))}>Rename</Button>
        <Button size="sm" variant="secondary" icon={FolderInput} onClick={act(() => actions.move([file]))}>Move</Button>
        <Button size="sm" variant="danger" icon={Trash2} onClick={act(() => actions.trash([file]))}>Trash</Button>
      </div>

      <dl className="divide-y divide-line rounded-[20px] border border-card-border bg-surface px-4">
        <Row label="Type">{isGoogleNative(file) ? 'Google Docs file' : kind.label.replace(/s$/, '')}</Row>
        <Row label="File size">{formatBytes(file.sizeBytes)}</Row>
        <Row label="Stored in">{file.connectedAccount?.email ?? '—'} · {providerLabel(file.provider)}</Row>
        <Row label="Folder">{file.folder?.name ?? 'My Files'}</Row>
        {file.takenAt && <Row label="Taken">{fullDate(file.takenAt)}</Row>}
        <Row label="Added to drive">{fullDate(file.createdAt)}</Row>
        <Row label="MIME type"><span className="font-mono text-[12px] font-medium">{file.mimeType}</span></Row>
      </dl>

      <section className="grid grid-cols-1 gap-3">
        <div className="flex items-center gap-2">
          <h3 className="flex-1 text-[16px] font-semibold">Sharing</h3>
          {link.kind === 'public' ? <Pill tone="success">Public link on</Pill> : link.kind === 'private' ? <Pill>Private</Pill> : link.kind === 'unknown' ? <Pill tone="warn">Couldn't check</Pill> : null}
        </div>
        {link.kind === 'public' ? (
          <div className="flex flex-wrap gap-2">
            <Button size="sm" variant="soft" icon={Copy} onClick={async () => { if (await copyText(link.url)) toast.show('Link copied', { tone: 'success' }) }}>Copy link</Button>
            <Button size="sm" variant="danger" icon={Link2Off} loading={link.turningOff} onClick={turnOff}>Turn off link</Button>
          </div>
        ) : link.kind === 'private' ? (
          <Button size="sm" variant="soft" icon={Link2} className="justify-self-start" onClick={act(() => actions.share(file))}>Create a public link</Button>
        ) : link.kind === 'unknown' ? (
          <Button size="sm" variant="soft" icon={RefreshCw} className="justify-self-start" onClick={() => void loadLink()}>Check again</Button>
        ) : null}
        <h4 className="pt-1 text-[13px] font-semibold text-muted">Invite by email</h4>
        <InvitePanel target={{ type: 'file', id: file.id, name: file.name }} compact />
      </section>
    </div>
  )
}
