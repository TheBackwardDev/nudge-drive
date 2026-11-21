import { useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import clsx from 'clsx'
import { BadgeCheck, Bell, Check, Info, LayoutGrid, LogOut, Moon, Sun, SunMoon, Trash2, TriangleAlert, type LucideIcon } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ListRow, Panel, Pill } from '@/components/ui/Surface'
import { PageHeader } from '@/components/layout/PageHeader'
import { AdminSection } from '@/pages/AdminSection'
import { useAuth } from '@/context/AuthContext'
import { useTheme, type ThemePref } from '@/context/ThemeContext'
import { useToast } from '@/context/ToastContext'
import { usePref } from '@/lib/hooks'
import { PREF_ALERTS, PREF_BROWSER } from '@/lib/notices'

function Group({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section aria-label={title} className="grid grid-cols-1 gap-2">
      <h2 className="px-1 text-[13px] font-semibold uppercase tracking-wider text-subtle">{title}</h2>
      <Panel className="p-2">{children}</Panel>
    </section>
  )
}

/** A switch row with an icon tile (SettingSwitch). */
function ToggleRow({ icon: Icon, title, subtitle, checked, onChange, disabled }: { icon: LucideIcon; title: string; subtitle: string; checked: boolean; onChange: (v: boolean) => void; disabled?: boolean }) {
  return (
    <label className={clsx('flex items-center gap-3 rounded-[18px] px-3 py-2.5', disabled ? 'opacity-55' : 'cursor-pointer hover:bg-hover')}>
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[13px] bg-mist text-link"><Icon size={19} aria-hidden /></span>
      <span className="min-w-0 flex-1">
        <span className="block text-[14.5px] font-semibold text-ink">{title}</span>
        <span className="block text-[12.5px] text-muted">{subtitle}</span>
      </span>
      <input type="checkbox" role="switch" checked={checked} disabled={disabled} onChange={(e) => onChange(e.target.checked)} className="peer sr-only" />
      <span aria-hidden className="relative h-7 w-12 shrink-0 rounded-full bg-lilac transition-colors duration-300 peer-checked:bg-primary peer-focus-visible:ring-2 peer-focus-visible:ring-accent peer-focus-visible:ring-offset-2 peer-focus-visible:ring-offset-surface after:absolute after:left-1 after:top-1 after:h-5 after:w-5 after:rounded-full after:bg-white after:shadow after:transition-transform after:duration-500 after:ease-[var(--spring-bouncy)] peer-checked:after:translate-x-5" />
    </label>
  )
}

/** Mini screen preview on each theme card. */
function Preview({ kind }: { kind: ThemePref }) {
  const light = (
    <div className="absolute inset-0 bg-gradient-to-b from-[#d9ceff] via-[#f7f4ff] to-white p-2">
      <div className="h-2 w-10 rounded-full bg-[#1e1440]/80" />
      <div className="mt-1.5 h-6 rounded-[8px] border border-white bg-white/80" />
      <div className="mt-1.5 flex gap-1"><div className="h-3 flex-1 rounded-[5px] bg-white" /><div className="h-3 flex-1 rounded-[5px] bg-white" /></div>
      <div className="absolute inset-x-2 bottom-1.5 h-2.5 rounded-full bg-white/90"><div className="h-full w-5 rounded-full bg-primary" /></div>
    </div>
  )
  const dark = (
    <div className="absolute inset-0 bg-gradient-to-b from-[#16121f] via-black to-black p-2">
      <div className="h-2 w-10 rounded-full bg-[#f4f2fa]/85" />
      <div className="mt-1.5 h-6 rounded-[8px] border border-white/10 bg-white/[0.06]" />
      <div className="mt-1.5 flex gap-1"><div className="h-3 flex-1 rounded-[5px] bg-[#131218]" /><div className="h-3 flex-1 rounded-[5px] bg-[#131218]" /></div>
      <div className="absolute inset-x-2 bottom-1.5 h-2.5 rounded-full bg-[#121116]"><div className="h-full w-5 rounded-full bg-primary" /></div>
    </div>
  )
  if (kind === 'light') return light
  if (kind === 'dark') return dark
  return (
    <>
      {light}
      <div className="absolute inset-0 [clip-path:polygon(100%_0,100%_100%,0_100%)]">{dark}</div>
    </>
  )
}

const THEMES: { value: ThemePref; label: string; icon: LucideIcon }[] = [
  { value: 'system', label: 'System', icon: SunMoon },
  { value: 'light', label: 'Light', icon: Sun },
  { value: 'dark', label: 'Dark', icon: Moon },
]

export default function SettingsPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const { user, signOut } = useAuth()
  const { pref, setPref } = useTheme()
  const [view, setView] = usePref('nudge:files-view', 'list', ['list', 'grid'] as const)
  const [alerts, setAlerts] = usePref(PREF_ALERTS, 'on', ['on', 'off'] as const)
  const [browser, setBrowser] = usePref(PREF_BROWSER, 'off', ['on', 'off'] as const)
  const supported = 'Notification' in window
  const [permission, setPermission] = useState(() => (supported ? Notification.permission : 'denied'))

  async function toggleBrowser(on: boolean) {
    if (!on) return setBrowser('off')
    const p = permission === 'granted' ? 'granted' : await Notification.requestPermission()
    setPermission(p)
    if (p === 'granted') {
      setBrowser('on')
      toast.show('Browser notifications are on', { tone: 'success' })
    } else toast.error('Notifications are blocked for this site in your browser settings.')
  }

  return (
    <div className="mx-auto grid max-w-2xl grid-cols-1 gap-6">
      <PageHeader title="Settings" back="/profile" className="!pb-0" />

      <Group title="Appearance">
        <div role="radiogroup" aria-label="Theme" className="grid grid-cols-3 gap-2.5 p-1.5">
          {THEMES.map((t) => {
            const on = pref === t.value
            return (
              <button
                key={t.value}
                type="button"
                role="radio"
                aria-checked={on}
                onClick={(e) => setPref(t.value, e)}
                className={clsx('press press-cell group flex flex-col rounded-[20px] border-2 p-2 text-left', on ? 'border-primary bg-mist/60' : 'border-card-border hover:border-lilac')}
              >
                <span className="relative block h-[76px] w-full overflow-hidden rounded-[14px] border border-black/5 dark:border-white/10"><Preview kind={t.value} /></span>
                <span className="mt-2 flex items-center gap-1.5 px-1 pb-0.5 text-[13.5px] font-semibold text-ink">
                  <t.icon size={15} aria-hidden className={on ? 'text-link' : 'text-muted'} />
                  <span className="flex-1">{t.label}</span>
                  {on && <span className="pop-in flex h-5 w-5 items-center justify-center rounded-full bg-primary text-white"><Check size={13} strokeWidth={3} aria-hidden /></span>}
                </span>
              </button>
            )
          })}
        </div>
      </Group>

      <Group title="Notifications">
        <ToggleRow
          icon={Bell}
          title="Browser notifications"
          subtitle={!supported ? 'Not supported in this browser' : permission === 'denied' ? 'Blocked in your browser settings' : 'Uploads, shares and alerts while this tab is in the background'}
          checked={browser === 'on' && permission === 'granted'}
          disabled={!supported || permission === 'denied'}
          onChange={(v) => void toggleBrowser(v)}
        />
        <ToggleRow icon={TriangleAlert} title="Storage alerts" subtitle="Warn me when a drive is almost full" checked={alerts === 'on'} onChange={(v) => setAlerts(v ? 'on' : 'off')} />
        <ListRow icon={Bell} title="Open notifications" to="/notifications" />
      </Group>

      <Group title="Files">
        <ToggleRow icon={LayoutGrid} title="Grid view by default" subtitle="Show files as thumbnails instead of a list" checked={view === 'grid'} onChange={(v) => setView(v ? 'grid' : 'list')} />
        <ListRow icon={Trash2} title="Trash" to="/trash" />
      </Group>

      <Group title="Account">
        <div className="flex flex-wrap items-center gap-4 p-3">
          <span className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-[#cdbfff] to-iris font-display text-[22px] font-bold text-white">{(user?.name ?? 'N').charAt(0).toUpperCase()}</span>
          <div className="min-w-0 flex-1">
            <p className="truncate text-[17px] font-semibold text-ink">{user?.name}</p>
            <p className="flex flex-wrap items-center gap-2 text-[13.5px] text-muted">
              <span className="truncate">{user?.email}</span>
              {user?.emailVerified !== false && <Pill tone="success"><BadgeCheck size={13} aria-hidden />Verified</Pill>}
            </p>
          </div>
          <Button variant="secondary" icon={LogOut} onClick={() => void signOut().then(() => navigate('/login'))}>Sign out</Button>
        </div>
      </Group>

      <AdminSection />

      <Panel className="p-2">
        <ListRow icon={Info} title="About Nudge Drive" subtitle={`Version ${__APP_VERSION__}`} to="/about" />
      </Panel>
    </div>
  )
}
