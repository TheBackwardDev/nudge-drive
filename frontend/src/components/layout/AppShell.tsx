import { createContext, Suspense, useContext, useEffect, useLayoutEffect, useRef, useState, type DragEvent, type FormEvent, type ReactNode } from 'react'
import { Link as RouterLink, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import clsx from 'clsx'
import {
  Bell, Clock, CloudUpload, FileSpreadsheet, FileText, Folder, FolderOpen, FolderPlus, FolderUp, HardDrive, History, House, Image, Info, KeyRound, Layers,
  Link, LogOut, Plus, Presentation, Search, Settings, Trash2, UserRound, type LucideIcon,
} from 'lucide-react'
import { Wordmark } from '@/components/brand/Brand'
import { StripedBar } from '@/components/ui/Surface'
import { Menu, useMenu } from '@/components/ui/Menu'
import { BellButton, PageSkeleton, ScrollToTop, ThemeToggle } from '@/components/layout/Chrome'
import { Transfers } from '@/components/layout/Transfers'
import { useAuth } from '@/context/AuthContext'
import { useFileActions } from '@/context/FileActions'
import { entriesFromDrop, useUploads } from '@/context/UploadContext'
import { useToast } from '@/context/ToastContext'
import { api, errorMessage } from '@/lib/api'
import { useLoad, useOnChange } from '@/lib/hooks'
import { formatBytes, plural, toNumber } from '@/lib/format'
import { onceADay, postNotice, storageAlertsOn, unseenInvites } from '@/lib/notices'

/** Lets the current screen say where dropped files should go (My files sets the open folder). */
const DropTargetContext = createContext<(folderId: string | null) => void>(() => undefined)
export const useDropTarget = () => useContext(DropTargetContext)

type NavItem = { to: string; label: string; icon: LucideIcon; match?: string[] }

const PRIMARY: NavItem[] = [
  { to: '/home', label: 'Home', icon: House },
  { to: '/files', label: 'My files', icon: FolderOpen },
  { to: '/photos', label: 'Photos', icon: Image },
  { to: '/recent', label: 'Recent', icon: Clock },
  { to: '/folders', label: 'All folders', icon: Folder },
  { to: '/shared', label: 'Shared', icon: Link },
]
const SECONDARY: NavItem[] = [
  { to: '/drives', label: 'Drives', icon: HardDrive },
  { to: '/activity', label: 'Activity', icon: History },
  { to: '/trash', label: 'Trash', icon: Trash2 },
  { to: '/api-keys', label: 'API keys', icon: KeyRound },
]
const TABS: NavItem[] = [
  { to: '/home', label: 'Home', icon: House },
  { to: '/files', label: 'Files', icon: FileText, match: ['/files', '/photos', '/recent', '/folders', '/shared', '/trash', '/activity'] },
  { to: '/drives', label: 'Drives', icon: Layers },
  { to: '/profile', label: 'Profile', icon: UserRound, match: ['/profile', '/settings', '/api-keys', '/notifications', '/about'] },
]

function SideLink({ item }: { item: NavItem }) {
  return (
    <NavLink
      to={item.to}
      data-side=""
      className={({ isActive }) =>
        clsx('press relative flex h-[42px] items-center gap-3 rounded-[14px] px-3 text-[14px] font-bold', isActive ? 'text-ink' : 'text-muted hover:bg-hover hover:text-ink')
      }
    >
      {({ isActive }) => (
        <>
          <item.icon size={19} aria-hidden className={clsx('transition-colors duration-300', isActive && 'text-link')} />
          {item.label}
        </>
      )}
    </NavLink>
  )
}

function StorageMini() {
  const { data, reload } = useLoad(() => api.storage.summary())
  useOnChange(['storage', 'accounts'], reload)
  const total = toNumber(data?.totalBytes)
  if (!data || total <= 0) return null
  const used = toNumber(data.usedBytes)
  return (
    <RouterLink to="/drives" className="press block rounded-[18px] border border-card-border bg-surface/70 p-3 hover:border-lilac">
      <span className="flex items-center justify-between text-[13px] font-semibold text-muted">Storage<span className="text-[12px] text-subtle">{Math.round((used / total) * 100)}%</span></span>
      <span className="mt-2 block"><StripedBar fraction={used / total} height={8} label="Storage used" /></span>
      <span className="mt-1.5 block text-[12px] text-muted">{formatBytes(used)} of {formatBytes(total)}</span>
    </RouterLink>
  )
}

/** The "New" menu (sidebar button, phone + buttons): uploads, folders and Google files. */
export function useNewMenu(folderId: string | null) {
  const actions = useFileActions()
  const menu = useMenu()
  const items = [
    { label: 'Upload files', icon: CloudUpload, onSelect: () => actions.upload(folderId, 'files') },
    { label: 'Upload a folder', icon: FolderUp, onSelect: () => actions.upload(folderId, 'folder') },
    { label: 'New folder', icon: FolderPlus, onSelect: () => actions.newFolder(folderId) },
    'divider' as const,
    { label: 'Google Doc', icon: FileText, onSelect: () => actions.newDocument('document', folderId) },
    { label: 'Google Sheet', icon: FileSpreadsheet, onSelect: () => actions.newDocument('spreadsheet', folderId) },
    { label: 'Google Slides', icon: Presentation, onSelect: () => actions.newDocument('presentation', folderId) },
  ]
  return { menu, element: <Menu open={menu.open} anchor={menu.anchor} onClose={menu.close} items={items} title="Add to Nudge Drive" /> }
}

/** One lavender pill that glides to the active link (the desktop answer to the phone's tab pill). */
function useIndicator(pathname: string) {
  const ref = useRef<HTMLDivElement>(null)
  const [pos, setPos] = useState<{ y: number; h: number; animate: boolean } | null>(null)
  useLayoutEffect(() => {
    const root = ref.current
    if (!root) return
    const measure = (animate: boolean) => {
      const el = root.querySelector<HTMLElement>('a[data-side][aria-current="page"]')
      setPos((p) => (el ? { y: el.offsetTop, h: el.offsetHeight, animate: animate && !!p } : p && { ...p, h: 0, animate: false }))
    }
    measure(true)
    const ro = new ResizeObserver(() => measure(false))
    ro.observe(root)
    return () => ro.disconnect()
  }, [pathname])
  return { ref, pos }
}

function Sidebar({ dropFolder }: { dropFolder: string | null }) {
  const { menu, element } = useNewMenu(dropFolder)
  const { pathname } = useLocation()
  const ind = useIndicator(pathname)
  return (
    <aside className="sidebar-glass sticky top-0 hidden h-dvh w-[248px] shrink-0 flex-col border-r border-line/70 px-3.5 py-5 lg:flex">
      <NavLink to="/home" className="mb-5 rounded-xl px-2"><Wordmark size={18} /></NavLink>
      <button
        type="button"
        onClick={(e) => menu.openAt(e.currentTarget)}
        aria-haspopup="menu"
        className="press mb-4 flex h-12 items-center gap-2.5 rounded-[18px] bg-primary px-4 text-[15px] font-bold text-white shadow-[0_12px_26px_-12px_rgb(112_89_240/0.8)] hover:bg-primary-hover"
      >
        <Plus size={20} aria-hidden />New
      </button>
      {element}
      <nav aria-label="Main" className="no-scrollbar flex min-h-0 flex-1 flex-col overflow-y-auto">
        <div ref={ind.ref} className="relative flex flex-1 flex-col">
          <span
            aria-hidden
            className="side-indicator"
            style={{
              transform: `translateY(${ind.pos?.y ?? 0}px)`,
              height: ind.pos?.h ?? 0,
              opacity: ind.pos?.h ? 1 : 0,
              transition: ind.pos?.animate ? undefined : 'none',
            }}
          />
          <div className="grid grid-cols-1 gap-0.5">{PRIMARY.map((i) => <SideLink key={i.to} item={i} />)}</div>
          <div className="mx-2.5 my-3 h-px bg-line" />
          <div className="grid grid-cols-1 gap-0.5">{SECONDARY.map((i) => <SideLink key={i.to} item={i} />)}</div>
          <div className="mt-auto grid grid-cols-1 gap-2 pt-4">
            <StorageMini />
            <SideLink item={{ to: '/settings', label: 'Settings', icon: Settings }} />
          </div>
        </div>
      </nav>
    </aside>
  )
}

function TopBar() {
  const navigate = useNavigate()
  const location = useLocation()
  const { user, signOut } = useAuth()
  const menu = useMenu()
  const [q, setQ] = useState(() => new URLSearchParams(location.search).get('q') ?? '')

  useEffect(() => {
    if (location.pathname === '/files') setQ(new URLSearchParams(location.search).get('q') ?? '')
  }, [location.pathname, location.search])

  function submit(e: FormEvent) {
    e.preventDefault()
    const term = q.trim()
    navigate(term ? `/files?q=${encodeURIComponent(term)}` : '/files')
  }

  return (
    <div className="sticky top-0 z-30 hidden items-center gap-3 px-8 pb-2 pt-5 lg:flex">
      <form role="search" onSubmit={submit} className="glass flex h-12 max-w-xl flex-1 items-center rounded-[24px] pl-4 pr-1.5 shadow-soft focus-within:ring-2 focus-within:ring-accent/40">
        <Search size={19} className="text-muted" aria-hidden />
        <label htmlFor="global-search" className="sr-only">Search all your files</label>
        <input id="global-search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search all your files" className="h-full min-w-0 flex-1 bg-transparent px-3 text-[15px] text-ink outline-none placeholder:text-subtle" />
        <kbd className="mr-2 hidden rounded-md border border-line px-1.5 text-[11px] text-subtle xl:block">/</kbd>
      </form>
      <div className="ml-auto flex items-center gap-2">
        <ThemeToggle />
        <BellButton />
        <button
          type="button"
          onClick={(e) => menu.openAt(e.currentTarget)}
          aria-haspopup="menu"
          aria-label="Account menu"
          className="press ml-1 flex h-11 w-11 items-center justify-center rounded-full bg-gradient-to-br from-[#cdbfff] to-iris font-display text-[16px] font-bold text-white shadow-soft"
        >
          {(user?.name ?? 'N').charAt(0).toUpperCase()}
        </button>
        <Menu
          open={menu.open}
          anchor={menu.anchor}
          onClose={menu.close}
          title="Account"
          header={
            <div className="px-3 pb-2 pt-1.5">
              <p className="truncate text-[14px] font-semibold text-ink">{user?.name}</p>
              <p className="truncate text-[12px] text-muted">{user?.email}</p>
            </div>
          }
          items={[
            'divider',
            { label: 'Profile', icon: UserRound, onSelect: () => navigate('/profile') },
            { label: 'Notifications', icon: Bell, onSelect: () => navigate('/notifications') },
            { label: 'Settings', icon: Settings, onSelect: () => navigate('/settings') },
            { label: 'About Nudge Drive', icon: Info, onSelect: () => navigate('/about') },
            { label: 'Sign out', icon: LogOut, onSelect: () => void signOut().then(() => navigate('/login')) },
          ]}
        />
      </div>
    </div>
  )
}

function BottomNav() {
  const { pathname } = useLocation()
  return (
    <nav aria-label="Main" className="fixed inset-x-0 bottom-0 z-50 px-5 pb-[max(12px,env(safe-area-inset-bottom))] lg:hidden">
      <div className="nav-glass mx-auto flex h-[70px] max-w-md items-center justify-between rounded-[36px] px-[7px] shadow-lift">
        {TABS.map((t) => {
          const active = (t.match ?? [t.to]).some((m) => pathname === m || pathname.startsWith(`${m}/`))
          return (
            <NavLink
              key={t.to}
              to={t.to}
              aria-label={t.label}
              data-active={active || undefined}
              className={clsx('tab press flex h-14 items-center justify-center rounded-[28px] text-[15px] font-bold', active ? 'bg-primary px-[18px] text-white shadow-[0_10px_22px_-12px_rgb(112_89_240/0.9)]' : 'px-[17px] text-ink')}
            >
              <t.icon size={22} aria-hidden className="shrink-0" />
              <span className="tab-label" aria-hidden><span><span className="pl-2">{t.label}</span></span></span>
            </NavLink>
          )
        })}
      </div>
    </nav>
  )
}

/** Signed-in layout: sidebar + top bar on wide screens, floating glass tab bar on phones. Accepts file drops anywhere. */
/** Storage alerts and new invites become inbox notices (at most once a day per alert, like the app). */
function useNoticeWatcher() {
  useEffect(() => {
    let stopped = false
    async function check() {
      try {
        const [summary, { accounts }, invites] = await Promise.all([api.storage.summary(), api.accounts.list(), api.invites.list().catch(() => null)])
        if (stopped) return
        if (storageAlertsOn()) {
          const total = toNumber(summary.totalBytes)
          const pct = total > 0 ? toNumber(summary.usedBytes) / total : 0
          if (pct > 0.85 && onceADay('pool')) {
            postNotice({ type: 'storage', title: `Storage pool is ${Math.round(pct * 100)}% full`, body: `Only ${formatBytes(summary.availableBytes)} left. Connect another Google account to add 15 GB.`, to: '/drives' })
          }
          for (const a of accounts) {
            const t = toNumber(a.storageAccount?.totalBytes)
            if (a.storageAccount?.lastError) {
              if (onceADay(`err:${a.id}`)) postNotice({ type: 'storage', title: `${a.email} needs attention`, body: "Its storage couldn't be refreshed. Try reconnecting it in Drives.", to: '/drives' })
            } else if (t > 0 && toNumber(a.storageAccount?.usedBytes) / t > 0.92 && onceADay(`full:${a.id}`)) {
              postNotice(
                a.provider === 'google_drive'
                  ? { type: 'storage', title: `${a.email} is almost full`, body: 'Move its files to your other drives so Gmail and Google Photos keep working.', to: `/drives/${a.id}/free-up` }
                  : { type: 'storage', title: `${a.email} is almost full`, body: 'New uploads will go to your other drives.', to: '/drives' },
              )
            }
          }
        }
        if (invites) {
          const fresh = unseenInvites(invites.received.map((i) => i.id))
          for (const inv of invites.received.filter((i) => fresh.includes(i.id)).slice(0, 5)) {
            postNotice({
              type: 'invite',
              title: `${inv.user?.name ?? 'Someone'} shared “${inv.target?.name ?? 'an item'}”`,
              body: inv.role === 'editor' ? 'You can view and edit it.' : 'You can view it.',
              to: '/shared?tab=received',
            })
          }
          if (fresh.length > 5) postNotice({ type: 'invite', title: `${plural(fresh.length, 'item')} shared with you`, body: 'Open Shared to see them.', to: '/shared?tab=received' })
        }
      } catch {
        /* offline: try again later */
      }
    }
    const first = window.setTimeout(() => void check(), 2500)
    const t = window.setInterval(() => void check(), 10 * 60 * 1000)
    return () => {
      stopped = true
      window.clearTimeout(first)
      window.clearInterval(t)
    }
  }, [])
}

export function AppShell({ children }: { children?: ReactNode }) {
  useNoticeWatcher()
  const { pathname } = useLocation()
  const [dropFolder, setDropFolder] = useState<string | null>(null)
  const [dragging, setDragging] = useState(false)
  const depth = useRef(0)
  const { upload, uploadTree } = useUploads()
  const toast = useToast()

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === '/' && !(e.target as HTMLElement).closest('input, textarea, select, [contenteditable]') && !document.querySelector('dialog[open]')) {
        const el = document.getElementById('global-search') ?? document.getElementById('files-search')
        if (el && el.offsetParent !== null) {
          e.preventDefault()
          el.focus()
        }
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  const isFileDrag = (e: DragEvent) => e.dataTransfer.types.includes('Files')

  async function onDrop(e: DragEvent) {
    if (!isFileDrag(e)) return
    e.preventDefault()
    depth.current = 0
    setDragging(false)
    try {
      const entries = await entriesFromDrop(e.dataTransfer)
      if (entries.some((x) => x.dirs.length)) await uploadTree(entries, dropFolder)
      else upload(entries.map((x) => x.file), dropFolder)
    } catch (err) {
      toast.error(errorMessage(err, "Couldn't read the dropped items"))
    }
  }

  return (
    <DropTargetContext.Provider value={setDropFolder}>
      <div
        className="flex min-h-dvh"
        onDragEnter={(e) => { if (isFileDrag(e)) { depth.current++; setDragging(true) } }}
        onDragLeave={(e) => { if (isFileDrag(e) && --depth.current <= 0) { depth.current = 0; setDragging(false) } }}
        onDragOver={(e) => { if (isFileDrag(e)) e.preventDefault() }}
        onDrop={onDrop}
      >
        <div className="app-bg" aria-hidden />
        <a href="#main" className="sr-only z-[100] rounded-xl bg-primary px-4 py-2 text-white focus:not-sr-only focus:fixed focus:left-4 focus:top-4">Skip to content</a>
        <Sidebar dropFolder={dropFolder} />
        <div className="flex min-w-0 flex-1 flex-col">
          <TopBar />
          <main id="main" className="mx-auto w-full max-w-[1280px] flex-1 px-5 pb-36 pt-[max(12px,env(safe-area-inset-top))] sm:px-6 lg:px-8 lg:pb-12 lg:pt-3">
            {/* AnimatedContent: each screen fades in with a slight scale (0.96 -> 1). */}
            <Suspense key={pathname} fallback={<PageSkeleton />}>
              <div className="page-enter">{children ?? <Outlet />}</div>
            </Suspense>
          </main>
        </div>
        <BottomNav />
        <ScrollToTop />
        <Transfers />
        {dragging && (
          <div className="pointer-events-none fixed inset-0 z-[70] flex items-center justify-center bg-[var(--scrim)] p-6 backdrop-blur-sm">
            <div className="glass-strong flex flex-col items-center gap-3 rounded-[32px] border-2 border-dashed border-iris px-12 py-10 text-center shadow-lift">
              <span className="flex h-16 w-16 items-center justify-center rounded-[22px] bg-primary text-white"><CloudUpload size={30} aria-hidden /></span>
              <p className="font-display text-[22px] font-bold text-ink">Drop to upload</p>
              <p className="text-[14px] text-muted">Files and folders go {dropFolder ? 'into this folder' : 'to My Files'}</p>
            </div>
          </div>
        )}
      </div>
    </DropTargetContext.Provider>
  )
}
