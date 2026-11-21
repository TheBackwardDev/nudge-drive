import { useMemo, useState, type CSSProperties, type FormEvent } from 'react'
import clsx from 'clsx'
import { Check, ChevronRight, FolderPlus, House } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Dialog } from '@/components/ui/Dialog'
import { TextField } from '@/components/ui/Field'
import { FolderGlyph } from '@/components/files/FileVisuals'
import { DEFAULT_FOLDER_ICON, FOLDER_PALETTE, folderColor, folderPath, sortFolders } from '@/lib/files'
import { FOLDER_ICON_MAP, iconUrlFor } from '@/lib/folderIcons'
import { plural } from '@/lib/format'
import type { Folder } from '@/lib/types'

export type FolderLook = { name: string; color: string; iconUrl: string }

/** Create or edit a folder: name, colour and icon. */
export function FolderDialog({ open, onClose, initial, onSubmit, title }: { open: boolean; onClose: () => void; initial?: Folder | null; title: string; onSubmit: (v: FolderLook) => Promise<void> }) {
  return (
    <Dialog open={open} onClose={onClose} title={title} size="md">
      <FolderForm key={initial?.id ?? 'new'} initial={initial} onCancel={onClose} onSubmit={onSubmit} confirm={initial ? 'Save' : 'Create'} />
    </Dialog>
  )
}

function FolderForm({ initial, onCancel, onSubmit, confirm }: { initial?: Folder | null; onCancel: () => void; onSubmit: (v: FolderLook) => Promise<void>; confirm: string }) {
  const [name, setName] = useState(initial?.name ?? '')
  const [color, setColor] = useState(initial ? folderColor(initial.color) : FOLDER_PALETTE[0])
  const [iconUrl, setIconUrl] = useState(initial?.iconUrl || DEFAULT_FOLDER_ICON)
  const [busy, setBusy] = useState(false)
  const palette = FOLDER_PALETTE.includes(color) ? FOLDER_PALETTE : [...FOLDER_PALETTE, color]

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!name.trim()) return
    setBusy(true)
    try {
      await onSubmit({ name: name.trim(), color, iconUrl })
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="grid grid-cols-1 gap-5">
      <TextField label="Folder name" placeholder="Folder name" value={name} onChange={(e) => setName(e.target.value)} autoFocus required maxLength={255} />
      <fieldset>
        <legend className="mb-2 text-[13px] font-semibold text-muted">Colour</legend>
        <div className="flex flex-wrap gap-2.5">
          {palette.map((c) => (
            <button
              key={c}
              type="button"
              onClick={() => setColor(c)}
              aria-label={`Colour ${c}`}
              aria-pressed={color === c}
              className="press flex h-10 w-10 items-center justify-center rounded-full text-white ring-offset-2 ring-offset-sheet aria-pressed:ring-2 aria-pressed:ring-ink"
              style={{ background: c }}
            >
              {color === c && <Check size={18} strokeWidth={3} aria-hidden />}
            </button>
          ))}
        </div>
      </fieldset>
      <fieldset>
        <legend className="mb-2 text-[13px] font-semibold text-muted">Icon</legend>
        <div className="grid grid-cols-6 gap-2 sm:grid-cols-8">
          {Object.entries(FOLDER_ICON_MAP).map(([name, Icon]) => {
            const url = iconUrlFor(name)
            const on = iconUrl.replace('/lucide/', '/lucide:') === url
            return (
              <button
                key={name}
                type="button"
                onClick={() => setIconUrl(url)}
                aria-label={`Icon ${name.replace(/-/g, ' ')}`}
                aria-pressed={on}
                className={clsx('press flex aspect-square items-center justify-center rounded-[14px] border', on ? 'border-iris bg-mist' : 'border-line bg-field-solid hover:border-lilac')}
              >
                <Icon size={21} color={color} aria-hidden />
              </button>
            )
          })}
        </div>
      </fieldset>
      <div className="flex justify-end gap-2.5">
        <Button variant="secondary" onClick={onCancel} disabled={busy}>Cancel</Button>
        <Button type="submit" loading={busy} disabled={!name.trim()}>{confirm}</Button>
      </div>
    </form>
  )
}

type PickerProps = {
  open: boolean
  onClose: () => void
  title: string
  folders: Folder[]
  /** Folders that can't be chosen (a folder and its children when moving a folder). */
  exclude?: Set<string>
  startAt?: string | null
  confirm?: string
  onPick: (folderId: string | null) => Promise<void> | void
  onNewFolder?: (parentId: string | null) => void
}

/** Browse the folder tree and pick a destination (FolderPickerSheet). */
export function FolderPicker(props: PickerProps) {
  return (
    <Dialog open={props.open} onClose={props.onClose} title={props.title} size="md">
      <PickerBody key={String(props.open)} {...props} />
    </Dialog>
  )
}

function PickerBody({ folders, exclude, startAt = null, confirm = 'Move here', onPick, onClose, onNewFolder }: PickerProps) {
  const [current, setCurrent] = useState<string | null>(startAt)
  const [busy, setBusy] = useState(false)
  const path = useMemo(() => folderPath(folders, current), [folders, current])
  const children = useMemo(() => sortFolders(folders.filter((f) => f.parentId === current), 'name'), [folders, current])
  const countIn = (id: string) => folders.filter((f) => f.parentId === id).length

  async function pick() {
    setBusy(true)
    try {
      await onPick(current)
      onClose()
    } catch {
      /* caller shows the error */
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="grid grid-cols-1 gap-4">
      <nav aria-label="Folder path" className="no-scrollbar flex items-center gap-0.5 overflow-x-auto text-[13px] font-semibold">
        <button type="button" onClick={() => setCurrent(null)} className={clsx('press flex shrink-0 items-center gap-1.5 rounded-xl px-2 py-1.5', current === null ? 'text-ink' : 'text-link hover:bg-hover')}>
          <House size={15} aria-hidden />My Files
        </button>
        {path.map((f, i) => (
          <span key={f.id} className="flex shrink-0 items-center">
            <ChevronRight size={15} className="text-subtle" aria-hidden />
            <button type="button" onClick={() => setCurrent(f.id)} aria-current={i === path.length - 1 ? 'location' : undefined} className={clsx('press rounded-xl px-2 py-1.5', i === path.length - 1 ? 'text-ink' : 'text-link hover:bg-hover')}>{f.name}</button>
          </span>
        ))}
      </nav>
      <ul className="grid max-h-[46dvh] gap-1.5 overflow-y-auto">
        {children.length === 0 && <li className="rounded-[18px] bg-mist px-4 py-5 text-center text-[13.5px] text-muted">No folders here. You can move items into this folder.</li>}
        {children.map((f) => {
          const blocked = exclude?.has(f.id)
          const n = countIn(f.id)
          return (
            <li key={f.id}>
              <button
                type="button"
                disabled={blocked}
                onClick={() => setCurrent(f.id)}
                className="press flex h-14 w-full items-center gap-3 rounded-[18px] border border-card-border bg-surface px-3 text-left hover:border-lilac disabled:opacity-40"
              >
                <span className="soft-tile flex h-9 w-9 items-center justify-center rounded-[12px]" style={{ '--k': folderColor(f.color) } as CSSProperties}><FolderGlyph folder={f} size={19} /></span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-[14px] font-semibold text-ink">{f.name}</span>
                  {n > 0 && <span className="block text-[12px] text-muted">{plural(n, 'folder')}</span>}
                </span>
                {!blocked && <ChevronRight size={18} className="text-subtle" aria-hidden />}
              </button>
            </li>
          )
        })}
      </ul>
      <div className="flex flex-wrap items-center justify-between gap-2.5">
        {onNewFolder ? <Button variant="ghost" icon={FolderPlus} onClick={() => onNewFolder(current)}>New folder</Button> : <span />}
        <div className="flex gap-2.5">
          <Button variant="secondary" onClick={onClose} disabled={busy}>Cancel</Button>
          <Button onClick={pick} loading={busy} disabled={current !== null && exclude?.has(current)}>{confirm}</Button>
        </div>
      </div>
    </div>
  )
}

/** One-line text prompt (rename, new document). */
export function TextPrompt({ open, onClose, title, label, initial = '', confirm, onSubmit, selectBase, requireChange }: { open: boolean; onClose: () => void; title: string; label: string; initial?: string; confirm: string; onSubmit: (v: string) => Promise<void>; selectBase?: boolean; requireChange?: boolean }) {
  return (
    <Dialog open={open} onClose={onClose} title={title} size="sm">
      <PromptBody key={initial} label={label} initial={initial} confirm={confirm} onSubmit={onSubmit} onCancel={onClose} selectBase={selectBase} requireChange={requireChange} />
    </Dialog>
  )
}

function PromptBody({ label, initial, confirm, onSubmit, onCancel, selectBase, requireChange }: { label: string; initial: string; confirm: string; onSubmit: (v: string) => Promise<void>; onCancel: () => void; selectBase?: boolean; requireChange?: boolean }) {
  const [value, setValue] = useState(initial)
  const [busy, setBusy] = useState(false)
  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!value.trim()) return
    setBusy(true)
    try {
      await onSubmit(value.trim())
    } finally {
      setBusy(false)
    }
  }
  return (
    <form onSubmit={submit} className="grid grid-cols-1 gap-5">
      <TextField
        label={label}
        placeholder={label}
        value={value}
        onChange={(e) => setValue(e.target.value)}
        autoFocus
        required
        maxLength={255}
        onFocus={(e) => {
          // Select the name without its extension, like a file manager.
          const dot = initial.lastIndexOf('.')
          if (selectBase && dot > 0) e.currentTarget.setSelectionRange(0, dot)
          else e.currentTarget.select()
        }}
      />
      <div className="flex justify-end gap-2.5">
        <Button variant="secondary" onClick={onCancel} disabled={busy}>Cancel</Button>
        <Button type="submit" loading={busy} disabled={!value.trim() || (requireChange && value.trim() === initial)}>{confirm}</Button>
      </div>
    </form>
  )
}
