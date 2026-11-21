import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Check, Copy, ExternalLink, Link2, Link2Off, Mail, UserPlus, X } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Dialog } from '@/components/ui/Dialog'
import { Spinner } from '@/components/ui/Spinner'
import { TextField } from '@/components/ui/Field'
import { Pill, Segmented } from '@/components/ui/Surface'
import { DriveGlyph } from '@/components/brand/Brand'
import { useToast } from '@/context/ToastContext'
import { api, errorMessage } from '@/lib/api'
import { copyText, emitChange } from '@/lib/hooks'
import { friendlyDate } from '@/lib/format'
import type { FileItem, Invite } from '@/lib/types'

/** Public link for a file: create, copy, turn off; plus Google Drive's own link for Drive files. */
export function ShareDialog({ file, onClose }: { file: FileItem | null; onClose: () => void }) {
  return (
    <Dialog open={!!file} onClose={onClose} title="Share link" description={file?.name}>
      {file && <ShareBody file={file} onClose={onClose} />}
    </Dialog>
  )
}

function ShareBody({ file, onClose }: { file: FileItem; onClose: () => void }) {
  const toast = useToast()
  const [url, setUrl] = useState<string | null>(null)
  const [error, setError] = useState('')
  const [copied, setCopied] = useState(false)
  const [driveUrl, setDriveUrl] = useState<string | null>(null)
  const [busy, setBusy] = useState<'off' | 'drive' | null>(null)

  useEffect(() => {
    let alive = true
    api.files.share(file.id)
      .then((r) => {
        if (!alive) return
        setUrl(r.url)
        emitChange('shares')
      })
      .catch((e) => alive && setError(errorMessage(e)))
    return () => {
      alive = false
    }
  }, [file.id])

  async function copy(value: string) {
    if (await copyText(value)) {
      setCopied(true)
      toast.show('Link copied', { tone: 'success' })
      window.setTimeout(() => setCopied(false), 1800)
    }
  }

  async function turnOff() {
    setBusy('off')
    try {
      await api.files.unshare(file.id)
      emitChange('shares')
      toast.show('Public link turned off')
      onClose()
    } catch (e) {
      toast.error(errorMessage(e))
    } finally {
      setBusy(null)
    }
  }

  async function makeDrivePublic() {
    setBusy('drive')
    try {
      const r = await api.files.makeDrivePublic(file.id)
      setDriveUrl(r.url)
      if (await copyText(r.url)) toast.show('Google Drive link copied', { tone: 'success' })
    } catch (e) {
      toast.error(errorMessage(e))
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="grid grid-cols-1 gap-5">
      <section className="grid grid-cols-1 gap-3">
        <div className="flex items-center gap-2">
          <Link2 size={18} className="text-accent" aria-hidden />
          <h3 className="flex-1 text-[15px] font-semibold">Public link</h3>
          {url && <Pill tone="success">On</Pill>}
        </div>
        <p className="text-[13px] text-muted">Anyone with this link can view and download the file. No sign-in needed.</p>
        {error ? (
          <p role="alert" className="rounded-[16px] bg-danger-soft px-4 py-3 text-[13px] text-danger">{error}</p>
        ) : (
          <div className="flex items-center gap-2 rounded-[22px] border border-line bg-field-solid py-1.5 pl-4 pr-1.5">
            <input readOnly value={url ?? 'Creating link…'} aria-label="Public link" className="min-w-0 flex-1 bg-transparent text-[13.5px] text-ink outline-none" onFocus={(e) => e.currentTarget.select()} />
            <Button size="sm" icon={copied ? Check : Copy} disabled={!url} onClick={() => url && copy(url)}>{copied ? 'Copied' : 'Copy'}</Button>
          </div>
        )}
        <div className="flex flex-wrap gap-2">
          {url && <a href={url} target="_blank" rel="noreferrer" className="press inline-flex h-9 items-center gap-1.5 rounded-[18px] bg-mist px-3.5 text-[13px] font-bold text-link"><ExternalLink size={15} aria-hidden />Open</a>}
          {url && <Button size="sm" variant="danger" icon={Link2Off} loading={busy === 'off'} onClick={turnOff}>Turn off link</Button>}
        </div>
      </section>

      {file.provider === 'google_drive' && (
        <section className="grid grid-cols-1 gap-3 border-t border-line pt-5">
          <div className="flex items-center gap-2">
            <DriveGlyph size={17} />
            <h3 className="text-[15px] font-semibold">Google Drive link</h3>
          </div>
          <p className="text-[13px] text-muted">
            Opens the file on drive.google.com. This changes the file's sharing on Google Drive so <strong className="text-ink">anyone with the link can edit it</strong>.
          </p>
          {driveUrl ? (
            <div className="flex items-center gap-2 rounded-[22px] border border-line bg-field-solid py-1.5 pl-4 pr-1.5">
              <input readOnly value={driveUrl} aria-label="Google Drive link" className="min-w-0 flex-1 bg-transparent text-[13.5px] text-ink outline-none" onFocus={(e) => e.currentTarget.select()} />
              <Button size="sm" variant="secondary" icon={Copy} onClick={() => copy(driveUrl)}>Copy</Button>
            </div>
          ) : (
            <Button variant="secondary" loading={busy === 'drive'} onClick={makeDrivePublic} className="justify-self-start">Make public on Drive & copy link</Button>
          )}
        </section>
      )}
    </div>
  )
}

export type InviteTarget = { type: 'file' | 'folder'; id: string; name: string }

/** Invite someone by email to a file or folder, and see / revoke who already has access. */
export function InviteDialog({ target, onClose }: { target: InviteTarget | null; onClose: () => void }) {
  return (
    <Dialog open={!!target} onClose={onClose} title="Invite people" description={target ? `${target.type === 'folder' ? 'Folder' : 'File'} · ${target.name}` : undefined}>
      {target && <InvitePanel target={target} />}
    </Dialog>
  )
}

export function InvitePanel({ target, compact }: { target: InviteTarget; compact?: boolean }) {
  const toast = useToast()
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<'viewer' | 'editor'>('viewer')
  const [busy, setBusy] = useState(false)
  const [invites, setInvites] = useState<Invite[]>([])
  /** The invite being revoked; one at a time. */
  const [revoking, setRevoking] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      const { sent } = await api.invites.list()
      setInvites(sent.filter((i) => i.targetId === target.id && i.status !== 'revoked'))
    } catch {
      /* the list is a nice-to-have */
    }
  }, [target.id])

  useEffect(() => {
    void load()
  }, [load])

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    try {
      await api.invites.create(email.trim(), role, target.type, target.id)
      toast.show(`Invited ${email.trim()} as ${role === 'editor' ? 'editor' : 'viewer'}`, { tone: 'success' })
      setEmail('')
      await load()
    } catch (err) {
      toast.error(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  async function revoke(inv: Invite) {
    if (revoking) return
    setRevoking(inv.id)
    try {
      await api.invites.revoke(inv.id)
      toast.show('Invite revoked')
      await load()
    } catch (err) {
      toast.error(errorMessage(err))
    } finally {
      setRevoking(null)
    }
  }

  return (
    <div className="grid grid-cols-1 gap-4">
      <form onSubmit={submit} className="grid grid-cols-1 gap-3">
        <TextField label="Email address" type="email" placeholder="friend@gmail.com" icon={Mail} value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="email" />
        <div className="flex flex-wrap items-center justify-between gap-3">
          <Segmented label="Access" value={role} onChange={setRole} options={[{ value: 'viewer', label: 'Can view' }, { value: 'editor', label: 'Can edit' }]} />
          <Button type="submit" icon={UserPlus} loading={busy} disabled={!email.trim()}>Invite</Button>
        </div>
      </form>
      {invites.length > 0 && (
        <div className="grid grid-cols-1 gap-2">
          {!compact && <h3 className="text-[13px] font-semibold text-muted">People with access</h3>}
          <ul className="grid grid-cols-1 gap-1.5">
            {invites.map((inv) => (
              <li key={inv.id} className="flex items-center gap-3 rounded-[16px] bg-mist px-3 py-2">
                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-surface font-display text-[13px] font-bold text-ink">{(inv.user?.name ?? inv.email).charAt(0).toUpperCase()}</span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-[13.5px] font-semibold text-ink">{inv.user?.name ?? inv.email}</span>
                  <span className="block truncate text-[12px] text-muted">{inv.role === 'editor' ? 'Can edit' : 'Can view'} · {inv.status === 'accepted' ? 'Joined' : 'Invited'} {friendlyDate(inv.createdAt)}</span>
                </span>
                <button type="button" onClick={() => revoke(inv)} disabled={revoking !== null} aria-busy={revoking === inv.id || undefined} aria-label={`Revoke access for ${inv.email}`} className="press flex h-9 w-9 items-center justify-center rounded-full text-muted hover:bg-danger-soft hover:text-danger disabled:opacity-50">
                  {revoking === inv.id ? <Spinner size={17} /> : <X size={17} aria-hidden />}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
