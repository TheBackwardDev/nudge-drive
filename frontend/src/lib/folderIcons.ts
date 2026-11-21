import {
  Archive, BookOpen, Box, Briefcase, Calendar, Camera, ChartColumn, Clock, Cloud, CloudDownload, CloudUpload, Code, Database, FileArchive, FileCode,
  FileImage, FileMusic, FileText, FileVideoCamera, Files, Film, Folder, FolderOpen, Folders, GraduationCap, HardDrive, Headphones, Heart, House, Image,
  Images, Lock, Music, Notebook, Package, Plane, Receipt, Rocket, Server, Share2, Shield, Sparkles, Star, Terminal, UserCheck, Users, Video, Wallet,
  type LucideIcon,
} from 'lucide-react'

/**
 * Folder icons. The server stores them as https://api.iconify.design/lucide:<name>.svg (its validation requires
 * that form, and other Nudge apps read it), but they are drawn locally with the matching Lucide icon so they
 * work offline and don't depend on Iconify's public API.
 */
export const FOLDER_ICON_MAP: Record<string, LucideIcon> = {
  folder: Folder, 'folder-open': FolderOpen, folders: Folders, files: Files, 'file-text': FileText, 'file-image': FileImage,
  'file-video': FileVideoCamera, 'file-music': FileMusic, 'file-code': FileCode, 'file-archive': FileArchive, briefcase: Briefcase,
  archive: Archive, cloud: Cloud, 'cloud-upload': CloudUpload, 'cloud-download': CloudDownload, 'hard-drive': HardDrive, database: Database,
  server: Server, image: Image, images: Images, video: Video, film: Film, music: Music, headphones: Headphones, code: Code, terminal: Terminal,
  package: Package, box: Box, 'book-open': BookOpen, notebook: Notebook, 'graduation-cap': GraduationCap, receipt: Receipt, wallet: Wallet,
  'chart-column': ChartColumn, calendar: Calendar, clock: Clock, users: Users, 'user-check': UserCheck, 'share-2': Share2, lock: Lock,
  shield: Shield, star: Star, heart: Heart, rocket: Rocket, sparkles: Sparkles, camera: Camera, plane: Plane, house: House,
}

export const iconUrlFor = (name: string) => `https://api.iconify.design/lucide:${name}.svg`

/** The Lucide icon for a stored icon URL (also accepts the /lucide/<name>.svg form); Folder when unknown. */
export function folderIconFor(iconUrl: string | null | undefined): LucideIcon {
  const name = /lucide[:/]([a-z0-9-]+)\.svg/.exec(iconUrl ?? '')?.[1]
  return (name && FOLDER_ICON_MAP[name]) || Folder
}
