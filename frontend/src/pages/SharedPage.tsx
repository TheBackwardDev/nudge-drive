import { useState, type CSSProperties, type ReactNode } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import clsx from 'clsx'
import { Copy, ExternalLink, FileText, Folder, Inbox, Link2, Link2Off, Send, Share2, UserX } from 'lucide-react'
import { ConfirmDialog } from '@/components/ui/Dialog'
import { EmptyState, Pill, Shimmer } from '@/components/ui/Surface'
import { FileThumb } from '@/components/files/FileVisuals'
import { PageHeader } from '@/components/layout/PageHeader'
import { useFileActions } from '@/context/FileActions'
import { useToast } from '@/context/ToastContext'
import { api, errorMessage } from '@/lib/api'
import { copyText, emitChange, useLoad, useOnChange } from '@/lib/hooks'
import { formatBytes, friendlyDate } from '@/lib/format'
import type { Invite, SharedLink } from '@/lib/types'

type Tab = 'links' | 'sent' | 'received'

async function loadShared() {
  const [links, invites] = await Promise.all([api.files.sharedLinks(), api.invites.list()])
  return { links: links.shares, sent: invites.sent.filter((i) => i.status !== 'revoked'), received: invites.received }
}

/** Public links you made, invites you sent, and things shared with you. */
export default function SharedPage() {
  const [params, setParams] = useSearchParams()
  const tab = (['links', 'sent', 'received'].includes(params.get('tab') ?? '') ? params.get('tab') : 'links') as Tab
  const { data, loading, error, reload } = useLoad(loadShared)
  useOnChange(['shares'], reload)
  const [revoking, setRevoking] = useState<Invite | null>(null)
  const toast = useToast()

  const tabs: { key: Tab; label: string; count?: number; icon: typeof Link2 }[] = [
    { key: 'links', label: 'Links', count: data?.links.length, icon: Link2 },
    { key: 'sent', label: 'Invites sent', count: data?.sent.length, icon: Send },
    { key: 'received', label: 'Received', count: data?.received.length, icon: Inbox },
  ]

  return (
    <div>
      <PageHeader title="Shared" subtitle="Public links and invites" back="/files" />
      <div role="tablist" aria-label="Shared" className="no-scrollbar -mx-5 mb-5 flex gap-2 overflow-x-auto px-5 sm:mx-0 sm:px-0">
        {tabs.map((t) => (
          <button
            key={t.key}
            type="button"
            role="tab"
            aria-selected={tab === t.key}
            onClick={() => setParams(t.key === 'links' ? {} : { tab: t.key }, { replace: true })}
            className={clsx('press inline-flex h-10 shrink-0 items-center gap-2 rounded-[20px] border px-4 text-[13.5px] font-semibold', tab === t.key ? 'border-inverse bg-inverse text-on-inverse' : 'glass text-ink')}
          >
            <t.icon size={16} aria-hidden />{t.label}{t.count !== undefined && <span className="opacity-70">{t.count}</span>}
          </button>
        ))}
      </div>

      {loading && !data ? (
        <div className="grid grid-cols-1 gap-2.5">{[0, 1, 2].map((i) => <Shimmer key={i} className="h-[78px] rounded-[22px]" />)}</div>
      ) : error && !data ? (
        <p role="alert" className="rounded-[18px] bg-danger-soft p-4 text-[14px] text-danger">{error}</p>
      ) : tab === 'links' ? (
        <Links links={data?.links ?? []} />
      ) : tab === 'sent' ? (
        (data?.sent.length ?? 0) === 0 ? (
          <EmptyState icon={Send} title="No invites sent" body="Invite someone from a file's or folder's menu." />
        ) : (
          <ul className="stagger grid grid-cols-1 gap-2.5 md:grid-cols-2">
            {data!.sent.map((inv) => (
              <InviteRow key={inv.id} invite={inv} sub={`Shared with ${inv.email}`} action={<button type="button" onClick={() => setRevoking(inv)} className="press inline-flex h-9 items-center gap-1.5 rounded-[18px] bg-danger-soft px-3 text-[13px] font-bold text-danger"><UserX size={15} aria-hidden />Revoke</button>} />
            ))}
          </ul>
        )
      ) : (data?.received.length ?? 0) === 0 ? (
        <EmptyState icon={Inbox} title="Nothing received" body="When a friend invites your email to a file or folder, it shows up here." />
      ) : (
        <ul className="stagger grid grid-cols-1 gap-2.5 md:grid-cols-2">
          {data!.received.map((inv) => <InviteRow key={inv.id} invite={inv} sub={`${inv.role === 'editor' ? 'Can edit' : 'Can view'} · from ${inv.user?.name ?? 'someone'}`} />)}
        </ul>
      )}

      <ConfirmDialog
        open={!!revoking}
        onClose={() => setRevoking(null)}
        title="Revoke this invite?"
        body={revoking ? `${revoking.email} will lose access to “${revoking.target?.name ?? 'this item'}”.` : undefined}
        confirmLabel="Revoke"
        danger
        onConfirm={async () => {
          if (!revoking) return
          try {
            await api.invites.revoke(revoking.id)
            toast.show('Invite revoked')
            emitChange('shares')
          } catch (e) {
            toast.error(errorMessage(e))
            throw e
          }
        }}
      />
    </div>
  )
}

function Links({ links }: { links: SharedLink[] }) {
  const toast = useToast()
  const actions = useFileActions()
  const navigate = useNavigate()
  if (links.length === 0) {
    return <EmptyState icon={Link2} title="No public links" body="Open a file's menu and choose Share link to create one." action={<button type="button" onClick={() => navigate('/files')} className="press rounded-[20px] bg-primary px-5 py-2.5 text-[14px] font-bold text-white">Go to My files</button>} />
  }
  const canShare = typeof navigator.share === 'function'
  async function turnOff(l: SharedLink) {
    try {
      await api.files.unshare(l.file.id)
      emitChange('shares')
      toast.show('Link turned off')
    } catch (e) {
      toast.error(errorMessage(e))
    }
  }
  return (
    <ul className="stagger grid grid-cols-1 gap-2.5 md:grid-cols-2">
      {links.map((l) => (
        <li key={l.id} className="flex items-center gap-2 rounded-[22px] border border-card-border bg-surface p-3 shadow-soft transition-colors hover:border-lilac">
          <button type="button" onClick={() => actions.open(l.file)} aria-label={`Open ${l.file.name}`} className="press shrink-0"><FileThumb file={l.file} size={48} radius={15} /></button>
          <div className="min-w-0 flex-1">
            <p className="truncate text-[14px] font-semibold text-ink">{l.file.name}</p>
            <p className="truncate text-[12px] text-muted">{formatBytes(l.file.sizeBytes)} · shared {friendlyDate(l.createdAt)}</p>
            {l.url && <p className="mt-1 inline-block max-w-full truncate rounded-full bg-mist px-2.5 py-0.5 font-mono text-[11px] text-link">{l.url.replace(/^https?:\/\//, '')}</p>}
          </div>
          {l.url && (
            <>
              <button type="button" aria-label={`Copy link to ${l.file.name}`} onClick={async () => { if (await copyText(l.url!)) toast.show('Link copied', { tone: 'success' }) }} className="press flex h-10 w-10 items-center justify-center rounded-full text-link hover:bg-mist"><Copy size={18} aria-hidden /></button>
              {canShare && (
                <button type="button" aria-label={`Share ${l.file.name}`} onClick={() => void navigator.share({ title: l.file.name, url: l.url! }).catch(() => undefined)} className="press flex h-10 w-10 items-center justify-center rounded-full text-ink hover:bg-mist"><Share2 size={18} aria-hidden /></button>
              )}
              <a href={l.url} target="_blank" rel="noreferrer" aria-label={`Open public page for ${l.file.name}`} className="press flex h-10 w-10 items-center justify-center rounded-full text-ink hover:bg-mist max-sm:hidden"><ExternalLink size={18} aria-hidden /></a>
            </>
          )}
          <button type="button" aria-label={`Turn off link for ${l.file.name}`} onClick={() => void turnOff(l)} className="press flex h-10 w-10 items-center justify-center rounded-full text-danger hover:bg-danger-soft"><Link2Off size={18} aria-hidden /></button>
        </li>
      ))}
    </ul>
  )
}

function InviteRow({ invite, sub, action }: { invite: Invite; sub: string; action?: ReactNode }) {
  const Icon = invite.targetType === 'folder' ? Folder : FileText
  return (
    <li className="flex items-center gap-3 rounded-[22px] border border-card-border bg-surface p-3 shadow-soft">
      <span className="soft-tile flex h-12 w-12 shrink-0 items-center justify-center rounded-[15px]" style={{ '--k': invite.targetType === 'folder' ? '#f59e0b' : '#8b72f5' } as CSSProperties}><Icon size={22} aria-hidden /></span>
      <div className="min-w-0 flex-1">
        <p className="truncate text-[14px] font-semibold text-ink">{invite.target?.name ?? 'Item no longer available'}</p>
        <p className="truncate text-[12px] text-muted">{sub}</p>
        <div className="mt-1 flex flex-wrap gap-1.5">
          <Pill>{invite.role === 'editor' ? 'Editor' : 'Viewer'}</Pill>
          <Pill tone={invite.status === 'accepted' ? 'success' : 'warn'}>{invite.status === 'accepted' ? 'Joined' : 'Pending'}</Pill>
          {invite.target?.sizeBytes && <Pill>{formatBytes(invite.target.sizeBytes)}</Pill>}
        </div>
      </div>
      {action}
    </li>
  )
}
