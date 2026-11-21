import { useState } from 'react'
import clsx from 'clsx'
import { ChevronDown, CircleAlert, CircleCheck, CloudUpload, RotateCcw, X } from 'lucide-react'
import { Dialog } from '@/components/ui/Dialog'
import { useUploads, type Transfer } from '@/context/UploadContext'
import { useIsDesktop } from '@/lib/hooks'
import { formatBytes, plural } from '@/lib/format'

function TransferRow({ t }: { t: Transfer }) {
  const { retry, cancel } = useUploads()
  const icon = t.state === 'done' ? <CircleCheck size={20} className="text-success" aria-hidden /> : t.state === 'failed' ? <CircleAlert size={20} className="text-danger" aria-hidden /> : <CloudUpload size={20} className="text-iris" aria-hidden />
  return (
    <li className="flex items-center gap-3 rounded-[18px] border border-card-border bg-surface p-3">
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[13px] bg-mist">{icon}</span>
      <div className="min-w-0 flex-1">
        <p className="truncate text-[13.5px] font-semibold text-ink" title={t.name}>{t.name}</p>
        {t.state === 'failed' ? (
          <p className="line-clamp-2 text-[12px] text-danger">{t.error ?? 'Failed'}</p>
        ) : t.state === 'done' ? (
          <p className="text-[12px] text-muted">Uploaded · {formatBytes(t.size)}</p>
        ) : t.state === 'cancelled' ? (
          <p className="text-[12px] text-subtle">Cancelled</p>
        ) : (
          <>
            <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-mist" role="progressbar" aria-label={`Uploading ${t.name}`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(t.progress * 100)}>
              <div className="h-full rounded-full bg-iris transition-[width]" style={{ width: `${Math.max(3, t.progress * 100)}%` }} />
            </div>
            <p className="mt-1 text-[11px] text-muted">{t.state === 'queued' ? 'Waiting…' : t.progress >= 0.99 ? 'Finishing on the server…' : `${formatBytes(t.size * t.progress)} of ${formatBytes(t.size)}`}</p>
          </>
        )}
      </div>
      {t.state === 'failed' && (
        <button type="button" onClick={() => retry(t.id)} aria-label={`Retry ${t.name}`} className="press flex h-9 w-9 items-center justify-center rounded-full text-link hover:bg-mist"><RotateCcw size={17} aria-hidden /></button>
      )}
      {(t.state === 'queued' || t.state === 'running' || t.state === 'failed') && (
        <button type="button" onClick={() => cancel(t.id)} aria-label={`Cancel ${t.name}`} className="press flex h-9 w-9 items-center justify-center rounded-full text-subtle hover:bg-mist hover:text-ink"><X size={17} aria-hidden /></button>
      )}
    </li>
  )
}

function summary(transfers: Transfer[], active: number) {
  const failed = transfers.filter((t) => t.state === 'failed').length
  if (active > 0) return `Uploading ${plural(active, 'file')}`
  if (failed > 0) return `${plural(failed, 'upload')} failed`
  return 'Uploads complete'
}

/** Upload progress: a card in the corner on wide screens, a pill above the tab bar on phones (TransfersPill). */
export function Transfers() {
  const { transfers, active, panelOpen, setPanelOpen, clearFinished } = useUploads()
  const desktop = useIsDesktop()
  const [collapsed, setCollapsed] = useState(false)
  const [sheetOpen, setSheetOpen] = useState(false)
  // An emptied list ("Clear finished") closes the sheet for good, so the next upload doesn't pop it open.
  if (sheetOpen && transfers.length === 0) setSheetOpen(false)
  if (transfers.length === 0) return null
  const running = transfers.filter((t) => t.state === 'running' || t.state === 'queued')
  const progress = running.length ? running.reduce((n, t) => n + t.progress, 0) / running.length : 1
  const hasFinished = transfers.some((t) => t.state === 'done' || t.state === 'failed' || t.state === 'cancelled')
  const list = (
    <ul className="grid grid-cols-1 gap-2">{[...transfers].reverse().map((t) => <TransferRow key={t.id} t={t} />)}</ul>
  )

  if (desktop) {
    if (!panelOpen) return null
    return (
      <section aria-label="Uploads" className="rise fixed bottom-6 right-6 z-[60] w-[380px] overflow-hidden rounded-[22px] border border-card-border bg-surface shadow-lift">
        <div className="flex items-center gap-2 bg-inverse py-2.5 pl-4 pr-2 text-on-inverse">
          <p className="flex-1 text-[14px] font-semibold" aria-live="polite">{summary(transfers, active)}{active > 0 && ` · ${Math.round(progress * 100)}%`}</p>
          {hasFinished && active === 0 && <button type="button" onClick={clearFinished} className="press rounded-xl px-2.5 py-1 text-[13px] font-semibold opacity-80 hover:opacity-100">Clear</button>}
          <button type="button" onClick={() => setCollapsed((c) => !c)} aria-label={collapsed ? 'Expand uploads' : 'Collapse uploads'} aria-expanded={!collapsed} className="press flex h-8 w-8 items-center justify-center rounded-full hover:bg-white/15 dark:hover:bg-black/10">
            <ChevronDown size={18} className={clsx('transition-transform', collapsed && 'rotate-180')} aria-hidden />
          </button>
          <button type="button" onClick={() => setPanelOpen(false)} aria-label="Hide uploads" className="press flex h-8 w-8 items-center justify-center rounded-full hover:bg-white/15 dark:hover:bg-black/10"><X size={18} aria-hidden /></button>
        </div>
        {!collapsed && <div className="max-h-[50vh] overflow-y-auto p-3">{list}</div>}
      </section>
    )
  }

  return (
    <>
      {(active > 0 || transfers.some((t) => t.state === 'failed')) && (
        <button
          type="button"
          onClick={() => setSheetOpen(true)}
          className="glass-strong press fixed bottom-[calc(96px+env(safe-area-inset-bottom))] left-1/2 z-[55] flex -translate-x-1/2 items-center gap-2.5 rounded-[24px] py-2 pl-2 pr-4 shadow-lift"
        >
          <span className="relative flex h-8 w-8 items-center justify-center">
            <svg width="32" height="32" className="-rotate-90" aria-hidden>
              <circle cx="16" cy="16" r="13" fill="none" stroke="var(--mist)" strokeWidth="3" />
              <circle cx="16" cy="16" r="13" fill="none" stroke="#8b72f5" strokeWidth="3" strokeLinecap="round" strokeDasharray={`${81.7 * progress} 81.7`} />
            </svg>
            <CloudUpload size={14} className="absolute text-link" aria-hidden />
          </span>
          <span className="text-[13px] font-semibold text-ink">{summary(transfers, active)}{active > 0 && ` · ${Math.round(progress * 100)}%`}</span>
        </button>
      )}
      <Dialog open={sheetOpen} onClose={() => setSheetOpen(false)} title="Uploads" description={summary(transfers, active)}>
        {hasFinished && <button type="button" onClick={clearFinished} className="press mb-3 rounded-xl px-2 py-1 text-[13px] font-semibold text-link">Clear finished</button>}
        {list}
      </Dialog>
    </>
  )
}
