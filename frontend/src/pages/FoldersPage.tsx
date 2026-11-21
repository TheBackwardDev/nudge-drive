import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { FolderPlus, Folders, Search, X } from 'lucide-react'
import { Button, IconButton } from '@/components/ui/Button'
import { Chip, EmptyState } from '@/components/ui/Surface'
import { FolderRow } from '@/components/files/FileVisuals'
import { useItemMenu } from '@/components/files/Selection'
import { PageHeader } from '@/components/layout/PageHeader'
import { useFileActions } from '@/context/FileActions'
import { api } from '@/lib/api'
import { folderPath, sortFolders } from '@/lib/files'
import type { Folder } from '@/lib/types'
import { plural, shortDate } from '@/lib/format'
import { useLoad, useOnChange, usePref } from '@/lib/hooks'

type FolderSort = 'name' | 'za' | 'created' | 'oldest' | 'newest'
const SORTS: { value: FolderSort; label: string }[] = [
  { value: 'name', label: 'Name A–Z' },
  { value: 'za', label: 'Name Z–A' },
  { value: 'created', label: 'Newest' },
  { value: 'oldest', label: 'Oldest' },
  { value: 'newest', label: 'Recently changed' },
]

function sortBy(list: Folder[], mode: FolderSort) {
  if (mode === 'za') return sortFolders(list, 'name').reverse()
  if (mode === 'created') return [...list].sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  return sortFolders(list, mode)
}

/** Every folder at every depth, with its path, so deep folders are one tap away. */
export default function FoldersPage() {
  const navigate = useNavigate()
  const actions = useFileActions()
  const itemMenu = useItemMenu()
  const [sort, setSort] = usePref<FolderSort>('nudge:folders-sort', 'name', ['name', 'za', 'created', 'oldest', 'newest'])
  const [q, setQ] = useState('')
  const { data, reload } = useLoad(() => api.files.list())
  useOnChange(['files'], reload)

  const counts = useMemo(() => {
    const m = new Map<string, number>()
    for (const f of data?.files ?? []) if (f.folderId) m.set(f.folderId, (m.get(f.folderId) ?? 0) + 1)
    return m
  }, [data])

  const list = useMemo(() => {
    const term = q.trim().toLowerCase()
    return sortBy(actions.folders.filter((f) => !term || f.name.toLowerCase().includes(term)), sort)
  }, [actions.folders, q, sort])

  const open = (id: string) => navigate(`/files?folder=${id}`)

  return (
    <div>
      <PageHeader
        title="All folders"
        subtitle={`${plural(actions.folders.length, 'folder')} across your drives`}
        back="/files"
        actions={
          <>
            <IconButton icon={FolderPlus} label="New folder" tone="primary" onClick={() => actions.newFolder(null)} />
          </>
        }
      />
      {itemMenu.element}

      <div className="glass mb-4 flex h-[54px] items-center rounded-[28px] pl-4 pr-1.5 focus-within:ring-2 focus-within:ring-accent/40">
        <Search size={19} className="text-muted" aria-hidden />
        <label htmlFor="folder-search" className="sr-only">Find a folder</label>
        <input id="folder-search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Find a folder" className="h-full min-w-0 flex-1 bg-transparent px-3 text-[15px] text-ink outline-none placeholder:text-subtle" />
        {q && <IconButton icon={X} label="Clear" tone="plain" size={42} onClick={() => setQ('')} />}
      </div>
      <div role="group" aria-label="Sort folders" className="no-scrollbar -mx-5 mb-4 flex gap-2 overflow-x-auto px-5 sm:mx-0 sm:px-0">
        {SORTS.map((s) => <Chip key={s.value} selected={sort === s.value} onClick={() => setSort(s.value)}>{s.label}</Chip>)}
      </div>

      {list.length === 0 ? (
        <EmptyState icon={Folders} title={q ? 'No folder matches' : 'No folders yet'} body={q ? 'Try another name.' : 'Folders help you keep things tidy. They also exist on your Google Drive.'} action={!q && <Button icon={FolderPlus} onClick={() => actions.newFolder(null)}>New folder</Button>} />
      ) : (
        <ul className="stagger grid grid-cols-1 gap-2 md:grid-cols-2">
          {list.map((f) => {
            const parent = folderPath(actions.folders, f.parentId).map((p) => p.name).join(' / ')
            return (
              <li key={f.id}>
                <FolderRow
                  folder={f}
                  subtitle={`${parent ? `in ${parent} · ` : ''}${plural(counts.get(f.id) ?? 0, 'file')} · ${shortDate(f.updatedAt)}`}
                  onOpen={() => open(f.id)}
                  onMenu={(a) => itemMenu.show(f.name, actions.folderMenu(f, () => open(f.id)), a)}
                  onDropFile={(id) => void actions.moveTo([id], f.id).catch(() => undefined)}
                />
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
