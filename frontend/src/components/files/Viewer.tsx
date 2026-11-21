import { useEffect, useRef, useState, type ReactNode } from 'react'
import clsx from 'clsx'
import { ArrowLeft, ChevronLeft, ChevronRight, Download, ExternalLink, FileQuestion, Info, Link2, Pencil, Trash2 } from 'lucide-react'
import { Button, IconButton } from '@/components/ui/Button'
import { Spinner } from '@/components/ui/Spinner'
import { FileThumb } from '@/components/files/FileVisuals'
import { api, authedFetch, errorMessage, previewUrl } from '@/lib/api'
import { canEditInApp, fileKind, isGoogleNative, viewerType } from '@/lib/files'
import { formatBytes, fullDate } from '@/lib/format'
import { loadThumb } from '@/lib/thumbs'
import type { FileItem, SheetData } from '@/lib/types'

export type ViewerActions = {
  download: (f: FileItem) => void
  share: (f: FileItem) => void
  details: (f: FileItem) => void
  edit: (f: FileItem) => void
  trash: (f: FileItem) => void
}

type Props = { files: FileItem[]; index: number; onIndex: (i: number) => void; onClose: () => void; actions: ViewerActions }

/** Full-screen viewer for photos, video, audio, PDFs, documents, sheets and text, with swipe-through. */
export function Viewer({ files, index, onIndex, onClose, actions }: Props) {
  const ref = useRef<HTMLDialogElement>(null)
  const file = files[index]
  const touch = useRef<number | null>(null)
  const strip = useRef<HTMLDivElement>(null)
  // Film strip along the bottom when paging through photos and videos (ViewerScreen).
  const media = files.length > 1 && files.every((f) => { const v = viewerType(f); return v === 'image' || v === 'video' })

  useEffect(() => {
    const el = strip.current?.querySelector<HTMLElement>('[aria-current="true"]')
    el?.scrollIntoView({ inline: 'center', block: 'nearest', behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' })
  }, [index])

  useEffect(() => {
    const el = ref.current
    if (el && !el.open) el.showModal()
    return () => el?.close()
  }, [])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.target as HTMLElement)?.closest('input, textarea, select')) return
      if (e.key === 'ArrowLeft' && index > 0) onIndex(index - 1)
      if (e.key === 'ArrowRight' && index < files.length - 1) onIndex(index + 1)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [index, files.length, onIndex])

  if (!file) return null
  const kind = fileKind(file)
  const editable = canEditInApp(file) || (isGoogleNative(file) && file.provider === 'google_drive')

  return (
    <dialog
      ref={ref}
      aria-label={`Viewing ${file.name}`}
      onCancel={(e) => { e.preventDefault(); onClose() }}
      className="m-0 h-dvh max-h-none w-screen max-w-none border-0 bg-[#0b0a10] p-0 text-white backdrop:bg-black"
    >
      <div className="viewer-in flex h-full flex-col">
        <header className="flex items-center gap-2 bg-gradient-to-b from-black/70 to-transparent px-2 py-2 pt-[max(0.5rem,env(safe-area-inset-top))] sm:px-4">
          <IconButton icon={ArrowLeft} label="Close viewer" tone="inverse" onClick={onClose} className="!text-white" autoFocus />
          <div className="min-w-0 flex-1 px-1">
            <h2 className="truncate font-sans text-[15px] font-semibold tracking-normal">{file.name}</h2>
            <p className="truncate text-[12px] text-white/60">
              {kind.label.replace(/s$/, '')} · {formatBytes(file.sizeBytes)} · {fullDate(file.takenAt ?? file.createdAt)}
              {files.length > 1 && ` · ${index + 1} of ${files.length}`}
            </p>
          </div>
          {editable && <IconButton icon={Pencil} label="Edit" tone="inverse" className="!text-white" onClick={() => actions.edit(file)} />}
          <IconButton icon={Link2} label="Share link" tone="inverse" className="!text-white" onClick={() => actions.share(file)} />
          <IconButton icon={Download} label="Download" tone="inverse" className="!text-white" onClick={() => actions.download(file)} />
          <IconButton icon={Info} label="Details" tone="inverse" className="!text-white max-sm:hidden" onClick={() => actions.details(file)} />
          <IconButton icon={Trash2} label="Move to trash" tone="inverse" className="!text-white max-sm:hidden" onClick={() => actions.trash(file)} />
        </header>
        <div
          className="relative min-h-0 flex-1"
          onTouchStart={(e) => { touch.current = e.touches[0].clientX }}
          onTouchEnd={(e) => {
            if (touch.current === null) return
            const dx = e.changedTouches[0].clientX - touch.current
            touch.current = null
            if (dx > 70 && index > 0) onIndex(index - 1)
            if (dx < -70 && index < files.length - 1) onIndex(index + 1)
          }}
        >
          <div key={file.id} className="viewer-swap h-full w-full"><ViewerBody file={file} onDownload={() => actions.download(file)} /></div>
          {index > 0 && (
            <button type="button" onClick={() => onIndex(index - 1)} aria-label="Previous file" className="press absolute left-3 top-1/2 hidden h-12 w-12 -translate-y-1/2 items-center justify-center rounded-full bg-white/10 backdrop-blur hover:bg-white/20 sm:flex">
              <ChevronLeft size={26} aria-hidden />
            </button>
          )}
          {index < files.length - 1 && (
            <button type="button" onClick={() => onIndex(index + 1)} aria-label="Next file" className="press absolute right-3 top-1/2 hidden h-12 w-12 -translate-y-1/2 items-center justify-center rounded-full bg-white/10 backdrop-blur hover:bg-white/20 sm:flex">
              <ChevronRight size={26} aria-hidden />
            </button>
          )}
        </div>
        {media && (
          <div ref={strip} role="tablist" aria-label="Photos and videos" className="no-scrollbar flex gap-1.5 overflow-x-auto px-4 pb-2 pt-1 [scroll-padding-inline:50%]">
            {files.map((f, i) => (
              <button
                key={f.id}
                type="button"
                role="tab"
                aria-selected={i === index}
                aria-current={i === index}
                aria-label={f.name}
                onClick={() => onIndex(i)}
                className={clsx('press shrink-0 overflow-hidden rounded-[10px] transition-[opacity,outline-color] duration-200', i === index ? 'opacity-100 outline-2 outline-offset-2 outline-white' : 'opacity-55 outline-transparent hover:opacity-90')}
              >
                <FileThumb file={f} size={48} radius={10} />
              </button>
            ))}
          </div>
        )}
        <nav className="flex justify-center gap-1 pb-[max(0.5rem,env(safe-area-inset-bottom))] sm:hidden" aria-label="More actions">
          <Button variant="ghost" size="sm" icon={Info} className="!text-white hover:!bg-white/10" onClick={() => actions.details(file)}>Details</Button>
          <Button variant="ghost" size="sm" icon={Trash2} className="!text-white hover:!bg-white/10" onClick={() => actions.trash(file)}>Trash</Button>
        </nav>
      </div>
    </dialog>
  )
}

function Centered({ children }: { children: ReactNode }) {
  return <div className="flex h-full w-full items-center justify-center p-4">{children}</div>
}

function Loading({ file }: { file: FileItem }) {
  return (
    <Centered>
      <div className="flex flex-col items-center gap-4 text-white/70">
        <FileThumb file={file} size={96} radius={28} />
        <Spinner size={26} label="Loading preview" />
      </div>
    </Centered>
  )
}

function NoPreview({ file, onDownload, message }: { file: FileItem; onDownload: () => void; message?: string }) {
  return (
    <Centered>
      <div className="flex max-w-sm flex-col items-center text-center">
        <span className="flex h-20 w-20 items-center justify-center rounded-[26px] bg-white/10"><FileQuestion size={36} aria-hidden /></span>
        <h3 className="mt-5 text-[18px] font-semibold">Preview not available</h3>
        <p className="mt-2 text-[14px] text-white/65">{message ?? `${file.name} can't be shown in the browser. Download it to open it with another app.`}</p>
        <Button className="mt-6" icon={Download} onClick={onDownload}>Download</Button>
      </div>
    </Centered>
  )
}

/** Resolves a short-lived URL (or fetched content) for the file. */
function useSource<T>(load: () => Promise<T>) {
  const [state, setState] = useState<{ value?: T; error?: string }>({})
  const loadRef = useRef(load)
  // Runs once per mount; each viewer body is keyed by file id.
  useEffect(() => {
    let alive = true
    loadRef.current().then((value) => alive && setState({ value }), (e) => alive && setState({ error: errorMessage(e) }))
    return () => { alive = false }
  }, [])
  return state
}

function ViewerBody({ file, onDownload }: { file: FileItem; onDownload: () => void }) {
  switch (viewerType(file)) {
    case 'image': return <ImageView file={file} onDownload={onDownload} />
    case 'video': return <MediaView file={file} kind="video" onDownload={onDownload} />
    case 'audio': return <MediaView file={file} kind="audio" onDownload={onDownload} />
    case 'pdf': return <FrameView file={file} onDownload={onDownload} source={() => previewUrl(file.id)} />
    case 'sheet': return <SheetView file={file} onDownload={onDownload} />
    case 'doc': return <DocView file={file} onDownload={onDownload} />
    case 'google': return <FrameView file={file} onDownload={onDownload} source={() => previewUrl(file.id)} />
    case 'office': return <FrameView file={file} onDownload={onDownload} source={() => (file.provider === 'google_drive' ? pdfBlobUrl(file.id) : officeViewerUrl(file.id))} />
    case 'text': return <TextView file={file} onDownload={onDownload} />
    default: return <NoPreview file={file} onDownload={onDownload} />
  }
}

/** Google converts office files / Google Docs to PDF (GET /files/:id/document-preview needs the token). */
async function pdfBlobUrl(id: string) {
  const res = await authedFetch(`/files/${encodeURIComponent(id)}/document-preview`)
  return URL.createObjectURL(await res.blob())
}

/** Files on S3 fall back to Microsoft's online viewer, fed with a 10-minute public preview URL. */
async function officeViewerUrl(id: string) {
  const url = new URL(await previewUrl(id), window.location.origin).toString()
  return `https://view.officeapps.live.com/op/embed.aspx?src=${encodeURIComponent(url)}`
}

function ImageView({ file, onDownload }: { file: FileItem; onDownload: () => void }) {
  const [placeholder, setPlaceholder] = useState<string | null>(null)
  const [loaded, setLoaded] = useState(false)
  const [failed, setFailed] = useState(false)
  const src = useSource(() => previewUrl(file.id))
  useEffect(() => {
    if (file.provider === 'google_drive' || file.mimeType.startsWith('image/')) void loadThumb(file.id, 1024).then(setPlaceholder)
  }, [file.id, file.provider, file.mimeType])
  if (src.error || failed) return <NoPreview file={file} onDownload={onDownload} message={src.error ?? "This picture couldn't be loaded."} />
  return (
    <Centered>
      {!loaded && placeholder && <img src={placeholder} alt="" className="absolute max-h-full max-w-full scale-[1.001] object-contain blur-[2px]" />}
      {!loaded && !placeholder && <Spinner size={28} label="Loading picture" />}
      {src.value && (
        <img
          src={src.value}
          alt={file.name}
          onLoad={() => setLoaded(true)}
          onError={() => setFailed(true)}
          className={clsx('relative max-h-full max-w-full select-none object-contain transition-opacity', loaded ? 'opacity-100' : 'opacity-0')}
        />
      )}
    </Centered>
  )
}

function MediaView({ file, kind, onDownload }: { file: FileItem; kind: 'video' | 'audio'; onDownload: () => void }) {
  const src = useSource(() => previewUrl(file.id))
  const [failed, setFailed] = useState(false)
  if (src.error || failed) return <NoPreview file={file} onDownload={onDownload} message={src.error ?? "This file can't be played in the browser. Download it to play it with another app."} />
  if (!src.value) return <Loading file={file} />
  if (kind === 'audio') {
    return (
      <Centered>
        <div className="flex w-full max-w-md flex-col items-center gap-6">
          <FileThumb file={file} size={140} radius={40} />
          <audio controls autoPlay src={src.value} onError={() => setFailed(true)} className="w-full" />
        </div>
      </Centered>
    )
  }
  return (
    <Centered>
      <video controls autoPlay playsInline src={src.value} onError={() => setFailed(true)} className="max-h-full max-w-full rounded-[12px] bg-black" />
    </Centered>
  )
}

function FrameView({ file, onDownload, source }: { file: FileItem; onDownload: () => void; source: () => Promise<string> }) {
  const src = useSource(source)
  useEffect(() => () => { if (src.value?.startsWith('blob:')) URL.revokeObjectURL(src.value) }, [src.value])
  if (src.error) return <NoPreview file={file} onDownload={onDownload} message={src.error} />
  if (!src.value) return <Loading file={file} />
  return (
    <div className="flex h-full flex-col px-2 pb-2 sm:px-6 sm:pb-6">
      <iframe src={src.value} title={file.name} className="min-h-0 w-full flex-1 rounded-[14px] border-0 bg-white" />
      <a href={src.value} target="_blank" rel="noreferrer" className="mx-auto mt-2 inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-[13px] font-semibold text-white/75 hover:bg-white/10 hover:text-white">
        <ExternalLink size={15} aria-hidden />Open in a new tab
      </a>
    </div>
  )
}

function DocView({ file, onDownload }: { file: FileItem; onDownload: () => void }) {
  const src = useSource(async () => (await authedFetch(`/files/${encodeURIComponent(file.id)}/document-html`)).text())
  if (src.error) return <NoPreview file={file} onDownload={onDownload} message={src.error} />
  if (src.value === undefined) return <Loading file={file} />
  // Rendered in a sandboxed frame with scripts disabled: the HTML comes from the document itself.
  const page = `<!doctype html><html><head><meta charset="utf-8"><base target="_blank"><style>body{margin:0;padding:28px;font-family:Inter,system-ui,sans-serif;color:#1e1440;background:#fff;line-height:1.5}img{max-width:100%;height:auto}table{border-collapse:collapse;max-width:100%}td,th{border:1px solid #d6d1e6;padding:4px 6px}</style></head><body>${src.value}</body></html>`
  return (
    <div className="flex h-full justify-center px-2 pb-2 sm:px-6 sm:pb-6">
      <iframe sandbox="allow-popups allow-popups-to-escape-sandbox" srcDoc={page} title={file.name} className="h-full w-full max-w-[900px] rounded-[14px] border-0 bg-white" />
    </div>
  )
}

function SheetView({ file, onDownload }: { file: FileItem; onDownload: () => void }) {
  const src = useSource(() => api.files.sheetData(file.id))
  const [tab, setTab] = useState(0)
  if (src.error) return <NoPreview file={file} onDownload={onDownload} message={src.error} />
  const data: SheetData | undefined = src.value
  if (!data) return <Loading file={file} />
  const sheet = data.sheets[tab]
  if (!sheet) return <NoPreview file={file} onDownload={onDownload} message="This spreadsheet is empty." />
  return (
    <div className="flex h-full flex-col px-2 pb-2 sm:px-6 sm:pb-6">
      <div className="min-h-0 flex-1 overflow-auto rounded-[14px] bg-white text-[#1e1440]">
        <table className="border-collapse text-[13px]">
          <tbody>
            {sheet.rows.map((row, r) => (
              <tr key={r}>
                <th scope="row" className="sticky left-0 border border-[#e7e1f7] bg-[#f6f3ff] px-2 text-right text-[11px] font-semibold text-[#6b6390]">{r + 1}</th>
                {row.map((cell, c) => (
                  <td key={c} className="max-w-[320px] truncate border border-[#e7e1f7] px-2 py-1" style={{ minWidth: Math.min(320, Math.max(60, sheet.colWidths[c] ?? 90)) }} title={cell}>{cell}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
        {sheet.truncated && <p className="p-3 text-[12px] text-[#6b6390]">Showing the first rows and columns only. Download the file to see everything.</p>}
      </div>
      {data.sheets.length > 1 && (
        <div role="tablist" aria-label="Sheets" className="no-scrollbar mt-2 flex gap-1.5 overflow-x-auto">
          {data.sheets.map((s, i) => (
            <button key={s.name} type="button" role="tab" aria-selected={tab === i} onClick={() => setTab(i)} className={clsx('press shrink-0 rounded-full px-3.5 py-1.5 text-[13px] font-semibold', tab === i ? 'bg-white text-[#1e1440]' : 'bg-white/10 text-white hover:bg-white/20')}>{s.name}</button>
          ))}
        </div>
      )}
    </div>
  )
}

function TextView({ file, onDownload }: { file: FileItem; onDownload: () => void }) {
  const src = useSource(async () => {
    if (Number(file.sizeBytes) > 2 * 1024 * 1024) throw new Error('This text file is too big to show here. Download it instead.')
    const res = await fetch(await previewUrl(file.id))
    if (!res.ok) throw new Error("This file couldn't be loaded.")
    return res.text()
  })
  if (src.error) return <NoPreview file={file} onDownload={onDownload} message={src.error} />
  if (src.value === undefined) return <Loading file={file} />
  return (
    <div className="h-full px-2 pb-2 sm:px-6 sm:pb-6">
      <pre className="h-full overflow-auto whitespace-pre-wrap break-words rounded-[14px] bg-[#131218] p-5 font-mono text-[13px] leading-relaxed text-[#f4f2fa]">{src.value}</pre>
    </div>
  )
}

