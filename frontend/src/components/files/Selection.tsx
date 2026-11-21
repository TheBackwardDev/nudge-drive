import { useCallback, useState } from 'react'
import { Download, FolderInput, ListChecks, Share2, Trash2, X } from 'lucide-react'
import { IconButton } from '@/components/ui/Button'
import { Menu, useMenu, type MenuAnchor, type MenuItem } from '@/components/ui/Menu'
import { useFileActions } from '@/context/FileActions'
import type { FileItem } from '@/lib/types'

/** Multi-select state for a list of files. */
export function useSelection() {
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const toggle = useCallback((id: string) => setSelected((s) => {
    const n = new Set(s)
    if (n.has(id)) n.delete(id)
    else n.add(id)
    return n
  }), [])
  const clear = useCallback(() => setSelected(new Set()), [])
  return { selected, selecting: selected.size > 0, toggle, clear, setAll: (ids: string[]) => setSelected(new Set(ids)) }
}

/** Dark floating bar shown while files are selected (SelectionBar). */
export function SelectionBar({ files, selection }: { files: FileItem[]; selection: ReturnType<typeof useSelection> }) {
  const actions = useFileActions()
  const chosen = files.filter((f) => selection.selected.has(f.id))
  return (
    <div className="sticky top-[max(12px,env(safe-area-inset-top))] z-20 mb-4 mt-2.5 flex items-center gap-1 rounded-[26px] bg-inverse px-2 py-1.5 text-on-inverse shadow-lift lg:top-20">
      <IconButton icon={X} label="Clear selection" tone="inverse" onClick={selection.clear} />
      <p className="flex-1 pl-1 font-display text-[16px] font-semibold" aria-live="polite">{selection.selected.size} selected</p>
      <IconButton icon={ListChecks} label="Select all" tone="inverse" onClick={() => selection.setAll(files.map((f) => f.id))} />
      <IconButton icon={Download} label="Download as zip" tone="inverse" onClick={() => void actions.downloadZip(chosen)} />
      <IconButton icon={FolderInput} label="Move" tone="inverse" onClick={() => actions.move(chosen, selection.clear)} />
      {chosen.length === 1 && <IconButton icon={Share2} label="Share link" tone="inverse" onClick={() => actions.share(chosen[0])} />}
      <IconButton icon={Trash2} label="Move to trash" tone="inverse" onClick={() => void actions.trash(chosen, selection.clear)} />
    </div>
  )
}

/** One shared options menu per screen, opened for whichever item asked. */
export function useItemMenu() {
  const menu = useMenu()
  const [content, setContent] = useState<{ title: string; items: MenuItem[] }>({ title: '', items: [] })
  return {
    show: (title: string, items: MenuItem[], anchor: MenuAnchor) => {
      setContent({ title, items })
      menu.openAt(anchor)
    },
    element: <Menu open={menu.open} anchor={menu.anchor} onClose={menu.close} title={content.title} items={content.items} />,
  }
}
