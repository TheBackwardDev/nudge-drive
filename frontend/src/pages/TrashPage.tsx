import { useMemo, useState } from 'react'
import { ListChecks, RotateCcw, Trash2, X } from 'lucide-react'
import { Button, IconButton } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/Dialog'
import { EmptyState, Shimmer } from '@/components/ui/Surface'
import { FileRow } from '@/components/files/FileVisuals'
import { useSelection } from '@/components/files/Selection'
import { PageHeader } from '@/components/layout/PageHeader'
import { useToast } from '@/context/ToastContext'
import { api, errorMessage } from '@/lib/api'
import { emitChange, useLoad, useOnChange } from '@/lib/hooks'
import { formatBytes, friendlyDate, plural } from '@/lib/format'

/** Deleted files: restore them, or remove them from the drive for good. */
export default function TrashPage() {
  const toast = useToast()
  const selection = useSelection()
  const { data, loading, error, reload } = useLoad(() => api.files.trashed())
  useOnChange(['files'], reload)
  const [confirm, setConfirm] = useState<string[] | null>(null)
  const files = useMemo(() => data?.files ?? [], [data])
  const chosen = [...selection.selected]

  async function restore(ids: string[]) {
    try {
      for (let i = 0; i < ids.length; i += 100) await api.files.restore(ids.slice(i, i + 100))
      selection.clear()
      emitChange('files', 'storage')
      toast.show(`Restored ${plural(ids.length, 'file')}`, { tone: 'success' })
    } catch (e) {
      toast.error(errorMessage(e))
    }
  }

  async function deleteForever(ids: string[]) {
    let deleted = 0
    let failed = 0
    try {
      for (let i = 0; i < ids.length; i += 100) {
        const r = await api.files.deleteForever(ids.slice(i, i + 100))
        deleted += r.deleted
        failed += r.failed?.length ?? 0
      }
    } catch (e) {
      toast.error(errorMessage(e))
      throw e
    } finally {
      selection.clear()
      emitChange('files', 'storage')
    }
    toast.show(failed ? `Deleted ${deleted}; ${plural(failed, 'file')} couldn't be removed from the drive` : `Deleted ${plural(deleted, 'file')} forever`, { tone: failed ? 'error' : 'success' })
  }

  return (
    <div>
      {selection.selecting ? (
        <div className="sticky top-[max(12px,env(safe-area-inset-top))] z-20 mb-4 mt-2.5 flex items-center gap-1 rounded-[26px] bg-inverse px-2 py-1.5 text-on-inverse shadow-lift lg:top-20">
          <IconButton icon={X} label="Clear selection" tone="inverse" onClick={selection.clear} />
          <p className="flex-1 pl-1 font-display text-[16px] font-semibold" aria-live="polite">{selection.selected.size} selected</p>
          <IconButton icon={ListChecks} label="Select all" tone="inverse" onClick={() => selection.setAll(files.map((f) => f.id))} />
          <IconButton icon={RotateCcw} label="Restore selected" tone="inverse" onClick={() => void restore(chosen)} />
          <IconButton icon={Trash2} label="Delete selected forever" tone="inverse" onClick={() => setConfirm(chosen)} />
        </div>
      ) : (
        <PageHeader
          title="Trash"
          subtitle={data ? (files.length ? `${plural(files.length, 'file')} · ${formatBytes(files.reduce((n, f) => n + Number(f.sizeBytes), 0))}` : 'Empty') : undefined}
          back="/files"
          actions={files.length > 0 && <Button variant="danger" icon={Trash2} onClick={() => setConfirm(files.map((f) => f.id))}>Empty trash</Button>}
        />
      )}
      {loading && !data ? (
        <div className="grid grid-cols-1 gap-2.5">{[0, 1, 2].map((i) => <Shimmer key={i} className="h-[66px] rounded-[20px]" />)}</div>
      ) : error && !data ? (
        <p role="alert" className="rounded-[18px] bg-danger-soft p-4 text-[14px] text-danger">{error}</p>
      ) : files.length === 0 ? (
        <EmptyState icon={Trash2} title="Trash is empty" body="Files you delete stay here until you remove them for good." />
      ) : (
        <>
          <p className="mb-3 text-[13px] text-muted">Select files to restore them where they were, or delete them forever.</p>
          <ul className="stagger grid grid-cols-1 gap-2.5">
            {files.map((f) => (
              <li key={f.id}>
                <FileRow
                  file={f}
                  selected={selection.selected.has(f.id)}
                  selecting
                  subtitle={`${formatBytes(f.sizeBytes)} · deleted ${friendlyDate(f.deletedAt)} · ${f.connectedAccount?.email ?? ''}`}
                  onOpen={() => selection.toggle(f.id)}
                  onToggle={() => selection.toggle(f.id)}
                />
              </li>
            ))}
          </ul>
        </>
      )}
      <ConfirmDialog
        open={!!confirm}
        onClose={() => setConfirm(null)}
        title={confirm ? `Delete ${plural(confirm.length, 'file')} forever?` : ''}
        body="They'll be removed from Google Drive / S3 too. This can't be undone."
        confirmLabel="Delete forever"
        danger
        onConfirm={() => deleteForever(confirm ?? [])}
      />
    </div>
  )
}
