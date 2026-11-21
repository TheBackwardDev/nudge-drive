import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import clsx from 'clsx'
import { CircleCheck, CloudDownload, Layers, PartyPopper, Plus, ShieldCheck, Zap } from 'lucide-react'
import { DriveGlyph, LogoTile } from '@/components/brand/Brand'
import { Button } from '@/components/ui/Button'
import { Spinner } from '@/components/ui/Spinner'
import { setOnboarded, useAuth } from '@/context/AuthContext'
import { useToast } from '@/context/ToastContext'
import { api, errorMessage } from '@/lib/api'
import { useConnectGoogle } from '@/lib/connectGoogle'
import { formatBytes, plural, toNumber } from '@/lib/format'
import { useDocumentTitle, useLoad, useOnChange } from '@/lib/hooks'
import type { ImportStatus } from '@/lib/types'

const FEATURES = [
  { icon: Layers, title: 'One pool for every drive', body: '15 GB free per Google account, added together' },
  { icon: Zap, title: 'Smart uploads', body: 'Each file goes to the drive with the most room' },
  { icon: ShieldCheck, title: 'Yours, not ours', body: 'Files stay in your own Google Drive or S3 bucket' },
]

/** First-run tour (once per user): welcome → connect a drive → import existing files → done. */
export default function WelcomePage() {
  useDocumentTitle('Welcome')
  const navigate = useNavigate()
  const toast = useToast()
  const { user } = useAuth()
  const [step, setStep] = useState(0)
  const { data, reload } = useLoad(() => api.accounts.list())
  useOnChange(['accounts'], reload)
  const accounts = data?.accounts ?? []
  const googles = accounts.filter((a) => a.provider === 'google_drive')
  const google = useConnectGoogle((ok, err) => (ok ? toast.show('Google Drive connected', { tone: 'success' }) : toast.error(err ?? 'Not connected')))
  const [imports, setImports] = useState<Record<string, ImportStatus>>({})
  /** Accounts whose "Import" was clicked and whose import hasn't answered yet. */
  const [starting, setStarting] = useState<ReadonlySet<string>>(() => new Set())

  const runningKey = Object.entries(imports).filter(([, s]) => s.state === 'running').map(([id]) => id).join(',')
  useEffect(() => {
    if (!runningKey) return
    let stopped = false
    let timer = 0
    const tick = async () => {
      for (const id of runningKey.split(',')) {
        try {
          const s = await api.accounts.importStatus(id)
          if (stopped) return
          // A late "running" never undoes a finished import.
          setImports((m) => (s.state === 'running' && (m[id]?.state === 'done' || m[id]?.state === 'failed') ? m : { ...m, [id]: s }))
        } catch {
          /* try again next tick */
        }
      }
      if (!stopped) timer = window.setTimeout(tick, 2500)
    }
    timer = window.setTimeout(tick, 2500)
    return () => {
      stopped = true
      window.clearTimeout(timer)
    }
  }, [runningKey])

  function finish() {
    if (user) setOnboarded(user.id)
    navigate('/home', { replace: true })
  }

  async function startImport(id: string) {
    if (starting.has(id)) return
    setStarting((cur) => new Set(cur).add(id))
    try {
      const s = await api.accounts.importDrive(id)
      setImports((m) => ({ ...m, [id]: s }))
    } catch (e) {
      toast.error(errorMessage(e))
    } finally {
      setStarting((cur) => {
        const next = new Set(cur)
        next.delete(id)
        return next
      })
    }
  }

  const total = accounts.reduce((n, a) => n + toNumber(a.storageAccount?.totalBytes), 0)
  const first = user?.name?.split(' ')[0] ?? 'there'

  return (
    <div className="flex min-h-dvh flex-col">
      <div className="auth-bg" aria-hidden />
      <div className="mx-auto flex w-full max-w-[520px] flex-1 flex-col px-5 pb-8 pt-[max(20px,env(safe-area-inset-top))]">
        <div className="flex items-center justify-between">
          <div className="flex gap-1.5" aria-label={`Step ${step + 1} of 4`} role="progressbar" aria-valuemin={1} aria-valuemax={4} aria-valuenow={step + 1}>
            {[0, 1, 2, 3].map((i) => <span key={i} className={clsx('h-2 rounded-full transition-all', i === step ? 'w-7 bg-primary' : i < step ? 'w-2 bg-iris' : 'w-2 bg-lilac')} />)}
          </div>
          {step < 3 && <button type="button" onClick={finish} className="press rounded-xl px-3 py-2 text-[14px] font-semibold text-link hover:bg-hover">Skip</button>}
        </div>

        <main key={step} className="rise flex flex-1 flex-col justify-center py-8">
          {step === 0 && (
            <>
              <LogoTile size={84} />
              <h1 className="mt-7 text-[34px] font-bold leading-[1.1] tracking-[-0.04em]">Welcome, {first}!</h1>
              <p className="mt-3 text-[16px] text-muted">Nudge Drive joins all your Google Drives into one big storage pool.</p>
              <ul className="mt-7 grid grid-cols-1 gap-3">
                {FEATURES.map((f) => (
                  <li key={f.title} className="glass flex items-center gap-4 rounded-[22px] p-4">
                    <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[14px] bg-primary text-white"><f.icon size={21} aria-hidden /></span>
                    <span><span className="block text-[15px] font-semibold">{f.title}</span><span className="block text-[13.5px] text-muted">{f.body}</span></span>
                  </li>
                ))}
              </ul>
            </>
          )}

          {step === 1 && (
            <>
              <span className="flex h-20 w-20 items-center justify-center rounded-[26px] bg-surface shadow-soft"><DriveGlyph size={40} /></span>
              <h1 className="mt-7 text-[30px] font-bold leading-tight tracking-[-0.03em]">Connect your Google Drive</h1>
              <p className="mt-3 text-[15px] text-muted">Pick an account on Google's page and you'll come right back. Add as many as you like.</p>
              <ul className="mt-6 grid grid-cols-1 gap-2.5">
                {accounts.map((a) => (
                  <li key={a.id} className="glass flex items-center gap-3 rounded-[20px] p-3.5">
                    <CircleCheck size={22} className="text-success" aria-hidden />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-[14.5px] font-semibold">{a.displayName || a.email}</span>
                      <span className="block text-[12.5px] text-muted">{a.storageAccount?.totalBytes ? `${formatBytes(Math.max(0, toNumber(a.storageAccount.totalBytes) - toNumber(a.storageAccount.usedBytes)))} free` : 'Connected'}</span>
                    </span>
                  </li>
                ))}
              </ul>
              <Button variant={accounts.length ? 'secondary' : 'primary'} size="lg" icon={accounts.length ? Plus : undefined} loading={google.busy} onClick={() => void google.connect()} className="mt-4">
                {accounts.length ? 'Add another account' : 'Connect Google Drive'}
              </Button>
            </>
          )}

          {step === 2 && (
            <>
              <span className="flex h-20 w-20 items-center justify-center rounded-[26px] bg-primary text-white shadow-soft"><CloudDownload size={38} aria-hidden /></span>
              <h1 className="mt-7 text-[30px] font-bold leading-tight tracking-[-0.03em]">Bring in your existing files?</h1>
              <p className="mt-3 text-[15px] text-muted">Show everything already in your Google Drive inside Nudge Drive, folders and all. Nothing is moved or copied.</p>
              {googles.length === 0 ? (
                <p className="mt-6 rounded-[18px] bg-mist p-4 text-[14px] text-muted">Connect a Google Drive first to import its files.</p>
              ) : (
                <ul className="mt-6 grid grid-cols-1 gap-2.5">
                  {googles.map((a) => {
                    const s = imports[a.id]
                    return (
                      <li key={a.id} className="glass flex items-center gap-3 rounded-[20px] p-3.5">
                        <DriveGlyph size={22} />
                        <span className="min-w-0 flex-1">
                          <span className="block truncate text-[14.5px] font-semibold">{a.email}</span>
                          <span className="block text-[12.5px] text-muted">
                            {s?.state === 'running' ? `Importing… ${s.folders} folders, ${s.created} files so far` : s?.state === 'done' ? `Done · ${plural(s.created, 'file')} added` : s?.state === 'failed' ? 'Import failed' : 'Not imported yet'}
                          </span>
                        </span>
                        {s?.state === 'running' ? <Spinner size={20} className="text-accent" label="Importing" /> : s?.state === 'done' ? <CircleCheck size={22} className="text-success" aria-label="Imported" /> : (
                          <Button size="sm" loading={starting.has(a.id)} onClick={() => void startImport(a.id)}>{s?.state === 'failed' ? 'Retry' : 'Import'}</Button>
                        )}
                      </li>
                    )
                  })}
                </ul>
              )}
              <p className="mt-4 text-[12.5px] text-subtle">Tip: deleting an imported folder in Nudge Drive also deletes it from Google Drive.</p>
            </>
          )}

          {step === 3 && (
            <div className="text-center">
              <span className="mx-auto flex h-24 w-24 items-center justify-center rounded-[30px] bg-success-soft text-success"><PartyPopper size={44} aria-hidden /></span>
              <h1 className="mt-7 text-[32px] font-bold tracking-[-0.03em]">You're all set!</h1>
              <p className="mt-3 text-[15px] text-muted">
                {accounts.length ? `${plural(accounts.length, 'drive')} connected · ${formatBytes(total)} in your pool.` : 'You can connect a drive any time from the Drives page.'}
              </p>
            </div>
          )}
        </main>

        <div className="grid grid-cols-1 gap-2">
          {step < 3 ? (
            <Button size="lg" block onClick={() => setStep(step + 1)}>
              {step === 0 ? "Let's set it up" : step === 1 ? (accounts.length ? 'Continue' : 'Skip for now') : runningKey ? 'Continue (keeps importing)' : 'Continue'}
            </Button>
          ) : (
            <Button size="lg" block onClick={finish}>Go to my drive</Button>
          )}
          {step > 0 && step < 3 && <Button variant="ghost" block onClick={() => setStep(step - 1)}>Back</Button>}
        </div>
      </div>
    </div>
  )
}
