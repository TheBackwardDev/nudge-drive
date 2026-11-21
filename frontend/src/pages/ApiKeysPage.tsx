import { useState, type CSSProperties, type FormEvent } from 'react'
import { Check, Copy, KeyRound, Plus, ShieldCheck, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog, Dialog } from '@/components/ui/Dialog'
import { TextField } from '@/components/ui/Field'
import { EmptyState, Panel, Pill, SectionTitle, Shimmer } from '@/components/ui/Surface'
import { PageHeader } from '@/components/layout/PageHeader'
import { useToast } from '@/context/ToastContext'
import { API_URL, api, errorMessage } from '@/lib/api'
import { copyText, useLoad } from '@/lib/hooks'
import { friendlyDate, shortDate } from '@/lib/format'
import type { ApiKey } from '@/lib/types'

const endpoint = () => `${window.location.origin}${API_URL}/api/v1/uploads`

const curlExample = () => `curl -X POST "${endpoint()}" \\
  -H "Authorization: Bearer 9d_live_xxx" \\
  -F 'filesMeta=[{"fieldName":"file-0","fileName":"hello.txt","mimeType":"text/plain","sizeBytes":"12"}]' \\
  -F "file-0=@hello.txt;type=text/plain"`

const jsExample = () => `const form = new FormData()
form.append('filesMeta', JSON.stringify([
  { fieldName: 'file-0', fileName: file.name, mimeType: file.type, sizeBytes: String(file.size) },
]))
form.append('file-0', file)

await fetch('${endpoint()}', {
  method: 'POST',
  headers: { Authorization: 'Bearer 9d_live_xxx' },
  body: form,
})`

/** Keys for uploading from scripts and other apps, plus the upload API docs. */
export default function ApiKeysPage() {
  const toast = useToast()
  const { data, loading, reload } = useLoad(() => api.apiKeys.list())
  const [creating, setCreating] = useState(false)
  const [secret, setSecret] = useState<string | null>(null)
  const [revoking, setRevoking] = useState<ApiKey | null>(null)
  const keys = data?.apiKeys ?? []

  async function copy(text: string, what: string) {
    if (await copyText(text)) toast.show(`${what} copied`, { tone: 'success' })
  }

  return (
    <div className="grid grid-cols-1 gap-6">
      <PageHeader title="API keys" subtitle="Upload from scripts, CI and other apps" back="/profile" actions={<Button icon={Plus} onClick={() => setCreating(true)}>New key</Button>} className="!pb-0" />

      <section aria-labelledby="keys-h" className="grid grid-cols-1 gap-3">
        <SectionTitle title={<span id="keys-h">Your keys</span>} />
        {loading && !data ? (
          <Shimmer className="h-[76px] rounded-[22px]" />
        ) : keys.length === 0 ? (
          <EmptyState icon={KeyRound} title="No API keys" body="Create a key to upload from scripts, CI or other apps. Keys can only upload files." action={<Button icon={Plus} onClick={() => setCreating(true)}>New key</Button>} />
        ) : (
          <ul className="stagger grid grid-cols-1 gap-2.5 lg:grid-cols-2">
            {keys.map((k) => (
              <li key={k.id}>
                <Panel className="flex items-center gap-3 p-4">
                  <span className="soft-tile flex h-11 w-11 shrink-0 items-center justify-center rounded-[14px]" style={{ '--k': k.status === 'active' ? '#7059f0' : '#8a8699' } as CSSProperties}><KeyRound size={20} aria-hidden /></span>
                  <div className="min-w-0 flex-1">
                    <p className="flex items-center gap-2 text-[15px] font-semibold text-ink"><span className="truncate">{k.name}</span><Pill tone={k.status === 'active' ? 'success' : 'mist'}>{k.status}</Pill></p>
                    <p className="truncate font-mono text-[12px] text-muted">{k.keyPrefix}…</p>
                    <p className="text-[12px] text-muted">Created {shortDate(k.createdAt)} · last used {k.lastUsedAt ? friendlyDate(k.lastUsedAt) : 'never'}</p>
                  </div>
                  {k.status === 'active' && <Button size="sm" variant="danger" icon={Trash2} onClick={() => setRevoking(k)}>Revoke</Button>}
                </Panel>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section aria-labelledby="docs-h" className="grid grid-cols-1 gap-3">
        <SectionTitle title={<span id="docs-h">Upload endpoint</span>} />
        <Panel className="grid grid-cols-1 gap-4 p-4 sm:p-5">
          <p className="text-[14px] text-muted">Send multipart form data with the header <code className="rounded-md bg-mist px-1.5 py-0.5 text-[13px] text-ink">Authorization: Bearer &lt;key&gt;</code>. Files are routed to your drives like normal uploads.</p>
          <CodeBlock label="Endpoint" code={`POST ${endpoint()}`} onCopy={() => copy(endpoint(), 'Endpoint')} />
          <CodeBlock label="cURL" code={curlExample()} onCopy={() => copy(curlExample(), 'Example')} />
          <CodeBlock label="JavaScript" code={jsExample()} onCopy={() => copy(jsExample(), 'Example')} />
        </Panel>
      </section>

      <CreateKeyDialog open={creating} onClose={() => setCreating(false)} onCreated={(s) => { setSecret(s); void reload() }} />
      <Dialog
        open={!!secret}
        onClose={() => setSecret(null)}
        title="Copy your key now"
        description="This is the only time it's shown. Store it somewhere safe."
        footer={<Button onClick={() => setSecret(null)}>Done</Button>}
      >
        <div className="grid grid-cols-1 gap-3">
          <div className="flex items-center gap-3 rounded-[18px] bg-success-soft p-3 text-success"><ShieldCheck size={20} aria-hidden /><span className="text-[13px] font-semibold">Key created. It can only upload files.</span></div>
          <code className="block break-all rounded-[16px] bg-mist p-4 font-mono text-[13.5px] text-ink">{secret}</code>
          <Button icon={Copy} onClick={() => secret && copy(secret, 'Key')}>Copy key</Button>
        </div>
      </Dialog>
      <ConfirmDialog
        open={!!revoking}
        onClose={() => setRevoking(null)}
        title={`Revoke “${revoking?.name ?? ''}”?`}
        body="Scripts using this key will stop working immediately."
        confirmLabel="Revoke"
        danger
        onConfirm={async () => {
          if (!revoking) return
          try {
            await api.apiKeys.revoke(revoking.id)
            toast.show('Key revoked')
            await reload()
          } catch (e) {
            toast.error(errorMessage(e))
            throw e
          }
        }}
      />
    </div>
  )
}

function CodeBlock({ label, code, onCopy }: { label: string; code: string; onCopy: () => void }) {
  const [done, setDone] = useState(false)
  return (
    <div className="min-w-0">
      <div className="mb-1.5 flex items-center justify-between">
        <span className="text-[13px] font-semibold text-ink">{label}</span>
        <Button size="sm" variant="soft" icon={done ? Check : Copy} onClick={() => { onCopy(); setDone(true); window.setTimeout(() => setDone(false), 1500) }}>{done ? 'Copied' : 'Copy'}</Button>
      </div>
      <pre className="max-h-72 overflow-auto rounded-[16px] bg-[#131218] p-4 font-mono text-[12.5px] leading-relaxed text-[#f4f2fa]">{code}</pre>
    </div>
  )
}

function CreateKeyDialog({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: (secret: string) => void }) {
  const toast = useToast()
  const [name, setName] = useState('')
  const [busy, setBusy] = useState(false)
  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    try {
      const r = await api.apiKeys.create(name.trim())
      setName('')
      onClose()
      onCreated(r.secret)
    } catch (err) {
      toast.error(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }
  return (
    <Dialog open={open} onClose={onClose} title="New API key" size="sm">
      <form onSubmit={submit} className="grid grid-cols-1 gap-4">
        <TextField label="Key name" placeholder="Key name (e.g. Laptop backup)" value={name} onChange={(e) => setName(e.target.value)} required autoFocus maxLength={191} />
        <div className="flex justify-end gap-2.5">
          <Button variant="secondary" onClick={onClose} disabled={busy}>Cancel</Button>
          <Button type="submit" loading={busy} disabled={!name.trim()}>Create</Button>
        </div>
      </form>
    </Dialog>
  )
}
