import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Bell, History, Info, KeyRound, Layers, LogOut, RefreshCw, Settings, Share2, ShieldCheck, Trash2, UserX } from 'lucide-react'
import { ConfirmDialog } from '@/components/ui/Dialog'
import { GlassCard, ListRow, Panel, Shimmer, StripedBar } from '@/components/ui/Surface'
import { PageHeader } from '@/components/layout/PageHeader'
import { ThemeToggle } from '@/components/layout/Chrome'
import { AppOnlyDialog } from '@/components/layout/AppOnlyDialog'
import { useAuth } from '@/context/AuthContext'
import { useToast } from '@/context/ToastContext'
import { api, errorMessage } from '@/lib/api'
import { emitChange, useLoad, useOnChange } from '@/lib/hooks'
import { formatBytes, plural, toNumber } from '@/lib/format'
import { useUnreadCount } from '@/lib/notices'

/** The Profile tab, laid out like the app's ProfileScreen: account card, then three menu groups. */
export default function ProfilePage() {
  const navigate = useNavigate()
  const toast = useToast()
  const { user, signOut } = useAuth()
  const unread = useUnreadCount()
  const [confirmOut, setConfirmOut] = useState(false)
  const [appInfo, setAppInfo] = useState(false)
  const [syncing, setSyncing] = useState(false)
  const { data, reload } = useLoad(() => Promise.all([api.storage.summary(), api.accounts.list()]))
  useOnChange(['storage', 'accounts'], reload)
  const [summary, accounts] = data ?? [null, null]
  const total = toNumber(summary?.totalBytes)

  async function sync() {
    if (syncing) return
    setSyncing(true)
    try {
      const { results } = await api.files.syncGoogle()
      emitChange('files', 'folders', 'storage')
      const n = results.reduce((s, r) => s + r.created + r.updated + r.deleted, 0)
      toast.show(n ? `Synced ${plural(n, 'change')}` : 'Everything is already in sync', { tone: 'success' })
    } catch (e) {
      toast.error(errorMessage(e, 'Sync failed'))
    } finally {
      setSyncing(false)
    }
  }

  return (
    <div className="mx-auto grid max-w-2xl grid-cols-1 gap-5">
      <PageHeader title="Profile" actions={<ThemeToggle />} className="!pb-0" />

      <GlassCard strong className="rise flex items-center gap-4 p-5">
        <span className="flex h-16 w-16 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-[#cdbfff] to-iris font-display text-[26px] font-bold text-white shadow-[0_12px_24px_-12px_rgb(112_89_240/0.8)]">
          {(user?.name ?? 'N').charAt(0).toUpperCase()}
        </span>
        <div className="min-w-0 flex-1">
          <p className="truncate font-display text-[20px] font-semibold text-ink">{user?.name || '—'}</p>
          <p className="truncate text-[13.5px] text-muted">{user?.email}</p>
          <div className="mt-2.5">
            {summary ? (
              <>
                <StripedBar fraction={total > 0 ? toNumber(summary.usedBytes) / total : 0} height={10} label="Storage used" />
                <p className="mt-1.5 text-[12px] text-muted">{formatBytes(summary.usedBytes)} used of {formatBytes(total)} across {plural(accounts?.accounts.length ?? 0, 'drive')}</p>
              </>
            ) : (
              <><Shimmer className="h-2.5 rounded-full" /><Shimmer className="mt-2 h-3 w-2/3 rounded-full" /></>
            )}
          </div>
        </div>
      </GlassCard>

      <Panel className="stagger p-2">
        <ListRow icon={Bell} title="Notifications" subtitle={unread ? `${plural(unread, 'unread notice')}` : undefined} badge={unread} to="/notifications" />
        <ListRow icon={Settings} title="Settings" subtitle="Theme, notifications, default view" to="/settings" />
        <ListRow icon={Layers} title="Drives & upload rules" to="/drives" />
        <ListRow icon={ShieldCheck} title="Auto backup" subtitle="Back up phone folders automatically" onClick={() => setAppInfo(true)} trailing={<span className="rounded-full bg-mist px-2 py-0.5 text-[11px] font-semibold text-link">App</span>} />
        <ListRow icon={Share2} title="Shared links & invites" to="/shared" />
        <ListRow icon={Trash2} title="Trash" to="/trash" />
        <ListRow icon={History} title="Activity" to="/activity" />
      </Panel>

      <Panel className="stagger p-2">
        <ListRow icon={RefreshCw} title={syncing ? 'Syncing…' : 'Sync from Google Drive'} subtitle="Pick up files added on drive.google.com" onClick={() => void sync()} />
        <ListRow icon={KeyRound} title="API keys" subtitle="Upload from scripts and other apps" to="/api-keys" />
        <ListRow icon={Info} title="About Nudge Drive" to="/about" />
      </Panel>

      <Panel className="stagger p-2">
        <ListRow icon={LogOut} title="Sign out" onClick={() => setConfirmOut(true)} danger />
        <ListRow icon={UserX} title="Delete account" subtitle="Permanently remove your account and data" to="/delete-account" danger />
      </Panel>

      <ConfirmDialog
        open={confirmOut}
        onClose={() => setConfirmOut(false)}
        title="Sign out?"
        body="You'll need your email and password, or Google, to sign back in."
        confirmLabel="Sign out"
        danger
        onConfirm={async () => {
          await signOut()
          navigate('/login')
        }}
      />
      <AppOnlyDialog open={appInfo} onClose={() => setAppInfo(false)} />
    </div>
  )
}
