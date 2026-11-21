import { useMemo } from 'react'
import { ArrowDownWideNarrow, ArrowUpNarrowWide, CloudOff, ImagePlus, RefreshCw } from 'lucide-react'
import { Button, IconButton } from '@/components/ui/Button'
import { EmptyState, Shimmer } from '@/components/ui/Surface'
import { PhotoTile } from '@/components/files/FileVisuals'
import { SelectionBar, useSelection } from '@/components/files/Selection'
import { PageHeader } from '@/components/layout/PageHeader'
import { useFileActions } from '@/context/FileActions'
import { api } from '@/lib/api'
import { isMedia, photoDate } from '@/lib/files'
import { monthLabel, parseDate, plural } from '@/lib/format'
import { useLoad, useOnChange, usePref } from '@/lib/hooks'
import type { FileItem } from '@/lib/types'

/** Google Photos-style timeline of every photo and video, grouped by month taken. */
export default function PhotosPage() {
  const actions = useFileActions()
  const selection = useSelection()
  const [order, setOrder] = usePref('nudge:photos-order', 'newest', ['newest', 'oldest'] as const)
  const { data, error, loading, reload } = useLoad(() => api.files.list())
  useOnChange(['files'], reload)

  const media = useMemo(() => {
    const list = (data?.files ?? []).filter(isMedia)
    list.sort((a, b) => photoDate(b).localeCompare(photoDate(a)))
    if (order === 'oldest') list.reverse()
    return list
  }, [data, order])

  const months = useMemo(() => {
    const groups: { label: string; files: FileItem[] }[] = []
    for (const f of media) {
      const d = parseDate(photoDate(f))
      const label = d ? monthLabel(d) : 'Undated'
      const last = groups[groups.length - 1]
      if (last?.label === label) last.files.push(f)
      else groups.push({ label, files: [f] })
    }
    return groups
  }, [media])

  return (
    <div>
      {selection.selecting ? (
        <SelectionBar files={media} selection={selection} />
      ) : (
        <PageHeader
          title="Photos"
          subtitle={data ? `${plural(media.length, 'photo or video', 'photos and videos')}` : undefined}
          back="/files"
          actions={
            <>
              <IconButton icon={order === 'newest' ? ArrowDownWideNarrow : ArrowUpNarrowWide} label={order === 'newest' ? 'Showing newest first. Show oldest first' : 'Showing oldest first. Show newest first'} onClick={() => setOrder(order === 'newest' ? 'oldest' : 'newest')} />
              <IconButton icon={ImagePlus} label="Upload photos" tone="primary" onClick={() => actions.upload(null)} />
            </>
          }
        />
      )}
      {loading && !data ? (
        <div className="grid grid-cols-3 gap-1 sm:grid-cols-5 lg:grid-cols-7">{Array.from({ length: 15 }, (_, i) => <Shimmer key={i} className="aspect-square rounded-[10px]" />)}</div>
      ) : error && !data ? (
        <EmptyState icon={CloudOff} title="Couldn't load photos" body={error} action={<Button icon={RefreshCw} onClick={() => void reload()}>Try again</Button>} />
      ) : media.length === 0 ? (
        <EmptyState icon={ImagePlus} title="No photos yet" body="Photos and videos you upload show up here, sorted by the month they were taken." action={<Button icon={ImagePlus} onClick={() => actions.upload(null)}>Upload photos</Button>} />
      ) : (
        <div className="grid grid-cols-1 gap-6">
          {months.map((m) => (
            <section key={m.label} aria-label={m.label}>
              <h2 className="sticky top-0 z-10 -mx-1 mb-2 px-1 py-1.5 text-[17px] font-semibold text-ink backdrop-blur-md lg:top-16">
                {m.label} <span className="text-[13px] font-semibold text-muted">· {m.files.length}</span>
              </h2>
              <ul className="stagger grid grid-cols-3 gap-1 sm:grid-cols-5 lg:grid-cols-6 xl:grid-cols-7">
                {m.files.map((f) => (
                  <li key={f.id}>
                    <PhotoTile file={f} selected={selection.selected.has(f.id)} selecting={selection.selecting} onOpen={() => actions.open(f, media)} onToggle={() => selection.toggle(f.id)} />
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>
      )}
    </div>
  )
}
