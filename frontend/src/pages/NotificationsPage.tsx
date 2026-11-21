import { useState, type CSSProperties } from 'react'
import { useNavigate } from 'react-router-dom'
import clsx from 'clsx'
import { BellOff, BellPlus, CheckCheck, CircleAlert, CloudUpload, Download, HardDrive, Info, Link2, Trash2, TriangleAlert, UserPlus, X, type LucideIcon } from 'lucide-react'
import { Button, IconButton } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/Dialog'
import { EmptyState } from '@/components/ui/Surface'
import { PageHeader } from '@/components/layout/PageHeader'
import { clearNotices, markAllRead, markRead, PREF_BROWSER, removeNotice, useNotices, type Notice, type NoticeType } from '@/lib/notices'
import { usePref } from '@/lib/hooks'

const LOOK: Record<NoticeType, { icon: LucideIcon; color: string }> = {
  upload: { icon: CloudUpload, color: '#7059f0' },
  uploadFailed: { icon: CircleAlert, color: '#e5484d' },
  download: { icon: Download, color: '#1e9e57' },
  drive: { icon: HardDrive, color: '#8b72f5' },
  storage: { icon: TriangleAlert, color: '#f59e0b' },
  share: { icon: Link2, color: '#1e9e57' },
  invite: { icon: UserPlus, color: '#a23fcf' },
  info: { icon: Info, color: '#8b72f5' },
}

const timeFmt = new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' })
const dayFmt = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short' })

function group(at: number) {
  const d = new Date(at)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  if (d >= today) return 'Today'
  if (d.getTime() >= today.getTime() - 86_400_000) return 'Yesterday'
  return 'Earlier'
}

/** The inbox: uploads, storage alerts and shares, grouped by day (NotificationsScreen). */
export default function NotificationsPage() {
  const navigate = useNavigate()
  const notices = useNotices()
  const [confirmClear, setConfirmClear] = useState(false)
  const [browserPref, setBrowserPref] = usePref(PREF_BROWSER, 'off', ['on', 'off'] as const)
  const [permission, setPermission] = useState(() => ('Notification' in window ? Notification.permission : 'denied'))
  const unread = notices.some((n) => !n.read)
  const askable = 'Notification' in window && permission !== 'denied' && (permission !== 'granted' || browserPref === 'off')

  async function enableBrowser() {
    const p = permission === 'granted' ? 'granted' : await Notification.requestPermission()
    setPermission(p)
    if (p === 'granted') setBrowserPref('on')
  }

  function open(n: Notice) {
    markRead(n.id)
    if (n.to) navigate(n.to)
  }

  const groups = (['Today', 'Yesterday', 'Earlier'] as const).map((g) => ({ g, items: notices.filter((n) => group(n.at) === g) })).filter((x) => x.items.length)

  return (
    <div className="max-w-3xl">
      <PageHeader
        title="Notifications"
        back="/profile"
        actions={
          notices.length > 0 && (
            <>
              {unread && <IconButton icon={CheckCheck} label="Mark all as read" onClick={markAllRead} />}
              <IconButton icon={Trash2} label="Clear all" onClick={() => setConfirmClear(true)} />
            </>
          )
        }
      />

      {askable && (
        <div className="rise mb-5 flex items-center gap-3 rounded-[22px] border border-card-border bg-surface p-4 shadow-soft">
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[14px] bg-mist text-link"><BellPlus size={21} aria-hidden /></span>
          <div className="min-w-0 flex-1">
            <p className="text-[14px] font-semibold text-ink">Turn on browser notifications</p>
            <p className="text-[12.5px] text-muted">Get told when uploads finish, friends share files or a drive fills up.</p>
          </div>
          <Button size="sm" onClick={() => void enableBrowser()}>Turn on</Button>
        </div>
      )}

      {groups.length === 0 ? (
        <EmptyState icon={BellOff} title="You're all caught up" body="Uploads, shares and storage alerts will show up here." />
      ) : (
        groups.map(({ g, items }) => (
          <section key={g} aria-label={g} className="mb-5">
            <h2 className="mb-2 px-1 text-[13px] font-semibold uppercase tracking-wider text-subtle">{g}</h2>
            <ul className="stagger grid grid-cols-1 gap-2">
              {items.map((n) => {
                const look = LOOK[n.type] ?? LOOK.info
                const at = new Date(n.at)
                return (
                  <li key={n.id} className="group relative">
                    <button
                      type="button"
                      onClick={() => open(n)}
                      className={clsx('press press-row flex w-full items-start gap-3 rounded-[20px] border p-3.5 pr-12 text-left shadow-[0_8px_20px_-16px_var(--shadow-color)] hover:border-lilac', n.read ? 'border-card-border bg-surface' : 'border-lilac/70 bg-surface')}
                    >
                      <span className="soft-tile flex h-11 w-11 shrink-0 items-center justify-center rounded-[14px]" style={{ '--k': look.color } as CSSProperties}><look.icon size={20} aria-hidden /></span>
                      <span className="min-w-0 flex-1">
                        <span className="flex items-center gap-2">
                          <span className="min-w-0 flex-1 truncate text-[14px] font-semibold text-ink">{n.title}</span>
                          {!n.read && <span className="h-2 w-2 shrink-0 rounded-full bg-primary" aria-label="Unread" />}
                        </span>
                        <span className="mt-0.5 line-clamp-3 block text-[13px] text-muted">{n.body}</span>
                        <span className="mt-1 block text-[11.5px] font-medium text-subtle">{g === 'Earlier' ? `${dayFmt.format(at)} · ${timeFmt.format(at)}` : timeFmt.format(at)}</span>
                      </span>
                    </button>
                    <button type="button" onClick={() => removeNotice(n.id)} aria-label={`Remove “${n.title}”`} className="press absolute right-2 top-2 flex h-9 w-9 items-center justify-center rounded-full text-subtle hover:bg-hover hover:text-ink">
                      <X size={16} aria-hidden />
                    </button>
                  </li>
                )
              })}
            </ul>
          </section>
        ))
      )}

      <ConfirmDialog
        open={confirmClear}
        onClose={() => setConfirmClear(false)}
        title="Clear all notifications?"
        body="This only clears the list on this device."
        confirmLabel="Clear"
        danger
        onConfirm={clearNotices}
      />
    </div>
  )
}
