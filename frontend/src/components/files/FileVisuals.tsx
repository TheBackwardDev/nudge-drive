import { useRef, useState, type CSSProperties, type DragEvent, type MouseEvent, type PointerEvent, type ReactNode } from 'react'
import clsx from 'clsx'
import { Check, EllipsisVertical, Folder as FolderIcon, Play } from 'lucide-react'
import { badge, fileKind, folderColor, hasThumbnail, viewerType } from '@/lib/files'
import { folderIconFor } from '@/lib/folderIcons'
import { formatBytes, friendlyDate } from '@/lib/format'
import { useThumbnail } from '@/lib/thumbs'
import type { FileItem, Folder } from '@/lib/types'

/** Coloured type tile; photos, videos and documents show their server thumbnail on top. */
export function FileThumb({ file, size = 44, radius = 14, fill, thumb = true, className }: { file: FileItem; size?: number; radius?: number; fill?: boolean; thumb?: boolean; className?: string }) {
  const k = fileKind(file)
  const big = fill || size >= 64
  const vt = viewerType(file)
  const want = thumb && hasThumbnail(file) && (big || vt === 'image' || vt === 'video')
  const { ref, url } = useThumbnail(want ? file.id : null, big ? 400 : 200)
  const Icon = k.icon
  const [loaded, setLoaded] = useState<string | null>(null)
  return (
    <span
      ref={ref}
      className={clsx('soft-tile relative inline-flex shrink-0 items-center justify-center overflow-hidden', className)}
      style={{ '--k': k.color, borderRadius: radius, width: fill ? '100%' : size, height: fill ? '100%' : size } as CSSProperties}
      aria-hidden
    >
      {big ? <Icon size={fill ? 40 : Math.round(size * 0.42)} /> : <span className="font-display font-extrabold" style={{ fontSize: Math.max(9, Math.round(size * 0.24)) }}>{badge(file)}</span>}
      {url && <img src={url} alt="" loading="lazy" decoding="async" onLoad={() => setLoaded(url)} className={clsx('absolute inset-0 h-full w-full object-cover transition-opacity duration-200', loaded === url ? 'opacity-100' : 'opacity-0')} draggable={false} />}
      {url && vt === 'video' && (
        <span className="absolute flex items-center justify-center rounded-full bg-black/45 text-white" style={{ width: big ? 34 : 18, height: big ? 34 : 18 }}>
          <Play size={big ? 15 : 9} fill="currentColor" />
        </span>
      )}
    </span>
  )
}

/** Long-press (phones) → callback; returns pointer handlers. Suppresses the click that follows. */
function useLongPress(onLong?: () => void) {
  const timer = useRef(0)
  const fired = useRef(false)
  const start = useRef({ x: 0, y: 0 })
  return {
    fired,
    handlers: onLong
      ? {
          onPointerDown: (e: PointerEvent) => {
            if (e.pointerType !== 'touch') return
            fired.current = false
            start.current = { x: e.clientX, y: e.clientY }
            timer.current = window.setTimeout(() => {
              fired.current = true
              navigator.vibrate?.(10)
              onLong()
            }, 450)
          },
          onPointerMove: (e: PointerEvent) => {
            if (Math.abs(e.clientX - start.current.x) > 8 || Math.abs(e.clientY - start.current.y) > 8) window.clearTimeout(timer.current)
          },
          onPointerUp: () => window.clearTimeout(timer.current),
          onPointerCancel: () => window.clearTimeout(timer.current),
        }
      : {},
  }
}

type ItemProps = {
  file: FileItem
  selected?: boolean
  selecting?: boolean
  subtitle?: ReactNode
  onOpen: () => void
  onToggle?: () => void
  onMenu?: (anchor: HTMLElement | { x: number; y: number }) => void
  draggable?: boolean
}

function SelectBox({ checked, onToggle, label, className }: { checked: boolean; onToggle: () => void; label: string; className?: string }) {
  return (
    <button
      type="button"
      role="checkbox"
      aria-checked={checked}
      aria-label={label}
      onClick={(e) => {
        e.stopPropagation()
        onToggle()
      }}
      className={clsx(
        'press flex h-6 w-6 shrink-0 items-center justify-center rounded-full border-2',
        checked ? 'border-iris bg-iris text-white' : 'border-subtle/60 bg-surface text-transparent hover:border-iris',
        className,
      )}
    >
      <Check size={14} strokeWidth={3} aria-hidden />
    </button>
  )
}

function dragProps(file: FileItem, enabled?: boolean) {
  if (!enabled) return {}
  return {
    draggable: true,
    onDragStart: (e: DragEvent) => {
      e.dataTransfer.setData('application/x-nudge-file', file.id)
      e.dataTransfer.effectAllowed = 'move'
    },
  }
}

export function FileRow({ file, selected, selecting, subtitle, onOpen, onToggle, onMenu, draggable }: ItemProps) {
  const long = useLongPress(onToggle)
  return (
    <div
      {...dragProps(file, draggable)}
      onContextMenu={onMenu ? (e: MouseEvent) => { e.preventDefault(); onMenu({ x: e.clientX, y: e.clientY }) } : undefined}
      className={clsx(
        'group press press-row flex h-[66px] items-center gap-3 rounded-[20px] border pl-2.5 pr-1 shadow-[0_8px_20px_-16px_var(--shadow-color)]',
        selected ? 'border-lavender bg-mist' : 'border-card-border bg-surface hover:border-lilac',
      )}
    >
      {onToggle && <SelectBox checked={!!selected} onToggle={onToggle} label={`Select ${file.name}`} className={clsx(!selecting && !selected && 'max-lg:hidden lg:opacity-0 lg:group-hover:opacity-100 lg:focus-visible:opacity-100')} />}
      <button
        type="button"
        {...long.handlers}
        onClick={() => {
          if (long.fired.current) { long.fired.current = false; return }
          if (selecting && onToggle) onToggle()
          else onOpen()
        }}
        className="flex h-full min-w-0 flex-1 items-center gap-3 text-left outline-none focus-visible:rounded-[16px] focus-visible:ring-2 focus-visible:ring-accent"
      >
        <FileThumb file={file} />
        <span className="min-w-0 flex-1">
          <span className="block truncate text-[14px] font-semibold text-ink">{file.name}</span>
          <span className="block truncate text-[12px] text-muted">
            {subtitle ?? `${formatBytes(file.sizeBytes)} · ${file.connectedAccount?.email?.split('@')[0] ?? 'drive'} · ${friendlyDate(file.createdAt)}`}
          </span>
        </span>
      </button>
      {onMenu && !selecting && (
        <button type="button" aria-label={`More actions for ${file.name}`} onClick={(e) => onMenu(e.currentTarget)} className="press flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-ink hover:bg-mist">
          <EllipsisVertical size={20} aria-hidden />
        </button>
      )}
    </div>
  )
}

export function FileGridCell({ file, selected, selecting, onOpen, onToggle, onMenu, draggable }: ItemProps) {
  const long = useLongPress(onToggle)
  return (
    <div
      {...dragProps(file, draggable)}
      onContextMenu={onMenu ? (e: MouseEvent) => { e.preventDefault(); onMenu({ x: e.clientX, y: e.clientY }) } : undefined}
      className={clsx(
        'group press press-cell relative flex flex-col rounded-[22px] border p-2 shadow-[0_8px_20px_-16px_var(--shadow-color)]',
        selected ? 'border-lavender bg-mist' : 'border-card-border bg-surface hover:border-lilac',
      )}
    >
      <button
        type="button"
        {...long.handlers}
        onClick={() => {
          if (long.fired.current) { long.fired.current = false; return }
          if (selecting && onToggle) onToggle()
          else onOpen()
        }}
        className="flex min-w-0 flex-col text-left outline-none focus-visible:rounded-[16px] focus-visible:ring-2 focus-visible:ring-accent"
      >
        <span className="block aspect-[1.15] w-full"><FileThumb file={file} fill radius={16} /></span>
        <span className="mt-2 block truncate px-1 text-[13px] font-semibold text-ink">{file.name}</span>
        <span className="block px-1 pb-0.5 text-[11px] text-muted">{formatBytes(file.sizeBytes)}</span>
      </button>
      {onToggle && <SelectBox checked={!!selected} onToggle={onToggle} label={`Select ${file.name}`} className={clsx('absolute left-3.5 top-3.5', !selecting && !selected && 'max-lg:hidden lg:opacity-0 lg:group-hover:opacity-100 lg:focus-visible:opacity-100')} />}
      {onMenu && !selecting && (
        <button type="button" aria-label={`More actions for ${file.name}`} onClick={(e) => onMenu(e.currentTarget)} className="press absolute right-3 top-3 flex h-9 w-9 items-center justify-center rounded-full bg-surface/85 text-ink shadow-soft backdrop-blur hover:bg-surface lg:opacity-0 lg:group-hover:opacity-100 lg:focus-visible:opacity-100">
          <EllipsisVertical size={18} aria-hidden />
        </button>
      )}
    </div>
  )
}

/** Square photo tile for the Photos timeline. */
export function PhotoTile({ file, selected, selecting, onOpen, onToggle }: ItemProps) {
  const long = useLongPress(onToggle)
  return (
    <div className="group relative aspect-square">
      <button
        type="button"
        {...long.handlers}
        onClick={() => {
          if (long.fired.current) { long.fired.current = false; return }
          if (selecting && onToggle) onToggle()
          else onOpen()
        }}
        aria-label={file.name}
        className={clsx('press block h-full w-full overflow-hidden rounded-[10px] outline-none focus-visible:ring-2 focus-visible:ring-accent', selected && 'ring-4 ring-iris ring-inset')}
      >
        <FileThumb file={file} fill radius={10} />
      </button>
      {onToggle && <SelectBox checked={!!selected} onToggle={onToggle} label={`Select ${file.name}`} className={clsx('absolute left-2 top-2', !selecting && !selected && 'opacity-0 group-hover:opacity-100 focus-visible:opacity-100')} />}
    </div>
  )
}

/** Folder glyph: the folder's chosen icon in its colour (a lightly filled folder by default). */
export function FolderGlyph({ folder, size = 22 }: { folder: Folder; size?: number }) {
  const color = folderColor(folder.color)
  const Icon = folderIconFor(folder.iconUrl)
  const plain = Icon === FolderIcon
  return <Icon size={size} color={color} fill={plain ? color : 'none'} fillOpacity={plain ? 0.25 : undefined} aria-hidden />
}

function useDropTarget(onDropFile?: (fileId: string) => void) {
  const [over, setOver] = useState(false)
  if (!onDropFile) return { over: false, props: {} }
  return {
    over,
    props: {
      onDragOver: (e: DragEvent) => {
        if (!e.dataTransfer.types.includes('application/x-nudge-file')) return
        e.preventDefault()
        e.dataTransfer.dropEffect = 'move'
        setOver(true)
      },
      onDragLeave: () => setOver(false),
      onDrop: (e: DragEvent) => {
        const id = e.dataTransfer.getData('application/x-nudge-file')
        setOver(false)
        if (id) {
          e.preventDefault()
          e.stopPropagation()
          onDropFile(id)
        }
      },
    },
  }
}

type FolderProps = {
  folder: Folder
  subtitle?: string
  onOpen: () => void
  onMenu?: (anchor: HTMLElement | { x: number; y: number }) => void
  onDropFile?: (fileId: string) => void
  className?: string
}

/** Folder-shaped card (tab + body) tinted with the folder's colour. */
export function FolderCard({ folder, subtitle, onOpen, onMenu, onDropFile, className }: FolderProps) {
  const color = folderColor(folder.color)
  const tint = { '--fc': color } as CSSProperties
  const long = useLongPress(onMenu ? () => onMenu({ x: window.innerWidth / 2, y: window.innerHeight / 2 }) : undefined)
  const drop = useDropTarget(onDropFile)
  return (
    <div
      {...drop.props}
      onContextMenu={onMenu ? (e: MouseEvent) => { e.preventDefault(); onMenu({ x: e.clientX, y: e.clientY }) } : undefined}
      className={clsx('group relative h-[118px] w-[124px] shrink-0', className)}
      style={tint}
    >
      <span aria-hidden className="absolute left-0 top-0 h-[22px] w-[54px] rounded-t-[12px] bg-[color-mix(in_srgb,var(--fc)_22%,var(--surface))] dark:bg-[color-mix(in_srgb,var(--fc)_28%,var(--surface))]" />
      <button
        type="button"
        {...long.handlers}
        onClick={() => {
          if (long.fired.current) { long.fired.current = false; return }
          onOpen()
        }}
        className={clsx(
          'press absolute inset-x-0 bottom-0 top-3 flex flex-col justify-between rounded-[0_20px_20px_20px] p-3 text-left shadow-[0_10px_24px_-16px_var(--shadow-color)] outline-none focus-visible:ring-2 focus-visible:ring-accent',
          'bg-[color-mix(in_srgb,var(--fc)_22%,var(--surface))] dark:bg-[color-mix(in_srgb,var(--fc)_28%,var(--surface))]',
          drop.over && 'ring-2 ring-iris',
        )}
      >
        <FolderGlyph folder={folder} />
        <span className="min-w-0">
          <span className="block truncate font-display text-[15px] font-bold text-ink">{folder.name}</span>
          {subtitle && <span className="block truncate text-[11px] font-semibold text-muted">{subtitle}</span>}
        </span>
      </button>
      {onMenu && (
        <button type="button" aria-label={`More actions for folder ${folder.name}`} onClick={(e) => onMenu(e.currentTarget)} className="press absolute right-1.5 top-4 flex h-8 w-8 items-center justify-center rounded-full text-ink hover:bg-white/40 dark:hover:bg-white/10 lg:opacity-0 lg:group-hover:opacity-100 lg:focus-visible:opacity-100">
          <EllipsisVertical size={17} aria-hidden />
        </button>
      )}
    </div>
  )
}

/** Full-width folder row used in lists and pickers. */
export function FolderRow({ folder, subtitle, onOpen, onMenu, onDropFile, trailing }: FolderProps & { trailing?: ReactNode }) {
  const color = folderColor(folder.color)
  const drop = useDropTarget(onDropFile)
  return (
    <div
      {...drop.props}
      onContextMenu={onMenu ? (e: MouseEvent) => { e.preventDefault(); onMenu({ x: e.clientX, y: e.clientY }) } : undefined}
      className={clsx('press press-row flex h-[60px] items-center gap-3 rounded-[18px] border bg-surface pl-3 pr-1', drop.over ? 'border-iris' : 'border-card-border hover:border-lilac')}
    >
      <button type="button" onClick={onOpen} className="flex h-full min-w-0 flex-1 items-center gap-3 text-left outline-none focus-visible:rounded-[14px] focus-visible:ring-2 focus-visible:ring-accent">
        <span className="soft-tile flex h-[38px] w-[38px] shrink-0 items-center justify-center rounded-[12px]" style={{ '--k': color } as CSSProperties}>
          <FolderGlyph folder={folder} size={20} />
        </span>
        <span className="min-w-0 flex-1">
          <span className="block truncate text-[14px] font-semibold text-ink">{folder.name}</span>
          {subtitle && <span className="block truncate text-[12px] text-muted">{subtitle}</span>}
        </span>
      </button>
      {trailing}
      {onMenu && (
        <button type="button" aria-label={`More actions for folder ${folder.name}`} onClick={(e) => onMenu(e.currentTarget)} className="press flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-ink hover:bg-mist">
          <EllipsisVertical size={20} aria-hidden />
        </button>
      )}
    </div>
  )
}
