import { Archive, CirclePlay, File, FileSpreadsheet, FileText, FileType, Image, Music, type LucideIcon } from 'lucide-react'
import type { FileItem, Folder } from '@/lib/types'

/** File categories with the app's colours (shared/util/Format.kt). */
export type KindKey = 'image' | 'video' | 'pdf' | 'doc' | 'sheet' | 'audio' | 'archive' | 'text' | 'other'
export type Kind = { key: KindKey; label: string; badge: string; icon: LucideIcon; color: string }

export const KINDS: Record<KindKey, Kind> = {
  image: { key: 'image', label: 'Images', badge: 'JPG', icon: Image, color: '#1e9e57' },
  video: { key: 'video', label: 'Videos', badge: 'MP4', icon: CirclePlay, color: '#f59e0b' },
  pdf: { key: 'pdf', label: 'PDFs', badge: 'PDF', icon: FileType, color: '#e8402b' },
  doc: { key: 'doc', label: 'Docs', badge: 'DOC', icon: FileText, color: '#2e64e0' },
  sheet: { key: 'sheet', label: 'Sheets', badge: 'XLS', icon: FileSpreadsheet, color: '#1e9e57' },
  audio: { key: 'audio', label: 'Audio', badge: 'MP3', icon: Music, color: '#b07800' },
  archive: { key: 'archive', label: 'Archives', badge: 'ZIP', icon: Archive, color: '#a23fcf' },
  text: { key: 'text', label: 'Text', badge: 'TXT', icon: FileText, color: '#2e64e0' },
  other: { key: 'other', label: 'Other', badge: 'FILE', icon: File, color: '#8b72f5' },
}

/** Filter chips on My files, in the app's order. */
export const FILTER_KINDS: KindKey[] = ['image', 'video', 'pdf', 'doc', 'archive']

const GOOGLE_DOC = 'application/vnd.google-apps.document'
const GOOGLE_SHEET = 'application/vnd.google-apps.spreadsheet'
const GOOGLE_SLIDES = 'application/vnd.google-apps.presentation'
const GOOGLE_DRAWING = 'application/vnd.google-apps.drawing'

export const ext = (name: string) => (name.includes('.') ? name.slice(name.lastIndexOf('.') + 1).toLowerCase() : '')

export function kindOf(mime: string, name = ''): KindKey {
  const e = ext(name)
  if (mime.startsWith('image/') || mime === GOOGLE_DRAWING) return 'image'
  if (mime.startsWith('video/')) return 'video'
  if (mime.startsWith('audio/')) return 'audio'
  if (mime === 'application/pdf' || mime === GOOGLE_SLIDES) return 'pdf'
  if (mime.includes('spreadsheet') || mime.includes('excel') || ['xls', 'xlsx', 'csv'].includes(e)) return 'sheet'
  if (mime.includes('word') || mime === GOOGLE_DOC || ['doc', 'docx', 'odt'].includes(e)) return 'doc'
  if (/zip|rar|tar|7z/.test(mime)) return 'archive'
  if (mime.startsWith('text/') || mime === 'application/json') return 'text'
  return 'other'
}

export const fileKind = (f: Pick<FileItem, 'mimeType' | 'name'>) => KINDS[kindOf(f.mimeType, f.name)]

/** Chips match the app: "Docs" also covers sheets and text files. */
export function matchesFilter(f: FileItem, filter: KindKey) {
  const k = kindOf(f.mimeType, f.name)
  if (filter === 'doc') return k === 'doc' || k === 'sheet' || k === 'text'
  return k === filter
}

export const badge = (f: Pick<FileItem, 'mimeType' | 'name'>) => ext(f.name).toUpperCase().slice(0, 4) || fileKind(f).badge

/** How the viewer shows a file (shared/util/Previews.kt). */
export type ViewerType = 'image' | 'video' | 'audio' | 'pdf' | 'sheet' | 'doc' | 'office' | 'google' | 'text' | 'none'

const TEXT_EXT = new Set(['txt', 'md', 'log', 'json', 'xml', 'yaml', 'yml', 'ini', 'conf', 'cfg', 'env', 'tsv', 'kt', 'kts', 'java', 'js', 'ts', 'tsx', 'jsx', 'py', 'rb', 'go', 'rs', 'c', 'h', 'cpp', 'cs', 'php', 'swift', 'sh', 'bat', 'ps1', 'sql', 'html', 'htm', 'css', 'scss', 'gradle', 'properties', 'toml', 'srt', 'vtt'])
const SHEET_EXT = new Set(['xls', 'xlsx', 'xlsm', 'ods', 'csv'])
const WORD_EXT = new Set(['doc', 'docx', 'odt', 'rtf'])
const OFFICE_EXT = new Set(['ppt', 'pptx', 'odp'])

export function viewerType(f: Pick<FileItem, 'mimeType' | 'name'>): ViewerType {
  const m = f.mimeType
  const e = ext(f.name)
  if (m.startsWith('image/') || m === GOOGLE_DRAWING) return 'image'
  if (m.startsWith('video/')) return 'video'
  if (m.startsWith('audio/')) return 'audio'
  if (m === 'application/pdf') return 'pdf'
  if (m === GOOGLE_SHEET || /spreadsheet|ms-excel|text\/csv|opendocument\.spreadsheet/.test(m) || SHEET_EXT.has(e)) return 'sheet'
  if (m === GOOGLE_DOC || /wordprocessingml|msword|opendocument\.text|rtf/.test(m) || WORD_EXT.has(e)) return 'doc'
  if (m === GOOGLE_SLIDES) return 'google'
  if (/presentation|powerpoint/.test(m) || OFFICE_EXT.has(e)) return 'office'
  if (m.startsWith('text/') || ['application/json', 'application/xml', 'application/javascript'].includes(m) || TEXT_EXT.has(e)) return 'text'
  return 'none'
}

export const isGoogleNative = (f: Pick<FileItem, 'mimeType'>) => f.mimeType.startsWith('application/vnd.google-apps.')

/** Files the in-app editor (Collabora) can open; the server has the final say. */
const EDITABLE_EXT = new Set(['doc', 'docx', 'odt', 'rtf', 'txt', 'xls', 'xlsx', 'xlsm', 'ods', 'csv', 'ppt', 'pptx', 'odp'])
export const canEditInApp = (f: Pick<FileItem, 'mimeType' | 'name'>) => !isGoogleNative(f) && EDITABLE_EXT.has(ext(f.name))

/** Google can make thumbnails for photos, videos and documents; S3 only serves images. */
export function hasThumbnail(f: FileItem) {
  if (f.status !== 'active') return false
  const v = viewerType(f)
  if (v === 'image') return true
  return f.provider === 'google_drive' && ['video', 'pdf', 'sheet', 'doc', 'office', 'google'].includes(v)
}

export const isMedia = (f: FileItem) => {
  const v = viewerType(f)
  return v === 'image' || v === 'video'
}

/** The date a photo was taken when known, otherwise when it was added. */
export const photoDate = (f: FileItem) => f.takenAt ?? f.createdAt

export const providerLabel = (provider: string | undefined) => (provider === 's3' ? 'S3 bucket' : 'Google Drive')

// ---------------------------------------------------------------------------------------- folders

export const FOLDER_PALETTE = ['#8b72f5', '#3b82f6', '#10b981', '#f59e0b', '#ec4899', '#ef4444', '#14b8a6', '#64748b']

const LEGACY: Record<string, string> = { red: '#ef4444', green: '#10b981', emerald: '#10b981', lime: '#84cc16', yellow: '#f59e0b', amber: '#f59e0b', purple: '#8b5cf6', violet: '#8b5cf6', pink: '#ec4899', orange: '#f97316', cyan: '#22d3ee' }

/** Folder colours are "#rrggbb" or legacy Tailwind names like "text-lime-500". */
export function folderColor(raw: string | null | undefined) {
  if (raw && /^#[0-9a-f]{6}$/i.test(raw)) return raw
  const hit = Object.keys(LEGACY).find((k) => raw?.includes(k))
  return hit ? LEGACY[hit] : '#3b82f6'
}

export const DEFAULT_FOLDER_ICON = 'https://api.iconify.design/lucide:folder.svg'

/** Root-to-folder chain, guarded against cycles. */
export function folderPath(folders: Folder[], id: string | null | undefined): Folder[] {
  if (!id) return []
  const byId = new Map(folders.map((f) => [f.id, f]))
  const out: Folder[] = []
  const seen = new Set<string>()
  let cur = byId.get(id)
  while (cur && !seen.has(cur.id)) {
    out.unshift(cur)
    seen.add(cur.id)
    cur = cur.parentId ? byId.get(cur.parentId) : undefined
  }
  return out
}

/** A folder plus every folder inside it (a folder cannot be moved into these). */
export function descendantIds(folders: Folder[], id: string) {
  const ids = new Set([id])
  let changed = true
  while (changed) {
    changed = false
    for (const f of folders) if (f.parentId && ids.has(f.parentId) && !ids.has(f.id)) { ids.add(f.id); changed = true }
  }
  return ids
}

export type SortMode = 'newest' | 'oldest' | 'name' | 'largest'
export const SORT_LABELS: Record<SortMode, string> = { newest: 'Newest first', oldest: 'Oldest first', name: 'Name A–Z', largest: 'Largest first' }

export function sortFiles(files: FileItem[], mode: SortMode) {
  const out = [...files]
  if (mode === 'newest') out.sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  if (mode === 'oldest') out.sort((a, b) => a.createdAt.localeCompare(b.createdAt))
  if (mode === 'name') out.sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true, sensitivity: 'base' }))
  if (mode === 'largest') out.sort((a, b) => Number(b.sizeBytes) - Number(a.sizeBytes))
  return out
}

export function sortFolders(folders: Folder[], mode: SortMode) {
  const out = [...folders]
  if (mode === 'name' || mode === 'largest') out.sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true, sensitivity: 'base' }))
  if (mode === 'newest') out.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
  if (mode === 'oldest') out.sort((a, b) => a.createdAt.localeCompare(b.createdAt))
  return out
}
