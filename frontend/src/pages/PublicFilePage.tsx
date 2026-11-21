import { useEffect, useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Code, Download, Link2Off } from 'lucide-react'
import { LogoTile } from '@/components/brand/Brand'
import { Button } from '@/components/ui/Button'
import { Spinner } from '@/components/ui/Spinner'
import { FileThumb } from '@/components/files/FileVisuals'
import { api, publicDownloadUrl, publicPreviewUrl } from '@/lib/api'
import { fileKind, isGoogleNative, viewerType } from '@/lib/files'
import { copyText } from '@/lib/hooks'
import { formatBytes, shortDate } from '@/lib/format'
import type { FileItem } from '@/lib/types'

type PublicFile = { id: string; name: string; mimeType: string; sizeBytes: string; createdAt: string }

const asItem = (f: PublicFile): FileItem => ({ ...f, provider: 'public', status: 'shared', folderId: null, connectedAccountId: '', updatedAt: f.createdAt })

/** Public share-link page (/public/files/:token) and its bare embed variant (/embed). No sign-in. */
export default function PublicFilePage({ embed = false }: { embed?: boolean }) {
  const { token = '' } = useParams()
  const [file, setFile] = useState<PublicFile | null>(null)
  const [failed, setFailed] = useState(false)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    setFailed(false)
    api.publicFile(token).then((r) => setFile(r.file), () => setFailed(true))
  }, [token])

  useEffect(() => {
    document.title = file ? `${file.name} · Nudge Drive` : 'Shared file · Nudge Drive'
  }, [file])

  if (failed) {
    return (
      <Shell>
        <div className="flex flex-1 items-center justify-center p-6">
          <div className="max-w-md rounded-[28px] bg-white/[0.06] p-8 text-center">
            <Link2Off size={44} className="mx-auto text-white/60" aria-hidden />
            <h1 className="mt-5 text-[24px] font-bold">This link doesn't work</h1>
            <p className="mt-2 text-[14px] text-white/65">It may have been turned off, or the file was deleted.</p>
            <Link to="/" className="mt-6 inline-block text-[14px] font-semibold text-lavender hover:underline">What is Nudge Drive?</Link>
          </div>
        </div>
      </Shell>
    )
  }
  if (!file) {
    return <Shell><div className="flex flex-1 items-center justify-center text-lavender"><Spinner size={30} label="Loading shared file" /></div></Shell>
  }

  const preview = <Preview file={file} token={token} />
  if (embed) return <main className="h-dvh overflow-hidden bg-black text-white">{preview}</main>

  return (
    <Shell>
      <header className="flex items-center gap-3 border-b border-white/10 bg-[#131218]/90 px-3 py-2.5 backdrop-blur sm:px-5">
        <Link to="/" aria-label="Nudge Drive"><LogoTile size={36} /></Link>
        <div className="min-w-0 flex-1">
          <h1 className="truncate font-sans text-[15px] font-semibold tracking-normal">{file.name}</h1>
          <p className="truncate text-[12px] text-white/60">{fileKind(asItem(file)).label.replace(/s$/, '')} · {formatBytes(file.sizeBytes)} · shared {shortDate(file.createdAt)}</p>
        </div>
        <Button
          variant="ghost"
          size="sm"
          icon={Code}
          className="!text-white hover:!bg-white/10 max-sm:hidden"
          onClick={async () => {
            if (await copyText(`<iframe src="${window.location.origin}/public/files/${token}/embed" width="800" height="450" style="border:0" allowfullscreen></iframe>`)) {
              setCopied(true)
              window.setTimeout(() => setCopied(false), 1800)
            }
          }}
        >
          {copied ? 'Embed code copied' : 'Embed'}
        </Button>
        <a href={publicDownloadUrl(token)} download className="press inline-flex h-9 items-center gap-1.5 rounded-[18px] bg-primary px-3.5 text-[13px] font-bold text-white hover:bg-primary-hover">
          <Download size={16} aria-hidden />Download
        </a>
      </header>
      <main className="min-h-0 flex-1 p-2 sm:p-5">{preview}</main>
    </Shell>
  )
}

function Shell({ children }: { children: ReactNode }) {
  return <div className="flex h-dvh flex-col bg-[#0b0a10] text-white">{children}</div>
}

function Preview({ file, token }: { file: PublicFile; token: string }) {
  const src = publicPreviewUrl(token)
  const item = asItem(file)
  const [broken, setBroken] = useState(false)
  const [text, setText] = useState<string | null>(null)
  // Google Docs / Sheets / Slides are exported to PDF for inline previews.
  const type = isGoogleNative(item) && viewerType(item) !== 'image' ? 'pdf' : viewerType(item)

  useEffect(() => {
    if (type !== 'text' || Number(file.sizeBytes) > 2 * 1024 * 1024) return
    fetch(src).then((r) => (r.ok ? r.text() : Promise.reject())).then(setText, () => setBroken(true))
  }, [type, src, file.sizeBytes])

  const none = (
    <div className="flex h-full flex-col items-center justify-center p-6 text-center">
      <FileThumb file={item} size={110} radius={34} thumb={false} />
      <h2 className="mt-6 text-[20px] font-semibold">{broken ? "This file couldn't be shown" : 'No preview for this file'}</h2>
      <p className="mt-2 max-w-sm text-[14px] text-white/65">Download it to open it with an app on your device.</p>
      <a href={publicDownloadUrl(token)} download className="press mt-6 inline-flex h-11 items-center gap-2 rounded-[22px] bg-primary px-5 text-[14px] font-bold text-white"><Download size={18} aria-hidden />Download · {formatBytes(file.sizeBytes)}</a>
    </div>
  )
  if (broken) return none

  const frame = 'h-full w-full rounded-[14px] border-0 bg-white'
  switch (type) {
    case 'image':
      return <div className="flex h-full items-center justify-center"><img src={src} alt={file.name} onError={() => setBroken(true)} className="max-h-full max-w-full object-contain" /></div>
    case 'video':
      return <div className="flex h-full items-center justify-center"><video src={src} controls playsInline onError={() => setBroken(true)} className="max-h-full max-w-full rounded-[12px] bg-black" /></div>
    case 'audio':
      return <div className="flex h-full flex-col items-center justify-center gap-6 p-6"><FileThumb file={item} size={130} radius={38} thumb={false} /><audio src={src} controls onError={() => setBroken(true)} className="w-full max-w-md" /></div>
    case 'pdf':
      return <iframe src={src} title={file.name} className={frame} />
    case 'doc':
    case 'sheet':
    case 'office': {
      const abs = new URL(src, window.location.origin).toString()
      return <iframe src={`https://view.officeapps.live.com/op/embed.aspx?src=${encodeURIComponent(abs)}`} title={file.name} className={frame} />
    }
    case 'text':
      return text === null ? <div className="flex h-full items-center justify-center"><Spinner size={26} /></div> : <pre className="h-full overflow-auto whitespace-pre-wrap break-words rounded-[14px] bg-[#131218] p-5 font-mono text-[13px] text-[#f4f2fa]">{text}</pre>
    default:
      return none
  }
}

