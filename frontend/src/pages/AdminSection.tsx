import { useEffect, useRef, useState, type FormEvent } from 'react'
import { DatabaseBackup, HardDriveDownload, KeyRound, RefreshCw, Upload } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog, Dialog } from '@/components/ui/Dialog'
import { PasswordField, TextField } from '@/components/ui/Field'
import { Panel } from '@/components/ui/Surface'
import { useToast } from '@/context/ToastContext'
import { api, authedFetch, errorMessage } from '@/lib/api'
import { clearSession } from '@/lib/session'

type GoogleConfig = Awaited<ReturnType<typeof api.system.googleConfig>>

/**
 * Server-admin tools (Google OAuth app, database backup / restore, code update). Rendered only when
 * GET /system/google-config succeeds, i.e. for emails listed in the server's ADMIN_EMAILS; everyone else
 * sees nothing (and no error).
 */
export function AdminSection() {
  const [config, setConfig] = useState<GoogleConfig | null>(null)
  useEffect(() => {
    api.system.googleConfig().then(setConfig, () => setConfig(null))
  }, [])
  if (!config) return null
  return (
    <section aria-label="Server admin" className="grid grid-cols-1 gap-2">
      <h2 className="px-1 text-[13px] font-semibold uppercase tracking-wider text-subtle">Server admin</h2>
      <Panel className="grid grid-cols-1 gap-6 p-5">
        <GoogleOAuthForm config={config} onSaved={() => api.system.googleConfig().then(setConfig, () => undefined)} />
        <div className="h-px bg-line" />
        <BackupRestore />
        <div className="h-px bg-line" />
        <SystemUpdate />
      </Panel>
    </section>
  )
}

function GoogleOAuthForm({ config, onSaved }: { config: GoogleConfig; onSaved: () => void }) {
  const toast = useToast()
  const [clientId, setClientId] = useState(config.clientId ?? '')
  const [secret, setSecret] = useState('')
  const [redirect, setRedirect] = useState(config.redirectUri ?? '')
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    try {
      const r = await api.system.saveGoogleConfig(clientId.trim(), secret || undefined, redirect.trim() || config.defaultRedirectUri)
      toast.show(r.message || 'Google OAuth credentials saved', { tone: 'success' })
      setSecret('')
      onSaved()
    } catch (err) {
      toast.error(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="grid grid-cols-1 gap-3">
      <div className="flex items-center gap-2"><KeyRound size={18} className="text-accent" aria-hidden /><h3 className="text-[16px] font-semibold">Google OAuth app</h3></div>
      <p className="text-[13px] text-muted">
        Used for Google sign-in and connecting drives. In Google Cloud Console create an OAuth client (Web application) with the Drive API enabled and add this redirect URI:{' '}
        <code className="break-all rounded-md bg-mist px-1.5 py-0.5 text-[12px] text-ink">{redirect || config.defaultRedirectUri}</code>
      </p>
      <TextField label="Client ID" showLabel value={clientId} onChange={(e) => setClientId(e.target.value)} required autoComplete="off" spellCheck={false} />
      <PasswordField label={config.hasSecret ? 'Client secret (saved; leave empty to keep it)' : 'Client secret'} showLabel value={secret} onChange={(e) => setSecret(e.target.value)} required={!config.hasSecret} autoComplete="off" />
      <TextField label="Redirect URI (optional)" showLabel placeholder={config.defaultRedirectUri} value={redirect} onChange={(e) => setRedirect(e.target.value)} spellCheck={false} />
      <Button type="submit" loading={busy} className="justify-self-start">Save credentials</Button>
    </form>
  )
}

function BackupRestore() {
  const toast = useToast()
  const [downloading, setDownloading] = useState(false)
  const [file, setFile] = useState<File | null>(null)
  const [confirm, setConfirm] = useState(false)

  async function download() {
    setDownloading(true)
    try {
      const res = await authedFetch('/system/backup')
      const url = URL.createObjectURL(await res.blob())
      const a = document.createElement('a')
      a.href = url
      a.download = 'nudge-drive-backup.db'
      a.click()
      window.setTimeout(() => URL.revokeObjectURL(url), 30_000)
    } catch (e) {
      toast.error(errorMessage(e, 'Backup failed'))
    } finally {
      setDownloading(false)
    }
  }

  return (
    <div className="grid grid-cols-1 gap-3">
      <div className="flex items-center gap-2"><DatabaseBackup size={18} className="text-accent" aria-hidden /><h3 className="text-[16px] font-semibold">Database backup</h3></div>
      <p className="text-[13px] text-muted">Download or restore the server's database file (single-file database installs only).</p>
      <div className="flex flex-wrap items-center gap-2.5">
        <Button variant="secondary" icon={HardDriveDownload} loading={downloading} onClick={download}>Download backup</Button>
        <label className="press inline-flex h-11 cursor-pointer items-center gap-2 rounded-[22px] border border-line bg-surface px-5 text-[14px] font-bold text-ink focus-within:ring-2 focus-within:ring-accent hover:bg-field-solid">
          <Upload size={18} aria-hidden />{file ? file.name : 'Choose a backup to restore'}
          <input type="file" accept=".db" className="sr-only" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
        </label>
        {file && <Button variant="danger" onClick={() => setConfirm(true)}>Restore…</Button>}
      </div>
      <ConfirmDialog
        open={confirm}
        onClose={() => setConfirm(false)}
        title="Restore this backup?"
        body="This replaces every account, drive connection, folder and setting on the server with the backup's contents, then restarts the server. Everyone is signed out."
        confirmLabel="Restore and restart"
        danger
        onConfirm={async () => {
          if (!file) return
          try {
            const r = await api.system.restore(file)
            toast.show(r.message || 'Restored. Signing out…', { tone: 'success' })
            window.setTimeout(() => {
              clearSession()
              window.location.href = '/login'
            }, 3000)
          } catch (e) {
            toast.error(errorMessage(e, 'Restore failed'))
            throw e
          }
        }}
      />
    </div>
  )
}

/** One server update, from the click to the restart. The dialog only shows it; closing it doesn't stop it. */
type UpdateState =
  | { kind: 'idle' }
  | { kind: 'starting'; log: string }
  | { kind: 'running'; log: string }
  | { kind: 'done'; log: string }
  | { kind: 'failed'; log: string }

function SystemUpdate() {
  const toast = useToast()
  const [update, setUpdate] = useState<UpdateState>({ kind: 'idle' })
  const [open, setOpen] = useState(false)
  const box = useRef<HTMLPreElement>(null)
  const log = update.kind === 'idle' ? '' : update.log
  const busy = update.kind === 'starting' || update.kind === 'running'

  const running = update.kind === 'running'
  useEffect(() => {
    if (!running) return
    let stopped = false
    let timer = 0
    const tick = async () => {
      try {
        const r = await api.system.updateLog()
        if (stopped) return
        if (r.log.includes('=== System Update Completed:')) return setUpdate({ kind: 'done', log: r.log })
        setUpdate((u) => (u.kind === 'running' ? { kind: 'running', log: r.log } : u))
      } catch {
        /* the server restarts during an update; keep polling */
      }
      if (!stopped) timer = window.setTimeout(tick, 2000)
    }
    timer = window.setTimeout(tick, 2000)
    return () => {
      stopped = true
      window.clearTimeout(timer)
    }
  }, [running])

  useEffect(() => {
    if (box.current) box.current.scrollTop = box.current.scrollHeight
  }, [log, open])

  async function start() {
    setOpen(true)
    // Already under way (or finished): just show it again, never post a second update.
    if (update.kind !== 'idle' && update.kind !== 'failed') return
    const first = 'Starting the update…\n'
    setUpdate({ kind: 'starting', log: first })
    try {
      const r = await api.system.update()
      setUpdate({ kind: 'running', log: `${first}${r.message}\n` })
    } catch (e) {
      setUpdate({ kind: 'failed', log: `${first}\n${errorMessage(e)}` })
      toast.error('The update could not start')
    }
  }

  const close = () => setOpen(false)

  return (
    <div className="grid grid-cols-1 gap-3">
      <div className="flex items-center gap-2"><RefreshCw size={18} className="text-accent" aria-hidden /><h3 className="text-[16px] font-semibold">Server update</h3></div>
      <p className="text-[13px] text-muted">Pull the latest code and restart (installs with an update script only; Docker installs are updated from the host).</p>
      <Button variant="secondary" icon={RefreshCw} onClick={start} className="justify-self-start">{busy ? 'Show update progress' : 'Update server'}</Button>
      <Dialog
        open={open}
        onClose={close}
        title={update.kind === 'done' ? 'Server updated' : update.kind === 'failed' ? 'The update could not start' : 'Updating server'}
        size="lg"
        footer={update.kind === 'done' ? <Button onClick={() => window.location.reload()}>Reload page</Button> : <Button variant="secondary" onClick={close}>Close</Button>}
      >
        <pre ref={box} aria-live="polite" className="h-72 overflow-auto whitespace-pre-wrap rounded-[16px] bg-[#131218] p-4 font-mono text-[12.5px] text-[#f4f2fa]">{log}</pre>
      </Dialog>
    </div>
  )
}
