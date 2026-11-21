import { useEffect, useId, useRef, useState } from 'react'
import { ArrowLeft } from 'lucide-react'
import { Button, IconButton } from '@/components/ui/Button'
import { Spinner } from '@/components/ui/Spinner'
import { api, errorMessage } from '@/lib/api'
import type { FileItem, OfficeSession } from '@/lib/types'

/**
 * In-app editor (Collabora Online over WOPI, served by Caddy on this origin). The access token is
 * POSTed into the editor frame, never put in a URL, exactly like /editor-host.html does for the desktop app.
 */
export function Editor({ file, onClose }: { file: FileItem; onClose: () => void }) {
  const ref = useRef<HTMLDialogElement>(null)
  const formRef = useRef<HTMLFormElement>(null)
  const frameName = `nd-editor-${useId().replace(/:/g, '')}`
  const [session, setSession] = useState<OfficeSession | null>(null)
  const [error, setError] = useState('')
  const [loaded, setLoaded] = useState(false)

  useEffect(() => {
    const el = ref.current
    if (el && !el.open) el.showModal()
    return () => el?.close()
  }, [])

  useEffect(() => {
    let alive = true
    api.files.officeSession(file.id)
      .then((s) => {
        if (!alive) return
        if (!s.url.startsWith(`${window.location.origin}/browser/`)) throw new Error('The editor returned an unexpected address.')
        setSession(s)
      })
      .catch((e) => alive && setError(errorMessage(e, "Couldn't open the editor.")))
    return () => { alive = false }
  }, [file.id])

  useEffect(() => {
    if (session) formRef.current?.submit()
  }, [session])

  // Collabora talks to its host page with postMessage (closebutton=1 → UI_Close).
  useEffect(() => {
    const onMessage = (e: MessageEvent) => {
      if (e.origin !== window.location.origin || typeof e.data !== 'string') return
      let msg: { MessageId?: string; Values?: { Status?: string } }
      try {
        msg = JSON.parse(e.data)
      } catch {
        return
      }
      const frame = document.querySelector<HTMLIFrameElement>(`iframe[name="${frameName}"]`)
      if (msg.MessageId === 'App_LoadingStatus' && msg.Values?.Status === 'Frame_Ready') {
        frame?.contentWindow?.postMessage(JSON.stringify({ MessageId: 'Host_PostmessageReady' }), window.location.origin)
      }
      if (msg.MessageId === 'App_LoadingStatus' && msg.Values?.Status === 'Document_Loaded') setLoaded(true)
      if (msg.MessageId === 'UI_Close') onClose()
    }
    window.addEventListener('message', onMessage)
    return () => window.removeEventListener('message', onMessage)
  }, [frameName, onClose])

  return (
    <dialog
      ref={ref}
      aria-label={`Editing ${file.name}`}
      onCancel={(e) => { e.preventDefault(); onClose() }}
      className="m-0 h-dvh max-h-none w-screen max-w-none border-0 bg-haze p-0 text-ink"
    >
      <div className="flex h-full flex-col">
        <header className="flex items-center gap-2 border-b border-line bg-sheet px-2 py-1.5 sm:px-4">
          <IconButton icon={ArrowLeft} label="Close editor" tone="plain" onClick={onClose} />
          <div className="min-w-0 flex-1">
            <h2 className="truncate font-sans text-[15px] font-semibold tracking-normal">{file.name}</h2>
            <p className="text-[12px] text-muted">Changes save to {file.provider === 's3' ? 'your S3 bucket' : 'Google Drive'} automatically</p>
          </div>
        </header>
        <div className="relative min-h-0 flex-1">
          {error ? (
            <div className="flex h-full flex-col items-center justify-center gap-4 p-6 text-center">
              <p className="max-w-sm text-[15px] text-muted">{error}</p>
              <Button variant="secondary" onClick={onClose}>Back</Button>
            </div>
          ) : (
            <>
              {!loaded && (
                <div className="pointer-events-none absolute inset-0 z-10 flex flex-col items-center justify-center gap-3 bg-haze text-accent">
                  <Spinner size={30} label="Opening the editor" />
                  <p className="text-[13px] text-muted">Opening the editor…</p>
                </div>
              )}
              {session && (
                <form ref={formRef} action={session.url} method="post" target={frameName} className="hidden">
                  <input type="hidden" name="access_token" value={session.accessToken} />
                  <input type="hidden" name="access_token_ttl" value={String(session.accessTokenTtl)} />
                </form>
              )}
              <iframe name={frameName} title={`Editor for ${file.name}`} allow="clipboard-read; clipboard-write; fullscreen" className="relative h-full w-full border-0" onLoad={() => session && window.setTimeout(() => setLoaded(true), 2500)} />
            </>
          )}
        </div>
      </div>
    </dialog>
  )
}
