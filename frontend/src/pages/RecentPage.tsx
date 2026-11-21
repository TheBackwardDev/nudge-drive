import { useMemo } from 'react'
import { Clock, CloudOff, LayoutGrid, LayoutList, RefreshCw } from 'lucide-react'
import { Button, IconButton } from '@/components/ui/Button'
import { EmptyState, Shimmer } from '@/components/ui/Surface'
import { FileGridCell, FileRow } from '@/components/files/FileVisuals'
import { SelectionBar, useItemMenu, useSelection } from '@/components/files/Selection'
import { PageHeader } from '@/components/layout/PageHeader'
import { useFileActions } from '@/context/FileActions'
import { api } from '@/lib/api'
import { dayLabel, formatBytes, timeOnly } from '@/lib/format'
import { useIsDesktop, useLoad, useOnChange, usePref } from '@/lib/hooks'
import type { FileItem } from '@/lib/types'

const LIMIT = 150

/** The latest uploads across every drive and folder, grouped by day. */
export default function RecentPage() {
  const actions = useFileActions()
  const selection = useSelection()
  const menu = useItemMenu()
  const desktop = useIsDesktop()
  const [view, setView] = usePref('nudge:files-view', 'list', ['list', 'grid'] as const)
  const { data, error, loading, reload } = useLoad(() => api.files.list())
  useOnChange(['files'], reload)

  const files = useMemo(() => [...(data?.files ?? [])].sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, LIMIT), [data])
  const days = useMemo(() => {
    const out: { label: string; files: FileItem[] }[] = []
    for (const f of files) {
      const label = dayLabel(f.createdAt)
      const last = out[out.length - 1]
      if (last?.label === label) last.files.push(f)
      else out.push({ label, files: [f] })
    }
    return out
  }, [files])

  const item = (f: FileItem) => {
    const common = {
      file: f,
      selected: selection.selected.has(f.id),
      selecting: selection.selecting,
      onOpen: () => actions.open(f, files),
      onToggle: () => selection.toggle(f.id),
      onMenu: (a: Parameters<typeof menu.show>[2]) => menu.show(f.name, actions.fileMenu(f, files), a),
      draggable: desktop,
    }
    return view === 'grid' ? <FileGridCell {...common} /> : <FileRow {...common} subtitle={`${formatBytes(f.sizeBytes)} · ${f.folder?.name ?? 'My Files'} · ${timeOnly(f.createdAt)}`} />
  }

  return (
    <div>
      {selection.selecting ? (
        <SelectionBar files={files} selection={selection} />
      ) : (
        <PageHeader
          title="Recent"
          subtitle="Newest uploads across all your drives"
          back="/files"
          actions={<IconButton icon={view === 'grid' ? LayoutList : LayoutGrid} label={view === 'grid' ? 'List view' : 'Grid view'} onClick={() => setView(view === 'grid' ? 'list' : 'grid')} />}
        />
      )}
      {menu.element}
      {loading && !data ? (
        <div className="grid grid-cols-1 gap-2.5">{Array.from({ length: 6 }, (_, i) => <Shimmer key={i} className="h-[66px] rounded-[20px]" />)}</div>
      ) : error && !data ? (
        <EmptyState icon={CloudOff} title="Couldn't load recent files" body={error} action={<Button icon={RefreshCw} onClick={() => void reload()}>Try again</Button>} />
      ) : files.length === 0 ? (
        <EmptyState icon={Clock} title="Nothing recent" body="Files you upload show up here first." action={<Button onClick={() => actions.upload(null)}>Upload files</Button>} />
      ) : (
        <div className="grid grid-cols-1 gap-6">
          {days.map((d) => (
            <section key={d.label} aria-label={d.label}>
              <h2 className="mb-2.5 text-[17px] font-semibold text-ink">{d.label}</h2>
              <ul className={view === 'grid' ? 'stagger grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5' : 'stagger grid grid-cols-1 gap-2.5'}>
                {d.files.map((f) => <li key={f.id}>{item(f)}</li>)}
              </ul>
            </section>
          ))}
        </div>
      )}
    </div>
  )
}
