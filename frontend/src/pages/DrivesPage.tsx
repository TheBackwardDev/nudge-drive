import { useCallback, useEffect, useRef, useState, type CSSProperties, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import clsx from 'clsx'
import { ArrowDown, ArrowUp, CloudDownload, Database, EllipsisVertical, HardDrive, Plus, RefreshCw, Sparkles, TriangleAlert, Unplug } from 'lucide-react'
import { DriveGlyph } from '@/components/brand/Brand'
import { Button, IconButton } from '@/components/ui/Button'
import { ConfirmDialog, Dialog } from '@/components/ui/Dialog'
import { Checkbox, PasswordField, TextField } from '@/components/ui/Field'
import { Menu, useMenu } from '@/components/ui/Menu'
import { EmptyState, GlassCard, Panel, Pill, SectionTitle, Shimmer, Switch, UsageRing } from '@/components/ui/Surface'
import { PageHeader } from '@/components/layout/PageHeader'
import { useToast } from '@/context/ToastContext'
import { postNotice } from '@/lib/notices'
import { api, errorMessage } from '@/lib/api'
import { useConnectGoogle } from '@/lib/connectGoogle'
import { providerLabel } from '@/lib/files'
import { formatBytes, plural, toNumber } from '@/lib/format'
import { emitChange, useLoad, useOnChange } from '@/lib/hooks'
import type { ConnectedAccount, ImportStatus, RoutingMode, RoutingPolicy } from '@/lib/types'

const MODES: { value: RoutingMode; title: string; body: string }[] = [
  { value: 'most_available', title: 'Most free space', body: 'Each upload goes to the drive with the most room left.' },
  { value: 'round_robin', title: 'Take turns', body: 'Uploads rotate across your drives one by one.' },
  { value: 'priority', title: 'My order', body: 'Fill drives in the order you choose.' },
]

async function loadDrives() {
  const [summary, accounts, routing] = await Promise.all([api.storage.summary(), api.accounts.list(), api.storage.routing()])
  return { summary, accounts: accounts.accounts, policy: routing.policy }
}

const fraction = (a: ConnectedAccount) => {
  const t = a.storageAccount?.totalBytes
  return t ? toNumber(a.storageAccount?.usedBytes) / Math.max(1, toNumber(t)) : 0
}

export default function DrivesPage() {
  const toast = useToast()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const { data, loading, error, reload, setData } = useLoad(loadDrives)
  useOnChange(['accounts', 'storage'], reload)
  const [s3Open, setS3Open] = useState(false)
  const [disconnecting, setDisconnecting] = useState<ConnectedAccount | null>(null)
  const [refreshing, setRefreshing] = useState<string | null>(null)
  const [imports, setImports] = useState<Record<string, ImportStatus>>({})
  const [autoRefresh, setAutoRefresh] = useState(false)
  const menu = useMenu()
  const [menuFor, setMenuFor] = useState<ConnectedAccount | null>(null)
  const google = useConnectGoogle((ok, err) => {
    if (!ok) return toast.error(err ?? 'Google Drive was not connected.')
    toast.show('Google Drive connected', { tone: 'success' })
    postNotice({ type: 'drive', title: 'Google Drive connected', body: 'Its free space is now part of your storage pool.', to: '/drives' })
  })

  // Coming back from a full-page Google redirect (pop-ups blocked).
  useEffect(() => {
    const c = params.get('connected')
    if (!c) return
    if (c === 'success') toast.show('Google Drive connected', { tone: 'success' })
    else toast.error('Google Drive was not connected.')
    setParams({}, { replace: true })
  }, [params, setParams, toast])

  useEffect(() => {
    if (!autoRefresh) return
    const t = window.setInterval(() => void reload(), 35_000)
    return () => window.clearInterval(t)
  }, [autoRefresh, reload])

  // Poll running imports.
  const running = Object.entries(imports).filter(([, s]) => s.state === 'running').map(([id]) => id)
  const runningKey = running.join(',')
  const doneRef = useRef(toast)
  doneRef.current = toast
  const accountsRef = useRef<ConnectedAccount[]>([])
  accountsRef.current = data?.accounts ?? []
  /** Imports already announced as finished, so each one posts its notice once. */
  const notified = useRef(new Set<string>())

  /** A late "running" never brings back an import already seen finishing; only startImport starts one again. */
  const mergeImport = useCallback((id: string, s: ImportStatus) => {
    setImports((m) => (s.state === 'running' && (m[id]?.state === 'done' || m[id]?.state === 'failed') ? m : { ...m, [id]: s }))
  }, [])

  useEffect(() => {
    if (!runningKey) return
    let stopped = false
    let timer = 0
    const tick = async () => {
      for (const id of runningKey.split(',')) {
        try {
          const s = await api.accounts.importStatus(id)
          if (stopped) return
          mergeImport(id, s)
          if (s.state === 'running' || s.state === 'idle' || notified.current.has(id)) continue
          notified.current.add(id)
          if (s.state === 'done') {
            const acc = accountsRef.current.find((a) => a.id === id)
            if (s.created + s.updated + s.folders === 0) {
              // Nothing in Drive itself: a full account like this is full of Gmail / Google Photos.
              postNotice({ type: 'drive', title: `${acc?.email ?? 'This drive'} has no other Drive files`, body: 'Its space is used by Google Photos and Gmail. Open Free up space to copy your photos out.', to: `/drives/${id}/free-up` })
              doneRef.current.show('Nothing else on this Drive. Its space is Google Photos and Gmail.')
            } else {
              postNotice({ type: 'drive', title: `Imported ${acc?.email ?? 'your drive'}`, body: `${plural(s.created, 'file')} and ${plural(s.folders, 'folder')} are now in My Files.`, to: '/files' })
              doneRef.current.show(`Imported ${plural(s.created, 'file')} and ${plural(s.folders, 'folder')}`, { tone: 'success' })
            }
            emitChange('files', 'folders', 'storage')
          } else doneRef.current.error(s.error ?? 'Import failed')
        } catch {
          /* try again next tick */
        }
      }
      if (!stopped) timer = window.setTimeout(tick, 2500)
    }
    timer = window.setTimeout(tick, 2500)
    return () => {
      stopped = true
      window.clearTimeout(timer)
    }
  }, [runningKey, mergeImport])

  // Pick up imports already running (started from the app or the welcome screen).
  const accountIds = data?.accounts.filter((a) => a.provider === 'google_drive').map((a) => a.id).join(',') ?? ''
  useEffect(() => {
    if (!accountIds) return
    for (const id of accountIds.split(',')) {
      api.accounts.importStatus(id).then((s) => mergeImport(id, s), () => undefined)
    }
  }, [accountIds, mergeImport])

  async function refreshAccount(a: ConnectedAccount) {
    setRefreshing(a.id)
    try {
      await api.accounts.syncQuota(a.id)
      emitChange('storage', 'accounts')
      toast.show(`Storage updated for ${a.displayName || a.email}`)
    } catch (e) {
      toast.error(errorMessage(e))
    } finally {
      setRefreshing(null)
    }
  }

  async function refreshAll() {
    if (!data) return
    setRefreshing('all')
    try {
      await Promise.all(data.accounts.map((a) => api.accounts.syncQuota(a.id).catch(() => undefined)))
      await reload()
      emitChange('storage')
    } finally {
      setRefreshing(null)
    }
  }

  async function startImport(a: ConnectedAccount) {
    try {
      const s = await api.accounts.importDrive(a.id)
      // A new import: it may run (and be announced) again.
      notified.current.delete(a.id)
      setImports((m) => ({ ...m, [a.id]: s }))
      toast.show(`Importing everything from ${a.email}…`)
    } catch (e) {
      toast.error(errorMessage(e))
    }
  }

  const savePolicy = useCallback(async (mode: RoutingMode, order: string[]) => {
    setData((d) => (d ? { ...d, policy: { ...d.policy, mode, priorityAccountIds: order } } : d))
    try {
      const { policy } = await api.storage.setRouting(mode, order)
      setData((d) => (d ? { ...d, policy } : d))
    } catch (e) {
      toast.error(errorMessage(e))
      void reload()
    }
  }, [setData, toast, reload])

  const accounts = data?.accounts ?? []
  const total = toNumber(data?.summary.totalBytes)
  const used = toNumber(data?.summary.usedBytes)

  return (
    <div className="grid grid-cols-1 gap-6">
      <PageHeader
        title="Drives"
        subtitle="Your storage pool and upload rules"
        actions={
          <>
            <IconButton icon={RefreshCw} label="Refresh storage numbers" onClick={refreshAll} disabled={refreshing !== null || !accounts.length} className={refreshing === 'all' ? '[&_svg]:animate-spin' : ''} />
            <Button icon={Plus} onClick={() => void google.connect()} loading={google.busy} className="max-sm:hidden">Connect Google Drive</Button>
          </>
        }
        className="!pb-0"
      />

      {/* Pool */}
      <GlassCard strong className="flex flex-wrap items-center gap-5 p-5">
        {loading && !data ? (
          <Shimmer className="h-[96px] w-full" />
        ) : (
          <>
            <UsageRing fraction={total ? used / total : 0} size={96} stroke={10} />
            <div className="min-w-[160px] flex-1">
              <p className="text-[13px] font-semibold text-muted">Storage pool · {plural(accounts.length, 'drive')}</p>
              <p className="mt-1 font-display text-[30px] font-bold leading-tight tracking-[-0.04em] text-ink">{formatBytes(data?.summary.availableBytes)} <span className="text-[16px] font-semibold text-muted">free</span></p>
              <p className="text-[13px] text-muted">{formatBytes(used)} of {formatBytes(total)} used</p>
              {data && <Pill className="mt-2.5">Uploads: {MODES.find((m) => m.value === data.policy.mode)?.title}</Pill>}
            </div>
          </>
        )}
      </GlassCard>

      {/* Connect */}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <button type="button" onClick={() => void google.connect()} disabled={google.busy} className="glass press lift flex items-center gap-4 rounded-[24px] p-4 text-left shadow-soft disabled:opacity-60">
          <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-[16px] bg-surface"><DriveGlyph size={24} /></span>
          <span className="min-w-0 flex-1">
            <span className="block text-[15px] font-semibold text-ink">{google.busy ? 'Waiting for Google…' : 'Connect Google Drive'}</span>
            <span className="block text-[13px] text-muted">Every account adds 15 GB of free space</span>
          </span>
          <Plus size={20} className="text-link" aria-hidden />
        </button>
        <button type="button" onClick={() => setS3Open(true)} className="glass press lift flex items-center gap-4 rounded-[24px] p-4 text-left shadow-soft">
          <span className="soft-tile flex h-12 w-12 shrink-0 items-center justify-center rounded-[16px]" style={{ '--k': '#8b72f5' } as CSSProperties}><Database size={22} aria-hidden /></span>
          <span className="min-w-0 flex-1">
            <span className="block text-[15px] font-semibold text-ink">Add S3 bucket</span>
            <span className="block text-[13px] text-muted">AWS, Cloudflare R2, Backblaze B2, MinIO, Wasabi…</span>
          </span>
          <Plus size={20} className="text-link" aria-hidden />
        </button>
      </div>

      {/* Accounts */}
      <section aria-labelledby="accounts-h" className="grid grid-cols-1 gap-3">
        <SectionTitle
          title={
            <span id="accounts-h" className="flex flex-wrap items-center gap-2.5">
              Your accounts
              {accounts.length > 0 && <span className="rounded-full bg-inverse px-2.5 py-1 font-sans text-[11.5px] font-semibold tracking-normal text-on-inverse">{accounts.length} connected · {formatBytes(total)}</span>}
            </span>
          }
        />
        {error && !data && <p role="alert" className="rounded-[18px] bg-danger-soft p-4 text-[14px] text-danger">{error}</p>}
        {data && accounts.length === 0 && <EmptyState icon={HardDrive} title="No drives yet" body="Connect a Google account to start your storage pool." action={<Button icon={Plus} onClick={() => void google.connect()}>Connect Google Drive</Button>} />}
        <ul className="stagger grid grid-cols-1 gap-3 lg:grid-cols-2">
          {accounts.map((a) => {
            const t = a.storageAccount?.totalBytes ?? null
            const imp = imports[a.id]
            const attention = Boolean(a.storageAccount?.lastError)
            const f = fraction(a)
            return (
              <li key={a.id}>
                <Panel className="flex items-center gap-4 p-4">
                  <span className="relative">
                    <UsageRing fraction={f} size={56} stroke={6} label={t === null ? '∞' : undefined} />
                    <span className="absolute -bottom-0.5 -right-0.5 flex h-6 w-6 items-center justify-center rounded-full bg-surface shadow-soft">
                      {a.provider === 's3' ? <Database size={13} className="text-accent" aria-hidden /> : <DriveGlyph size={13} />}
                    </span>
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-[15px] font-semibold text-ink">{a.displayName || a.email}</p>
                    <p className="truncate text-[12.5px] text-muted">{a.displayName ? `${a.email} · ` : ''}{providerLabel(a.provider)}</p>
                    <p className={clsx('mt-0.5 text-[12.5px]', attention ? 'text-warn' : 'text-muted')}>
                      {refreshing === a.id
                        ? 'Refreshing…'
                        : imp?.state === 'running'
                          ? `Importing… ${plural(imp.folders, 'folder')}, ${plural(imp.created, 'file')} so far`
                          : t === null
                            ? `${formatBytes(a.storageAccount?.usedBytes)} used · no size limit`
                            : `${formatBytes(a.storageAccount?.usedBytes)} of ${formatBytes(t)}`}
                      {attention && ' · needs attention'}
                    </p>
                  </div>
                  {/* A nearly full Google Drive gets a one-click way out. */}
                  {a.provider === 'google_drive' && f >= 0.85 && (
                    <Link
                      to={`/drives/${a.id}/free-up`}
                      className={clsx('press shrink-0 rounded-[12px] px-2.5 py-1.5 text-[12.5px] font-semibold', f >= 0.95 ? 'bg-danger-soft text-danger' : 'bg-warn-soft text-warn')}
                      aria-label={`Free up space on ${a.email}`}
                    >
                      Free up
                    </Link>
                  )}
                  <IconButton
                    icon={EllipsisVertical}
                    label={`Options for ${a.email}`}
                    tone="plain"
                    aria-haspopup="menu"
                    onClick={(e) => {
                      setMenuFor(a)
                      menu.openAt(e.currentTarget)
                    }}
                  />
                </Panel>
              </li>
            )
          })}
        </ul>
        {accounts.length > 0 && <Switch checked={autoRefresh} onChange={setAutoRefresh} label="Auto-refresh" description="Update these numbers every 35 seconds while this page is open" />}
      </section>
      <Menu
        open={menu.open}
        anchor={menu.anchor}
        onClose={menu.close}
        title={menuFor?.email ?? 'Drive'}
        items={menuFor ? [
          menuFor.provider === 'google_drive' && { label: 'Free up space', icon: Sparkles, onSelect: () => navigate(`/drives/${menuFor.id}/free-up`) },
          menuFor.provider === 'google_drive' && { label: 'Import everything from this Drive', icon: CloudDownload, disabled: imports[menuFor.id]?.state === 'running', onSelect: () => void startImport(menuFor) },
          { label: 'Refresh storage numbers', icon: RefreshCw, onSelect: () => void refreshAccount(menuFor) },
          'divider',
          { label: 'Disconnect', icon: Unplug, danger: true, onSelect: () => setDisconnecting(menuFor) },
        ] : []}
      />

      {/* Upload rules */}
      {data && accounts.length > 0 && <UploadRules accounts={accounts} policy={data.policy} onSave={savePolicy} />}

      <S3Dialog open={s3Open} onClose={() => setS3Open(false)} />
      <ConfirmDialog
        open={!!disconnecting}
        onClose={() => setDisconnecting(null)}
        title={`Disconnect ${disconnecting?.email ?? ''}?`}
        body={<>New uploads won't go to this drive any more, and its files stay listed but can't be opened. Nothing is deleted from the drive itself. You can connect it again later.</>}
        confirmLabel="Disconnect"
        danger
        onConfirm={async () => {
          if (!disconnecting) return
          try {
            await api.accounts.disconnect(disconnecting.id)
            emitChange('accounts', 'storage', 'files')
            toast.show(`Disconnected ${disconnecting.email}`)
          } catch (e) {
            toast.error(errorMessage(e))
            throw e
          }
        }}
      />
      <p className="flex items-center gap-2 text-[12.5px] text-subtle"><TriangleAlert size={14} aria-hidden />Tip: deleting an imported folder in Nudge Drive also deletes it from Google Drive.</p>
    </div>
  )
}

function UploadRules({ accounts, policy, onSave }: { accounts: ConnectedAccount[]; policy: RoutingPolicy; onSave: (mode: RoutingMode, order: string[]) => void }) {
  const byId = new Map(accounts.map((a) => [a.id, a]))
  const ordered = [...policy.priorityAccountIds.map((id) => byId.get(id)).filter((a): a is ConnectedAccount => !!a), ...accounts.filter((a) => !policy.priorityAccountIds.includes(a.id))]
  const ids = ordered.map((a) => a.id)
  function move(i: number, dir: -1 | 1) {
    const next = [...ids]
    const [x] = next.splice(i, 1)
    next.splice(i + dir, 0, x)
    onSave(policy.mode, next)
  }
  return (
    <section aria-labelledby="rules-h" className="grid grid-cols-1 gap-3">
      <SectionTitle title={<span id="rules-h">Where should uploads go?</span>} />
      <div role="radiogroup" aria-labelledby="rules-h" className="grid grid-cols-1 gap-2.5 sm:grid-cols-3">
        {MODES.map((m) => (
          <button
            key={m.value}
            type="button"
            role="radio"
            aria-checked={policy.mode === m.value}
            onClick={() => onSave(m.value, ids)}
            className={clsx('press rounded-[22px] border p-4 text-left', policy.mode === m.value ? 'border-iris bg-mist' : 'glass hover:bg-glass-strong')}
          >
            <span className="flex items-center gap-2">
              <span className={clsx('flex h-5 w-5 items-center justify-center rounded-full border-2', policy.mode === m.value ? 'border-iris' : 'border-subtle')} aria-hidden>
                {policy.mode === m.value && <span className="h-2.5 w-2.5 rounded-full bg-iris" />}
              </span>
              <span className="text-[15px] font-semibold text-ink">{m.title}</span>
            </span>
            <span className="mt-1 block pl-7 text-[13px] text-muted">{m.body}</span>
          </button>
        ))}
      </div>
      {policy.mode === 'priority' && (
        <Panel className="p-2">
          <p className="px-3 pb-1 pt-2 text-[13px] font-semibold text-muted">Fill in this order</p>
          <ol className="grid grid-cols-1 gap-1">
            {ordered.map((a, i) => (
              <li key={a.id} className="flex items-center gap-3 rounded-[16px] px-3 py-2 hover:bg-mist">
                <span className="flex h-7 w-7 items-center justify-center rounded-full bg-primary text-[13px] font-bold text-white">{i + 1}</span>
                <span className="min-w-0 flex-1 truncate text-[14px] font-semibold text-ink">{a.displayName || a.email}</span>
                <IconButton icon={ArrowUp} label={`Move ${a.email} up`} tone="plain" size={38} disabled={i === 0} onClick={() => move(i, -1)} />
                <IconButton icon={ArrowDown} label={`Move ${a.email} down`} tone="plain" size={38} disabled={i === ordered.length - 1} onClick={() => move(i, 1)} />
              </li>
            ))}
          </ol>
        </Panel>
      )}
    </section>
  )
}

const EMPTY_S3 = { name: '', bucket: '', region: 'us-east-1', endpoint: '', accessKeyId: '', secretAccessKey: '', quotaGb: '', forcePathStyle: false }

function S3Dialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const toast = useToast()
  const [f, setF] = useState(EMPTY_S3)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const set = (k: keyof typeof EMPTY_S3) => (e: { target: { value: string } }) => setF((s) => ({ ...s, [k]: e.target.value }))

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      const gb = Number(f.quotaGb)
      await api.accounts.connectS3({
        name: f.name.trim(),
        bucket: f.bucket.trim(),
        region: f.region.trim(),
        endpoint: f.endpoint.trim() || undefined,
        accessKeyId: f.accessKeyId.trim(),
        secretAccessKey: f.secretAccessKey,
        forcePathStyle: f.forcePathStyle,
        quotaBytes: f.quotaGb && gb > 0 ? String(Math.round(gb * 1024 ** 3)) : null,
      })
      emitChange('accounts', 'storage')
      toast.show('Bucket connected', { tone: 'success' })
      setF(EMPTY_S3)
      onClose()
    } catch (err) {
      setError(errorMessage(err, "Couldn't connect to that bucket. Check the details."))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onClose={onClose} title="Add an S3 bucket" description="Keys are encrypted on your server. Leave the endpoint empty for AWS.">
      <form onSubmit={submit} className="grid grid-cols-1 gap-3">
        <TextField label="Display name" showLabel placeholder="e.g. R2 backups" value={f.name} onChange={set('name')} required />
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <TextField label="Bucket name" showLabel value={f.bucket} onChange={set('bucket')} required autoCapitalize="off" spellCheck={false} />
          <TextField label="Region" showLabel placeholder="us-east-1, auto" value={f.region} onChange={set('region')} required autoCapitalize="off" spellCheck={false} />
        </div>
        <TextField label="Endpoint URL" showLabel type="url" placeholder="Empty for AWS" value={f.endpoint} onChange={set('endpoint')} autoCapitalize="off" spellCheck={false} />
        <TextField label="Access key ID" showLabel value={f.accessKeyId} onChange={set('accessKeyId')} required autoComplete="off" autoCapitalize="off" spellCheck={false} />
        <PasswordField label="Secret access key" showLabel value={f.secretAccessKey} onChange={set('secretAccessKey')} required autoComplete="off" />
        <TextField label="Size limit in GB (optional)" showLabel type="number" min={0} step="any" inputMode="decimal" value={f.quotaGb} onChange={set('quotaGb')} hint="Leave empty for no limit." />
        <Checkbox checked={f.forcePathStyle} onChange={(v) => setF((s) => ({ ...s, forcePathStyle: v }))} label="Path-style URLs (MinIO, most S3-compatible services)" className="pl-1" />
        {error && <p role="alert" className="rounded-[16px] bg-danger-soft px-4 py-3 text-[13.5px] text-danger">{error}</p>}
        <div className="flex justify-end gap-2.5 pt-1">
          <Button variant="secondary" onClick={onClose} disabled={busy}>Cancel</Button>
          <Button type="submit" loading={busy}>Test & connect</Button>
        </div>
      </form>
    </Dialog>
  )
}
