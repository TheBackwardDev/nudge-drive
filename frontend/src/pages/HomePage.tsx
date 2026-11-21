import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import clsx from 'clsx'
import { ArrowRight, Circle, CircleCheckBig, ChevronRight, CloudOff, CloudUpload, Database, FolderPlus, HardDrive, HardDriveUpload, Image, ImagePlus, Link2, Radar, RefreshCw, Search, ShieldCheck, Sparkles, TriangleAlert, Users, X } from 'lucide-react'
import { DriveGlyph } from '@/components/brand/Brand'
import { IconButton } from '@/components/ui/Button'
import { GlassCard, Notice, SectionTitle, Shimmer, StripedBar, UsageRing } from '@/components/ui/Surface'
import { FileThumb, FolderCard } from '@/components/files/FileVisuals'
import { useAuth } from '@/context/AuthContext'
import { useFileActions } from '@/context/FileActions'
import { AppOnlyDialog } from '@/components/layout/AppOnlyDialog'
import { BellButton, ThemeToggle } from '@/components/layout/Chrome'
import { api } from '@/lib/api'
import { sortFolders } from '@/lib/files'
import { formatBytes, friendlyDate, greeting, plural, toNumber } from '@/lib/format'
import { useDocumentTitle, useLoad, useOnChange, usePref } from '@/lib/hooks'
import type { ConnectedAccount, FileItem } from '@/lib/types'

async function loadHome() {
  const [summary, breakdown, accounts, files, shares, invites] = await Promise.all([
    api.storage.summary(),
    api.storage.breakdown(),
    api.accounts.list(),
    api.files.list(),
    api.files.sharedLinks().catch(() => ({ shares: [] })),
    api.invites.list().catch(() => ({ sent: [], received: [] })),
  ])
  return { summary, breakdown, accounts: accounts.accounts, files: files.files, sharedCount: shares.shares.length, received: invites.received }
}

const usedFraction = (a: ConnectedAccount) => {
  const t = a.storageAccount?.totalBytes
  return t ? toNumber(a.storageAccount?.usedBytes) / Math.max(1, toNumber(t)) : 0
}

export default function HomePage() {
  useDocumentTitle('Home')
  const navigate = useNavigate()
  const { user } = useAuth()
  const actions = useFileActions()
  const [appInfo, setAppInfo] = useState(false)
  const [promo, setPromo] = usePref(`nudge:app-card:${user?.id ?? ''}`, 'shown', ['shown', 'hidden'] as const)
  const { data, error, loading, reload } = useLoad(loadHome)
  useOnChange(['files', 'storage', 'accounts', 'shares', 'folders'], reload)
  const [dismissed, setDismissed] = usePref(`nudge:checklist:${user?.id ?? ''}`, 'shown', ['shown', 'hidden'] as const)
  const [query, setQuery] = useState('')

  const recent = data?.files.slice(0, 12) ?? []
  const folders = sortFolders(actions.folders, 'newest').slice(0, 8)
  const alerts = (data?.accounts ?? []).filter((a) => a.storageAccount?.lastError || usedFraction(a) > 0.92)

  function search(e: FormEvent) {
    e.preventDefault()
    if (query.trim()) navigate(`/files?q=${encodeURIComponent(query.trim())}`)
  }

  const steps = data
    ? [
        { label: 'Connect a Google Drive', done: data.accounts.length > 0, go: () => navigate('/drives') },
        { label: 'Add a second drive for more space', done: data.accounts.length >= 2, go: () => navigate('/drives') },
        { label: 'Upload your first file', done: data.files.length > 0, go: () => actions.upload(null) },
        { label: 'Organise with a folder', done: actions.folders.length > 0, go: () => actions.newFolder(null) },
        { label: 'Share a file with a link', done: data.sharedCount > 0, go: () => navigate('/files') },
      ]
    : []
  const showChecklist = data && dismissed === 'shown' && steps.some((s) => !s.done)

  return (
    <div className="grid grid-cols-1 gap-6 pb-4">
      {/* Greeting */}
      <div className="rise flex items-center gap-3 pt-2.5 lg:pt-4">
        <Link to="/profile" aria-label="Profile" className="press flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-[#cdbfff] to-iris font-display text-[18px] font-bold text-white shadow-[0_12px_24px_-12px_rgb(112_89_240/0.8)]">
          {(user?.name ?? 'N').charAt(0).toUpperCase()}
        </Link>
        <div className="min-w-0 flex-1">
          <p className="text-[13px] font-semibold text-muted">{greeting()}</p>
          <h1 className="truncate text-[22px] font-semibold tracking-[-0.02em] text-ink">{user?.name?.split(' ')[0] ?? 'there'}</h1>
        </div>
        <div className="flex items-center gap-2 lg:hidden">
          <ThemeToggle />
          <BellButton />
        </div>
      </div>

      <form role="search" onSubmit={search} className="rise glass flex h-[54px] items-center rounded-[28px] pl-4 pr-1.5 lg:hidden">
        <Search size={19} className="text-muted" aria-hidden />
        <label htmlFor="home-search" className="sr-only">Search all your files</label>
        <input id="home-search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search all your files" className="h-full min-w-0 flex-1 bg-transparent px-3 text-[15px] text-ink outline-none placeholder:text-subtle" />
        {query.trim() && <button type="submit" aria-label="Search" className="press flex h-[42px] w-[42px] items-center justify-center rounded-full bg-primary text-white"><ArrowRight size={18} aria-hidden /></button>}
      </form>

      {error && !data && (
        <Notice tone="danger" icon={CloudOff} title="Can't reach your server" action={<IconButton icon={RefreshCw} label="Try again" tone="danger" onClick={() => void reload()} />}>
          Check your internet connection and try again.
        </Notice>
      )}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1.35fr_1fr] lg:items-start">
        {/* Storage pool */}
        <Link to="/drives" className="rise glass-strong press lift block rounded-[28px] p-5 shadow-soft" aria-label="Storage pool. Manage drives">
          <div className="flex items-center gap-2.5">
            <span className="flex h-9 w-9 items-center justify-center rounded-[12px] bg-mist text-accent"><HardDrive size={18} aria-hidden /></span>
            <span className="flex-1 text-[14px] font-semibold text-muted">Storage pool</span>
            <span className="rounded-full bg-mist px-2.5 py-1 text-[13px] font-semibold text-ink">{plural(data?.accounts.length ?? 0, 'drive')}</span>
          </div>
          {!data ? (
            <div className="mt-4 grid grid-cols-1 gap-3"><Shimmer className="h-10 w-3/5" /><Shimmer className="h-5 w-full" /></div>
          ) : (
            <StorageNumbers summary={data.summary} breakdown={data.breakdown} />
          )}
        </Link>

        <div className="grid grid-cols-1 gap-6">
          <QuickActions onUpload={() => actions.upload(null)} onFolder={() => actions.newFolder(null)} />
          {!showChecklist && data && data.accounts.length > 0 && <div className="hidden lg:block"><DrivesCard accounts={data.accounts} /></div>}
          {showChecklist && (
            <GlassCard className="rise p-4">
              <div className="flex items-center gap-2 px-1">
                <Sparkles size={20} className="text-accent" aria-hidden />
                <h2 className="flex-1 text-[16px] font-semibold">Get started</h2>
                <span className="text-[13px] font-semibold text-muted">{steps.filter((s) => s.done).length} of {steps.length}</span>
                <IconButton icon={X} label="Hide checklist" tone="plain" size={36} onClick={() => setDismissed('hidden')} />
              </div>
              <ul className="stagger mt-1">
                {steps.map((s) => (
                  <li key={s.label}>
                    <button type="button" disabled={s.done} onClick={s.go} className="press flex w-full items-center gap-3 rounded-[14px] px-1.5 py-2.5 text-left hover:bg-hover disabled:cursor-default disabled:hover:bg-transparent">
                      {s.done ? <CircleCheckBig size={21} className="shrink-0 text-success" aria-label="Done" /> : <Circle size={21} className="shrink-0 text-subtle" aria-label="To do" />}
                      <span className={clsx('flex-1 text-[14px]', s.done ? 'text-muted line-through decoration-1' : 'text-ink')}>{s.label}</span>
                      {!s.done && <ChevronRight size={18} className="text-subtle" aria-hidden />}
                    </button>
                  </li>
                ))}
              </ul>
            </GlassCard>
          )}
        </div>
      </div>

      {alerts.map((a) => {
        const broken = Boolean(a.storageAccount?.lastError)
        const freeUp = !broken && a.provider === 'google_drive'
        return (
          <Link key={a.id} to={freeUp ? `/drives/${a.id}/free-up` : '/drives'} className="press block">
            <Notice icon={TriangleAlert} title={broken ? `${a.email} needs attention` : `${a.email} is almost full`} action={<ChevronRight size={20} className="self-center text-muted" aria-hidden />}>
              {broken
                ? 'Open Drives to refresh or reconnect it.'
                : freeUp
                  ? 'Free up space: move its files to your other drives so Gmail keeps working.'
                  : 'New uploads go to your other drives automatically.'}
            </Notice>
          </Link>
        )
      })}

      {data && data.received.length > 0 && (
        <Link to="/shared?tab=received" className="press block">
          <Notice tone="info" icon={Users} title={`${plural(data.received.length, 'item')} shared with you`} action={<ChevronRight size={20} className="self-center text-muted" aria-hidden />}>
            {data.received[0].user?.name ?? 'Someone'} shared “{data.received[0].target?.name ?? 'a file'}”{data.received.length > 1 ? ' and more' : ''}.
          </Notice>
        </Link>
      )}

      {promo === 'shown' && (
        <div className="rise flex items-center gap-3 rounded-[22px] border border-card-border bg-surface/80 p-3 pr-2 shadow-soft">
          <button type="button" onClick={() => setAppInfo(true)} className="press flex min-w-0 flex-1 items-center gap-3 text-left">
            <span className="flex -space-x-2">
              <span className="flex h-10 w-10 items-center justify-center rounded-full border-2 border-surface bg-mist text-link"><ShieldCheck size={18} aria-hidden /></span>
              <span className="flex h-10 w-10 items-center justify-center rounded-full border-2 border-surface bg-mist text-link"><Radar size={18} aria-hidden /></span>
            </span>
            <span className="min-w-0">
              <span className="block truncate text-[14px] font-semibold text-ink">Auto backup &amp; Nearby</span>
              <span className="block truncate text-[12.5px] text-muted">In the Nudge Drive Android app</span>
            </span>
          </button>
          <IconButton icon={X} label="Hide" tone="plain" size={36} onClick={() => setPromo('hidden')} />
        </div>
      )}

      {/* Recent files */}
      <section aria-labelledby="recent-h" className="grid grid-cols-1 gap-3">
        <SectionTitle title={<span id="recent-h">Recent files</span>} action="See all" to="/recent" />
        {loading && !data ? (
          <div className="no-scrollbar -mx-5 flex gap-3 overflow-x-auto px-5 sm:mx-0 sm:grid sm:grid-cols-4 sm:px-0 xl:grid-cols-6">
            {[0, 1, 2, 3].map((i) => <Shimmer key={i} className="h-[186px] w-[150px] shrink-0 rounded-[24px] sm:w-auto" />)}
          </div>
        ) : recent.length === 0 ? (
          <button type="button" onClick={() => actions.upload(null)} className="press flex items-center gap-4 rounded-[24px] border-[1.5px] border-lilac bg-glass-soft p-4 text-left">
            <span className="flex h-12 w-12 items-center justify-center rounded-[16px] bg-primary text-white"><ImagePlus size={22} aria-hidden /></span>
            <span>
              <span className="block text-[14px] font-semibold text-ink">Nothing here yet</span>
              <span className="block text-[13px] text-muted">Upload photos, videos or documents{data?.accounts.length ? '' : ' once you connect a drive'}.</span>
            </span>
          </button>
        ) : (
          <ul className="stagger no-scrollbar -mx-5 flex gap-3 overflow-x-auto px-5 pb-1 sm:mx-0 sm:grid sm:grid-cols-4 sm:overflow-visible sm:px-0 xl:grid-cols-6">
            {recent.map((f) => <RecentCard key={f.id} file={f} onOpen={() => actions.open(f, recent)} />)}
          </ul>
        )}
      </section>

      {folders.length > 0 && (
        <section aria-labelledby="folders-h" className="grid grid-cols-1 gap-2">
          <SectionTitle title={<span id="folders-h">Folders</span>} action="All folders" to="/folders" />
          <div className="stagger no-scrollbar -mx-5 flex gap-3 overflow-x-auto px-5 pb-2 sm:mx-0 sm:flex-wrap sm:px-0">
            {folders.map((f) => <FolderCard key={f.id} folder={f} onOpen={() => navigate(`/files?folder=${f.id}`)} />)}
          </div>
        </section>
      )}

      {data && data.accounts.length > 0 && (
        <div className={showChecklist ? '' : 'lg:hidden'}><DrivesCard accounts={data.accounts} /></div>
      )}
      <AppOnlyDialog open={appInfo} onClose={() => setAppInfo(false)} />
    </div>
  )
}

function StorageNumbers({ summary, breakdown }: { summary: { totalBytes: string; usedBytes: string; availableBytes: string }; breakdown: { photo: string; video: string; document: string } }) {
  const total = toNumber(summary.totalBytes)
  const frac = total > 0 ? toNumber(summary.usedBytes) / total : 0
  return (
    <>
      <div className="mt-4 flex items-end gap-1.5">
        <span className="font-display text-[34px] font-bold leading-none tracking-[-0.04em] text-ink">{formatBytes(summary.availableBytes)}</span>
        <span className="pb-0.5 text-[16px] font-semibold text-muted">free</span>
        <span className="ml-auto pb-1 text-[13px] font-semibold text-muted">{Math.round(frac * 100)}% used</span>
      </div>
      <div className="mt-3"><StripedBar fraction={frac} label="Storage used" /></div>
      <div className="mt-3 flex flex-wrap gap-2">
        <Legend label="Photos" bytes={breakdown.photo} color="#1e9e57" />
        <Legend label="Videos" bytes={breakdown.video} color="#f59e0b" />
        <Legend label="Docs" bytes={breakdown.document} color="#2e64e0" />
      </div>
      <p className="mt-3 flex items-center gap-1 text-[12.5px] text-subtle">{formatBytes(summary.usedBytes)} of {formatBytes(summary.totalBytes)} used · manage drives<ChevronRight size={14} aria-hidden /></p>
    </>
  )
}

function Legend({ label, bytes, color }: { label: string; bytes: string; color: string }) {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full bg-mist px-2.5 py-1 text-[11.5px] font-semibold">
      <span className="h-[7px] w-[7px] rounded-full" style={{ background: color }} />
      <span className="text-ink">{label} {formatBytes(bytes)}</span>
    </span>
  )
}

function QuickActions({ onUpload, onFolder }: { onUpload: () => void; onFolder: () => void }) {
  const items = [
    { label: 'Upload', icon: CloudUpload, primary: true, onClick: onUpload },
    { label: 'Folder', icon: FolderPlus, onClick: onFolder },
    { label: 'Add drive', icon: HardDriveUpload, to: '/drives' },
    { label: 'Photos', icon: Image, to: '/photos' },
    { label: 'Shared', icon: Link2, to: '/shared' },
  ]
  return (
    <nav aria-label="Quick actions" className="rise grid grid-cols-5 gap-1">
      {items.map((q) => {
        const tile = (
          <>
            <span className={clsx('flex h-[58px] w-[58px] items-center justify-center rounded-[22px] shadow-soft transition-[transform,box-shadow] duration-300 group-hover:-translate-y-0.5', q.primary ? 'bg-primary text-white shadow-[0_14px_28px_-12px_rgb(112_89_240/0.8)]' : 'glass text-ink')}>
              <q.icon size={24} aria-hidden />
            </span>
            <span className="mt-1.5 text-[13px] font-semibold text-ink">{q.label}</span>
          </>
        )
        const cls = 'group press flex flex-col items-center rounded-[20px] py-1'
        return q.to ? <Link key={q.label} to={q.to} className={cls}>{tile}</Link> : <button key={q.label} type="button" onClick={q.onClick} className={cls}>{tile}</button>
      })}
    </nav>
  )
}

function RecentCard({ file, onOpen }: { file: FileItem; onOpen: () => void }) {
  return (
    <li className="w-[150px] shrink-0 sm:w-auto">
      <button type="button" onClick={onOpen} className="glass press press-cell lift flex w-full flex-col rounded-[24px] p-2 text-left shadow-soft">
        <span className="block h-[110px] w-full"><FileThumb file={file} fill radius={18} /></span>
        <span className="mt-2 block truncate px-1 text-[14px] font-bold text-ink">{file.name}</span>
        <span className="block truncate px-1 pb-1 text-[11px] font-semibold text-muted">{formatBytes(file.sizeBytes)} · {friendlyDate(file.createdAt)}</span>
      </button>
    </li>
  )
}

/** "Your drives": usage ring per account, up to four, then "+N more" (HomeScreen DrivesCard). */
function DrivesCard({ accounts }: { accounts: ConnectedAccount[] }) {
  return (
    <section aria-labelledby="drives-h" className="grid grid-cols-1 gap-3">
      <SectionTitle title={<span id="drives-h">Your drives</span>} action="Manage" to="/drives" />
      <GlassCard className="stagger grid grid-cols-1 gap-1 p-2">
        {accounts.slice(0, 4).map((a) => {
          const total = a.storageAccount?.totalBytes ?? null
          return (
            <Link key={a.id} to="/drives" className="press press-row flex items-center gap-3 rounded-[18px] p-2.5 hover:bg-hover">
              <span className="relative">
                <UsageRing fraction={usedFraction(a)} size={42} stroke={4} label={total === null ? '∞' : undefined} />
                <span className="absolute -bottom-0.5 -right-0.5 flex h-[18px] w-[18px] items-center justify-center rounded-full bg-surface">
                  {a.provider === 's3' ? <Database size={11} className="text-accent" aria-hidden /> : <DriveGlyph size={11} />}
                </span>
              </span>
              <span className="min-w-0 flex-1">
                <span className="block truncate text-[14px] font-semibold text-ink">{a.displayName || a.email}</span>
                <span className="block text-[12.5px] text-muted">{total === null ? `${formatBytes(a.storageAccount?.usedBytes)} used` : `${formatBytes(Math.max(0, toNumber(total) - toNumber(a.storageAccount?.usedBytes)))} free of ${formatBytes(total)}`}</span>
              </span>
              {a.storageAccount?.lastError && <TriangleAlert size={17} className="text-warn" aria-label="Needs attention" />}
            </Link>
          )
        })}
        {accounts.length > 4 && <Link to="/drives" className="press rounded-[14px] px-3 py-2 text-[13px] font-semibold text-link hover:bg-hover">+{accounts.length - 4} more</Link>}
      </GlassCard>
    </section>
  )
}
