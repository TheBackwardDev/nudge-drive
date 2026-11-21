import { useMemo } from 'react'
import type { CSSProperties } from 'react'
import { CloudUpload, FolderInput, FolderMinus, FolderPen, FolderPlus, History, Pencil, RefreshCw, RotateCcw, Trash2, type LucideIcon } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { EmptyState, Shimmer } from '@/components/ui/Surface'
import { PageHeader } from '@/components/layout/PageHeader'
import { api } from '@/lib/api'
import { dayLabel, formatBytes, plural, timeOnly } from '@/lib/format'
import { useLoad } from '@/lib/hooks'
import type { AuditLog } from '@/lib/types'

type Meta = { name?: string; fileName?: string; folderName?: string; count?: number; sizeBytes?: string | number }

function meta(log: AuditLog): Meta {
  if (!log.metadata) return {}
  if (typeof log.metadata === 'string') {
    try {
      return JSON.parse(log.metadata) as Meta
    } catch {
      return {}
    }
  }
  return log.metadata as Meta
}

/** Friendly sentence + icon for each audit action (MoreScreens.kt). */
function describe(log: AuditLog): { text: string; icon: LucideIcon; color: string } {
  const m = meta(log)
  const name = m.name ?? m.fileName ?? m.folderName ?? 'an item'
  switch (log.action) {
    case 'UPLOAD_FILE': return { text: `Uploaded ${name}${m.sizeBytes ? ` (${formatBytes(m.sizeBytes)})` : ''}`, icon: CloudUpload, color: '#1e9e57' }
    case 'TRASH_FILE': return { text: `Moved ${name} to trash`, icon: Trash2, color: '#e5484d' }
    case 'RESTORE_FILE': return { text: `Restored ${name}`, icon: RotateCcw, color: '#b07800' }
    case 'PERMANENT_DELETE_FILE': return { text: `Deleted ${name} forever`, icon: Trash2, color: '#c62828' }
    case 'UPDATE_FILE': return { text: `Updated ${name}`, icon: Pencil, color: '#8b72f5' }
    case 'MOVE_FILES': return { text: `Moved ${m.count !== undefined ? plural(m.count, 'file') : 'files'}`, icon: FolderInput, color: '#7059f0' }
    case 'CREATE_FOLDER': return { text: `Created folder ${name}`, icon: FolderPlus, color: '#1e9e57' }
    case 'UPDATE_FOLDER': return { text: `Updated folder ${name}`, icon: FolderPen, color: '#8b72f5' }
    case 'DELETE_FOLDER': return { text: `Deleted folder ${name}`, icon: FolderMinus, color: '#e5484d' }
    default: {
      const words = log.action.toLowerCase().replace(/_/g, ' ')
      return { text: `${words.charAt(0).toUpperCase()}${words.slice(1)}${m.name ? ` · ${m.name}` : ''}`, icon: log.action.includes('SYNC') ? RefreshCw : History, color: '#8b72f5' }
    }
  }
}

/** The last 100 things that happened to your files and folders. */
export default function ActivityPage() {
  const { data, loading, error, reload } = useLoad(() => api.activity())
  const days = useMemo(() => {
    const out: { label: string; logs: AuditLog[] }[] = []
    for (const l of data?.logs ?? []) {
      const label = dayLabel(l.createdAt)
      const last = out[out.length - 1]
      if (last?.label === label) last.logs.push(l)
      else out.push({ label, logs: [l] })
    }
    return out
  }, [data])

  return (
    <div>
      <PageHeader title="Activity" subtitle="Uploads, moves and deletes" back="/files" />
      {loading && !data ? (
        <div className="grid grid-cols-1 gap-2.5">{[0, 1, 2, 3].map((i) => <Shimmer key={i} className="h-[62px] rounded-[18px]" />)}</div>
      ) : error && !data ? (
        <EmptyState icon={History} title="Couldn't load activity" body={error} action={<Button icon={RefreshCw} onClick={() => void reload()}>Try again</Button>} />
      ) : days.length === 0 ? (
        <EmptyState icon={History} title="No activity yet" body="Uploads, moves and deletes will show up here." />
      ) : (
        <div className="grid max-w-3xl grid-cols-1 gap-6">
          {days.map((d) => (
            <section key={d.label} aria-label={d.label}>
              <h2 className="mb-2.5 text-[17px] font-semibold">{d.label}</h2>
              {/* Timeline: a dot per event joined by a thin line (ActivityScreen). */}
              <ol className="stagger">
                {d.logs.map((l, i) => {
                  const x = describe(l)
                  return (
                    <li key={l.id} className="relative flex gap-3 pb-2.5 last:pb-0">
                      {i < d.logs.length - 1 && <span aria-hidden className="absolute bottom-0 left-5 top-10 w-px bg-line" />}
                      <span className="soft-tile relative mt-1 flex h-10 w-10 shrink-0 items-center justify-center rounded-full ring-4 ring-haze/0" style={{ '--k': x.color } as CSSProperties}><x.icon size={18} aria-hidden /></span>
                      <div className="flex min-w-0 flex-1 items-center gap-3 rounded-[18px] border border-card-border bg-surface px-3.5 py-3 shadow-[0_8px_20px_-16px_var(--shadow-color)]">
                        <p className="min-w-0 flex-1 break-words text-[14px] font-medium text-ink">{x.text}</p>
                        <time dateTime={l.createdAt} className="shrink-0 text-[12px] text-muted">{timeOnly(l.createdAt)}</time>
                      </div>
                    </li>
                  )
                })}
              </ol>
            </section>
          ))}
        </div>
      )}
    </div>
  )
}
