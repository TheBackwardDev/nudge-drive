import { useEffect, useMemo, useState, type CSSProperties, type FormEvent, type MouseEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import {
  ArrowLeft, ArrowUpDown, Check, ChevronRight, CloudOff, CloudUpload, FolderOpen, FolderPlus, History, LayoutGrid, LayoutList, Plus,
  RefreshCw, Search, SearchX, Share2, SlidersHorizontal, Trash2, X,
} from 'lucide-react'
import { Button, IconButton } from '@/components/ui/Button'
import { Dialog } from '@/components/ui/Dialog'
import { Select, TextField } from '@/components/ui/Field'
import { Menu, useMenu, type MenuAnchor } from '@/components/ui/Menu'
import { SelectionBar, useItemMenu, useSelection } from '@/components/files/Selection'
import { Chip, EmptyState, SectionTitle, Shimmer } from '@/components/ui/Surface'
import { FileGridCell, FileRow, FolderCard } from '@/components/files/FileVisuals'
import { useDropTarget, useNewMenu } from '@/components/layout/AppShell'
import { useFileActions } from '@/context/FileActions'
import { useToast } from '@/context/ToastContext'
import { api, errorMessage } from '@/lib/api'
import { FILTER_KINDS, KINDS, SORT_LABELS, folderPath, matchesFilter, providerLabel, sortFiles, sortFolders, type KindKey, type SortMode } from '@/lib/files'
import { formatBytes, plural } from '@/lib/format'
import { emitChange, useDocumentTitle, useIsDesktop, useLoad, useOnChange, usePref } from '@/lib/hooks'
import type { FileFilters, FileItem, Folder } from '@/lib/types'

const ADVANCED = ['account', 'min', 'max', 'from', 'to'] as const

export default function FilesPage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const toast = useToast()
  const actions = useFileActions()
  const desktop = useIsDesktop()
  const setDropTarget = useDropTarget()
  const [view, setView] = usePref('nudge:files-view', 'list', ['list', 'grid'] as const)
  const [sort, setSort] = usePref<SortMode>('nudge:files-sort', 'newest', ['newest', 'oldest', 'name', 'largest'])
  const selection = useSelection()
  const { selected, selecting, toggle } = selection
  const [filtersOpen, setFiltersOpen] = useState(false)
  const [syncing, setSyncing] = useState(false)
  const sortMenu = useMenu()
  const itemMenu = useItemMenu()

  const folderId = params.get('folder')
  const q = params.get('q') ?? ''
  const kind = (params.get('kind') as KindKey | null) ?? null
  const advancedCount = ADVANCED.filter((k) => params.get(k)).length
  const searching = Boolean(q.trim() || kind || advancedCount)

  // Server-side filters (account / size / dates); name search and kind chips filter locally.
  const serverFilters: FileFilters = {
    accountId: params.get('account') ?? undefined,
    minSize: params.get('min') ? String(Number(params.get('min')) * 1024 * 1024) : undefined,
    maxSize: params.get('max') ? String(Number(params.get('max')) * 1024 * 1024) : undefined,
    startDate: params.get('from') ? new Date(`${params.get('from')}T00:00:00`).toISOString() : undefined,
    endDate: params.get('to') ? new Date(`${params.get('to')}T23:59:59`).toISOString() : undefined,
  }
  const filterKey = JSON.stringify(serverFilters)
  const { data, error, loading, reload } = useLoad(() => api.files.list(serverFilters), [filterKey])
  useOnChange(['files'], reload)
  const all = useMemo(() => data?.files ?? [], [data])

  const folders = actions.folders
  const current = folders.find((f) => f.id === folderId) ?? null
  const path = useMemo(() => folderPath(folders, folderId), [folders, folderId])
  useDocumentTitle(current?.name ?? 'My files')

  useEffect(() => {
    setDropTarget(folderId)
    return () => setDropTarget(null)
  }, [folderId, setDropTarget])
  const clearSelection = selection.clear
  useEffect(() => clearSelection(), [folderId, q, kind, filterKey, clearSelection])

  const term = q.trim().toLowerCase()
  const files = useMemo(() => {
    const list = searching
      ? all.filter((f) => (!term || f.name.toLowerCase().includes(term)) && (!kind || matchesFilter(f, kind)))
      : all.filter((f) => (f.folderId ?? null) === folderId)
    return sortFiles(list, sort)
  }, [all, searching, term, kind, folderId, sort])

  const shownFolders = useMemo(() => {
    if (searching) return term && !kind ? sortFolders(folders.filter((f) => f.name.toLowerCase().includes(term)), 'name') : []
    return sortFolders(folders.filter((f) => f.parentId === folderId), sort)
  }, [folders, searching, term, kind, folderId, sort])

  const counts = useMemo(() => {
    const c = Object.fromEntries(FILTER_KINDS.map((k) => [k, 0])) as Record<KindKey, number>
    for (const f of all) for (const k of FILTER_KINDS) if (matchesFilter(f, k)) c[k]++
    return c
  }, [all])
  const filesIn = (id: string) => all.filter((f) => f.folderId === id).length

  function setParam(changes: Record<string, string | null>) {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(changes)) {
      if (v) next.set(k, v)
      else next.delete(k)
    }
    setParams(next, { replace: 'q' in changes })
  }
  const openFolder = (f: Folder | null) => {
    const next = new URLSearchParams()
    if (f) next.set('folder', f.id)
    setParams(next)
  }
  const clearSearch = () => setParams(folderId ? { folder: folderId } : {})

  const openItemMenu = (file: FileItem, anchor: MenuAnchor) => itemMenu.show(file.name, actions.fileMenu(file, files), anchor)
  const openFolderMenu = (folder: Folder, anchor: MenuAnchor) => itemMenu.show(folder.name, actions.folderMenu(folder, () => openFolder(folder)), anchor)
  function onBackgroundMenu(e: MouseEvent) {
    if ((e.target as HTMLElement).closest('button, a, input, [draggable="true"]')) return
    e.preventDefault()
    itemMenu.show(current?.name ?? 'My files', [
      { label: 'Upload here', icon: CloudUpload, onSelect: () => actions.upload(folderId) },
      { label: 'New folder', icon: FolderPlus, onSelect: () => actions.newFolder(folderId) },
      { label: 'Sync from Google Drive', icon: RefreshCw, onSelect: () => void sync() },
    ], { x: e.clientX, y: e.clientY })
  }

  async function sync() {
    setSyncing(true)
    try {
      const { results } = await api.files.syncGoogle()
      const n = results.reduce((s, r) => s + r.created + r.updated + r.deleted, 0)
      emitChange('files', 'folders', 'storage')
      toast.show(results.length === 0 ? 'No Google Drive connected yet' : n === 0 ? 'Everything is up to date' : `Synced ${plural(n, 'change')} from Google Drive`, { tone: 'success' })
    } catch (e) {
      toast.error(errorMessage(e, 'Sync failed'))
    } finally {
      setSyncing(false)
    }
  }

  const dropOnFolder = (target: Folder) => (fileId: string) => {
    const ids = selected.has(fileId) ? [...selected] : [fileId]
    void actions.moveTo(ids, target.id).then(clearSelection, () => undefined)
  }

  const { menu: newMenu, element: newMenuEl } = useNewMenu(folderId)
  const title = searching ? (q.trim() ? `“${q.trim()}”` : kind ? KINDS[kind].label : 'Filtered files') : current?.name ?? 'My files'

  return (
    <div onContextMenu={onBackgroundMenu} className="min-h-[60vh]">
      {/* Header / selection bar */}
      {selecting ? (
        <SelectionBar files={files} selection={selection} />
      ) : (
        <header className="flex items-center gap-2 pb-3 pt-2.5 lg:pt-4">
          {(current || searching) && <IconButton icon={ArrowLeft} label="Back" onClick={() => (searching ? clearSearch() : openFolder(folders.find((f) => f.id === current?.parentId) ?? null))} />}
          <h1 className="min-w-0 flex-1 truncate pl-1 text-[28px] font-bold tracking-[-0.03em] text-ink">{title}</h1>
          <IconButton icon={view === 'grid' ? LayoutList : LayoutGrid} label={view === 'grid' ? 'List view' : 'Grid view'} onClick={() => setView(view === 'grid' ? 'list' : 'grid')} />
          <IconButton icon={ArrowUpDown} label="Sort and more" aria-haspopup="menu" onClick={(e) => sortMenu.openAt(e.currentTarget)} />
          <IconButton icon={Plus} label="Add files or folders" tone="primary" aria-haspopup="menu" onClick={(e) => newMenu.openAt(e.currentTarget)} />
        </header>
      )}
      {newMenuEl}
      <Menu
        open={sortMenu.open}
        anchor={sortMenu.anchor}
        onClose={sortMenu.close}
        title="Sort and more"
        items={[
          ...(Object.keys(SORT_LABELS) as SortMode[]).map((m) => ({ label: SORT_LABELS[m], icon: sort === m ? Check : undefined, checked: sort === m, onSelect: () => setSort(m) })),
          'divider',
          { label: 'New folder', icon: FolderPlus, onSelect: () => actions.newFolder(folderId) },
          { label: syncing ? 'Syncing…' : 'Sync from Google Drive', icon: RefreshCw, disabled: syncing, onSelect: () => void sync() },
          { label: 'Filters', icon: SlidersHorizontal, hint: advancedCount ? String(advancedCount) : undefined, onSelect: () => setFiltersOpen(true) },
        ]}
      />
      {itemMenu.element}

      {/* Search + filters */}
      <SearchBox value={q} onChange={(v) => setParam({ q: v || null })} placeholder={current ? 'Search all drives…' : 'Search files, folders…'} />
      <div className="no-scrollbar -mx-5 mt-3 flex gap-2 overflow-x-auto px-5 py-0.5 sm:mx-0 sm:flex-wrap sm:px-0" role="group" aria-label="Filter by type">
        <Chip selected={!kind} onClick={() => setParam({ kind: null })}>All</Chip>
        {FILTER_KINDS.map((k) => (
          <Chip key={k} selected={kind === k} icon={KINDS[k].icon} onClick={() => setParam({ kind: kind === k ? null : k })}>{KINDS[k].label}</Chip>
        ))}
        <Chip selected={advancedCount > 0} icon={SlidersHorizontal} onClick={() => setFiltersOpen(true)}>{advancedCount ? `Filters · ${advancedCount}` : 'Filters'}</Chip>
      </div>

      {current && !searching && (
        <nav aria-label="Folder path" className="no-scrollbar mt-3 flex items-center gap-0.5 overflow-x-auto text-[13px] font-semibold">
          <button type="button" onClick={() => openFolder(null)} className="press shrink-0 rounded-[10px] px-1.5 py-1.5 text-link hover:bg-hover">My Files</button>
          {path.map((f, i) => (
            <span key={f.id} className="flex shrink-0 items-center">
              <ChevronRight size={16} className="text-subtle" aria-hidden />
              {i === path.length - 1 ? (
                <span aria-current="location" className="px-1.5 py-1.5 text-ink">{f.name}</span>
              ) : (
                <button type="button" onClick={() => openFolder(f)} className="press rounded-[10px] px-1.5 py-1.5 text-link hover:bg-hover">{f.name}</button>
              )}
            </span>
          ))}
        </nav>
      )}

      {!current && !searching && <CategoryGrid counts={counts} onKind={(k) => setParam({ kind: k })} onRoute={navigate} />}

      {/* Folders */}
      {(shownFolders.length > 0 || (!searching && data)) && (
        <section className="mt-6" aria-labelledby="folders-h">
          <SectionTitle title={<span id="folders-h">{searching ? 'Matching folders' : 'Folders'}</span>} action={searching ? undefined : 'New'} onAction={() => actions.newFolder(folderId)} />
          <div className="no-scrollbar -mx-5 mt-2 flex gap-3 overflow-x-auto px-5 pb-2 sm:mx-0 sm:flex-wrap sm:px-0">
            {shownFolders.map((f) => (
              <FolderCard
                key={f.id}
                folder={f}
                subtitle={plural(filesIn(f.id), 'file')}
                onOpen={() => openFolder(f)}
                onMenu={(a) => openFolderMenu(f, a)}
                onDropFile={desktop ? dropOnFolder(f) : undefined}
              />
            ))}
            {!searching && (
              <button type="button" onClick={() => actions.newFolder(folderId)} className="press mt-3 flex h-[106px] w-[96px] shrink-0 flex-col items-center justify-center gap-1.5 rounded-[20px] border-[1.5px] border-lavender bg-glass-button text-[13px] font-semibold text-link hover:bg-glass">
                <FolderPlus size={22} aria-hidden />New
              </button>
            )}
          </div>
        </section>
      )}

      {/* Files */}
      <section className="mt-5" aria-labelledby="files-h">
        <div className="mb-2.5 flex items-center gap-2">
          <h2 id="files-h" className="flex-1 text-[19px] font-semibold">{searching ? 'Results' : current ? 'Files' : 'Files in My Files'}</h2>
          {data && <span className="text-[13px] font-semibold text-muted">{files.length}</span>}
        </div>
        {loading && !data ? (
          <div className={view === 'grid' ? 'grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5' : 'grid grid-cols-1 gap-2.5'}>
            {Array.from({ length: 6 }, (_, i) => <Shimmer key={i} className={view === 'grid' ? 'h-[180px] rounded-[22px]' : 'h-[66px] rounded-[20px]'} />)}
          </div>
        ) : error && !data ? (
          <EmptyState icon={CloudOff} title="Couldn't load files" body={error} action={<Button icon={RefreshCw} onClick={() => void reload()}>Try again</Button>} />
        ) : files.length === 0 ? (
          searching ? (
            <EmptyState icon={SearchX} title="Nothing found" body="Try another name or remove a filter." action={<Button variant="secondary" onClick={clearSearch}>Clear search</Button>} />
          ) : (
            <EmptyState
              icon={FolderOpen}
              title="No files here yet"
              body={desktop ? 'Drag files or folders onto this page, or upload them. They go to whichever drive has the most room.' : 'Upload photos, videos or documents. They go to whichever drive has the most room.'}
              action={<Button icon={Plus} onClick={() => actions.upload(folderId)}>Upload files</Button>}
            />
          )
        ) : view === 'grid' ? (
          <ul className="stagger grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
            {files.map((f) => (
              <li key={f.id}>
                <FileGridCell file={f} selected={selected.has(f.id)} selecting={selecting} onOpen={() => actions.open(f, files)} onToggle={() => toggle(f.id)} onMenu={(a) => openItemMenu(f, a)} draggable={desktop} />
              </li>
            ))}
          </ul>
        ) : (
          <ul className="stagger grid grid-cols-1 gap-2.5">
            {files.map((f) => (
              <li key={f.id}>
                <FileRow
                  file={f}
                  selected={selected.has(f.id)}
                  selecting={selecting}
                  subtitle={searching && f.folder ? `${formatBytes(f.sizeBytes)} · in ${f.folder.name}` : undefined}
                  onOpen={() => actions.open(f, files)}
                  onToggle={() => toggle(f.id)}
                  onMenu={(a) => openItemMenu(f, a)}
                  draggable={desktop}
                />
              </li>
            ))}
          </ul>
        )}
      </section>

      <FiltersDialog open={filtersOpen} onClose={() => setFiltersOpen(false)} params={params} onApply={(v) => setParam(v)} />
    </div>
  )
}

function SearchBox({ value, onChange, placeholder }: { value: string; onChange: (v: string) => void; placeholder: string }) {
  const [text, setText] = useState(value)
  useEffect(() => setText(value), [value])
  // Debounce typing into the URL so history isn't flooded.
  useEffect(() => {
    if (text === value) return
    const t = window.setTimeout(() => onChange(text.trim()), 300)
    return () => window.clearTimeout(t)
  }, [text, value, onChange])
  return (
    <form role="search" onSubmit={(e: FormEvent) => { e.preventDefault(); onChange(text.trim()) }} className="glass flex h-[54px] items-center rounded-[28px] pl-4 pr-1.5 focus-within:ring-2 focus-within:ring-accent/40 lg:hidden">
      <Search size={19} className="text-muted" aria-hidden />
      <label htmlFor="files-search" className="sr-only">Search files and folders</label>
      <input id="files-search" type="search" value={text} onChange={(e) => setText(e.target.value)} placeholder={placeholder} className="h-full min-w-0 flex-1 bg-transparent px-3 text-[15px] text-ink outline-none placeholder:text-subtle [&::-webkit-search-cancel-button]:hidden" />
      {text && <IconButton icon={X} label="Clear search" tone="plain" size={42} onClick={() => { setText(''); onChange('') }} />}
    </form>
  )
}

const CATEGORIES: { label: string; kind?: KindKey; to?: string; icon: typeof Share2; color: string }[] = [
  { label: 'Images', kind: 'image', icon: KINDS.image.icon, color: '#1e9e57' },
  { label: 'Videos', kind: 'video', icon: KINDS.video.icon, color: '#7a5cf0' },
  { label: 'PDFs', kind: 'pdf', icon: KINDS.pdf.icon, color: '#e8402b' },
  { label: 'Docs', kind: 'doc', icon: KINDS.doc.icon, color: '#2e64e0' },
  { label: 'Zips', kind: 'archive', icon: KINDS.archive.icon, color: '#a23fcf' },
  { label: 'Shared', to: '/shared', icon: Share2, color: '#1e8f50' },
  { label: 'Activity', to: '/activity', icon: History, color: '#b07800' },
  { label: 'Trash', to: '/trash', icon: Trash2, color: '#d9534f' },
]

function CategoryGrid({ counts, onKind, onRoute }: { counts: Record<KindKey, number>; onKind: (k: KindKey) => void; onRoute: (to: string) => void }) {
  return (
    <nav aria-label="Browse by type" className="mt-4 grid grid-cols-4 gap-2.5 lg:grid-cols-8">
      {CATEGORIES.map((c) => (
        <button
          key={c.label}
          type="button"
          onClick={() => (c.kind ? onKind(c.kind) : c.to && onRoute(c.to))}
          className="glass press flex h-[84px] flex-col items-center justify-center rounded-[22px] shadow-soft hover:bg-glass-strong"
        >
          <c.icon size={24} style={{ color: c.color } as CSSProperties} aria-hidden />
          <span className="mt-1 text-[13px] font-semibold text-ink">{c.label}</span>
          {c.kind && <span className="text-[11px] font-semibold text-muted">{plural(counts[c.kind], 'item')}</span>}
        </button>
      ))}
    </nav>
  )
}

function FiltersDialog({ open, onClose, params, onApply }: { open: boolean; onClose: () => void; params: URLSearchParams; onApply: (v: Record<string, string | null>) => void }) {
  return (
    <Dialog open={open} onClose={onClose} title="Filters" description="Narrow files down by drive, size and upload date.">
      <FiltersForm key={String(open)} params={params} onClose={onClose} onApply={onApply} />
    </Dialog>
  )
}

function FiltersForm({ params, onClose, onApply }: { params: URLSearchParams; onClose: () => void; onApply: (v: Record<string, string | null>) => void }) {
  const { data } = useLoad(() => api.accounts.list())
  const [v, setV] = useState({ account: params.get('account') ?? '', min: params.get('min') ?? '', max: params.get('max') ?? '', from: params.get('from') ?? '', to: params.get('to') ?? '' })
  const set = (k: keyof typeof v) => (e: { target: { value: string } }) => setV((s) => ({ ...s, [k]: e.target.value }))
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        onApply({ account: v.account || null, min: v.min || null, max: v.max || null, from: v.from || null, to: v.to || null })
        onClose()
      }}
      className="grid grid-cols-1 gap-4"
    >
      <Select label="Drive" value={v.account} onChange={set('account')}>
        <option value="">All drives</option>
        {data?.accounts.map((a) => <option key={a.id} value={a.id}>{a.displayName || a.email} · {providerLabel(a.provider)}</option>)}
      </Select>
      <div className="grid grid-cols-2 gap-3">
        <TextField label="Min size (MB)" showLabel type="number" min={0} inputMode="decimal" placeholder="0" value={v.min} onChange={set('min')} />
        <TextField label="Max size (MB)" showLabel type="number" min={0} inputMode="decimal" placeholder="Any" value={v.max} onChange={set('max')} />
      </div>
      <div className="grid grid-cols-2 gap-3">
        <TextField label="Uploaded from" showLabel type="date" value={v.from} onChange={set('from')} />
        <TextField label="Uploaded to" showLabel type="date" value={v.to} onChange={set('to')} />
      </div>
      <div className="flex flex-wrap justify-between gap-2.5 pt-1">
        <Button variant="ghost" onClick={() => { onApply({ account: null, min: null, max: null, from: null, to: null }); onClose() }}>Clear all</Button>
        <div className="flex gap-2.5">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit">Apply</Button>
        </div>
      </div>
    </form>
  )
}

