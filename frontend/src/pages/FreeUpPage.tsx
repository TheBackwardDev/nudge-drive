import { useCallback, useEffect, useRef, useState, type CSSProperties, type Dispatch, type ReactNode, type SetStateAction } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import clsx from 'clsx'
import {
  CalendarDays, Check, ChevronDown, Circle, CircleAlert, CircleCheck, CircleDot, Clock, CloudOff, Database, ExternalLink, FileText,
  HardDriveDownload, Image, Images, Package, Pause, Plus, RefreshCw, Sparkles, Trash, Trash2, TriangleAlert, Video, X, type LucideIcon,
} from 'lucide-react'
import { DriveGlyph } from '@/components/brand/Brand'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/Dialog'
import { GlassCard, SectionTitle, Shimmer, SoftTile } from '@/components/ui/Surface'
import { Spinner } from '@/components/ui/Spinner'
import { PageHeader } from '@/components/layout/PageHeader'
import { useToast } from '@/context/ToastContext'
import { api, ApiError, errorMessage } from '@/lib/api'
import { useConnectGoogle } from '@/lib/connectGoogle'
import { formatBytes, shortDate, toNumber } from '@/lib/format'
import { emitChange } from '@/lib/hooks'
import { postNotice } from '@/lib/notices'
import type { FreeUpDestination, FreeUpJob, FreeUpPlan, PhotosSession } from '@/lib/types'

/**
 * Free up space on a full Google Drive (Android FreeUpScreen): shows what is using the 15 GB, offers the
 * quick win (empty the trash), moves the biggest files to the user's other drives, and copies Google
 * Photos out through Google's own picker. The server does the work; this page shows it live.
 */

const GB = 1024 ** 3
const MB = 1024 ** 2
const IRIS = '#8b72f5'

type Kind = { key: string; label: string; icon: LucideIcon; color: string }
const KINDS: Kind[] = [
  { key: 'videos', label: 'Videos', icon: Video, color: '#f59e0b' },
  { key: 'photos', label: 'Photos', icon: Image, color: '#1e9e57' },
  { key: 'documents', label: 'Documents', icon: FileText, color: '#2e64e0' },
  { key: 'other', label: 'Other files', icon: Package, color: '#a23fcf' },
]

const PHOTOS_URL = 'https://photos.google.com/'
const PHOTOS_BIN_URL = 'https://photos.google.com/trash'
const POPUP_NAME = 'nudge-google-photos'
const POPUP_FEATURES = 'width=1000,height=760'

const n = toNumber
const s = (count: number) => (count === 1 ? '' : 's')

/** The whole page: one of these at a time. `refreshError` is a failed refresh while a job is shown. */
type PageState =
  | { kind: 'loading' }
  | { kind: 'error'; error: string }
  | { kind: 'plan'; plan: FreeUpPlan }
  | { kind: 'job'; plan: FreeUpPlan; job: FreeUpJob; refreshError: string | null }

const fromPlan = (p: FreeUpPlan): PageState => (p.job ? { kind: 'job', plan: p, job: p.job, refreshError: null } : { kind: 'plan', plan: p })

export default function FreeUpPage() {
  const { accountId = '' } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const toast = useToast()
  const [page, setPage] = useState<PageState>({ kind: 'loading' })
  const pageRef = useRef(page)
  pageRef.current = page
  /** Jobs already announced as finished (a notice each, once). */
  const announced = useRef(new Set<string>())
  /** Only the latest plan request may set the page. */
  const loadGen = useRef(0)

  const goBack = useCallback(() => (location.key !== 'default' ? navigate(-1) : navigate('/drives')), [location.key, navigate])

  const load = useCallback(async () => {
    const gen = ++loadGen.current
    setPage((cur) => (cur.kind === 'error' ? { kind: 'loading' } : cur))
    try {
      const p = await api.freeUp.plan(accountId)
      if (gen === loadGen.current) setPage(fromPlan(p))
    } catch (e) {
      if (gen !== loadGen.current) return
      const error = errorMessage(e)
      const cur = pageRef.current
      if (cur.kind === 'plan') toast.error(error)
      setPage((c) => (c.kind === 'job' ? { ...c, refreshError: error } : c.kind === 'plan' ? c : { kind: 'error', error }))
    }
  }, [accountId, toast])

  useEffect(() => {
    setPage({ kind: 'loading' })
    void load()
  }, [load])

  const announce = useCallback((j: FreeUpJob) => {
    const cur = pageRef.current
    const email = cur.kind === 'job' || cur.kind === 'plan' ? cur.plan.account.email : undefined
    if (j.kind === 'photos') {
      const copied = j.doneFiles - j.skipped
      if (copied > 0) {
        postNotice({
          type: 'storage',
          title: `Copied ${copied} photos & videos from ${email ?? 'Google Photos'}`,
          body: "They're safe on your other drives. Delete them in Google Photos to free the space.",
          to: `/drives/${accountId}/free-up`,
        })
      }
    } else if (j.doneFiles > 0) {
      const freed = n(j.freedBytes) > 0 ? n(j.freedBytes) : n(j.movedBytes)
      postNotice({ type: 'storage', title: `Freed ${formatBytes(freed)} on ${email ?? 'your Drive'}`, body: `Moved ${j.doneFiles} files to your other drives.`, to: '/drives' })
    }
    emitChange('storage', 'accounts', 'files')
  }, [accountId])

  /** Every job update goes through here, so a job seen running is announced exactly once when it ends (poll or Stop). */
  const applyJob = useCallback((next: FreeUpJob) => {
    const cur = pageRef.current
    const wasRunning = cur.kind === 'job' && cur.job.id === next.id && cur.job.state === 'running'
    setPage((c) => (c.kind === 'job' || c.kind === 'plan' ? { kind: 'job', plan: c.plan, job: next, refreshError: null } : c))
    if (wasRunning && next.state !== 'running' && !announced.current.has(next.id)) {
      announced.current.add(next.id)
      announce(next)
    }
  }, [announce])

  // Live progress while the server moves / copies files. Keeps polling until the page no longer shows a running job.
  const running = page.kind === 'job' && page.job.state === 'running'
  useEffect(() => {
    if (!running) return
    let stopped = false
    let timer = 0
    const tick = async () => {
      try {
        const { job: next } = await api.freeUp.status(accountId)
        if (stopped) return
        if (next) applyJob(next)
        else {
          // The server forgot the job (restarted): show the plan again, or the job it has now.
          const p = await api.freeUp.plan(accountId)
          if (stopped) return
          setPage(fromPlan(p))
        }
      } catch (e) {
        if (stopped) return
        const error = errorMessage(e)
        setPage((c) => (c.kind === 'job' ? { ...c, refreshError: error } : c))
      }
      if (!stopped) timer = window.setTimeout(tick, 1500)
    }
    timer = window.setTimeout(tick, 1500)
    return () => {
      stopped = true
      window.clearTimeout(timer)
    }
  }, [running, accountId, applyJob])

  const plan = page.kind === 'plan' || page.kind === 'job' ? page.plan : null

  return (
    <div className="pb-6">
      <PageHeader title="Free up space" subtitle={plan?.account.email} back={goBack} backAlways />
      {page.kind === 'loading' && (
        <div className="grid grid-cols-1 gap-3.5 lg:grid-cols-2" aria-busy>
          <Shimmer className="h-[210px] rounded-[28px]" />
          <Shimmer className="h-[210px] rounded-[28px]" />
          <Shimmer className="h-[220px] rounded-[28px]" />
        </div>
      )}
      {page.kind === 'error' && (
        <div className="flex flex-col items-center px-6 py-12 text-center">
          <CloudOff size={42} className="text-muted" aria-hidden />
          <p role="alert" className="mt-3 max-w-sm text-[14px] text-muted">{page.error}</p>
          <Button variant="secondary" icon={RefreshCw} className="mt-4" onClick={() => void load()}>Try again</Button>
        </div>
      )}
      {page.kind === 'job' && (
        <JobView
          key="job"
          p={page.plan}
          j={page.job}
          refreshError={page.refreshError}
          onCancel={async () => {
            const { job: next } = await api.freeUp.cancel(accountId)
            if (next) applyJob(next)
          }}
          onDone={async () => {
            await api.freeUp.clear(accountId).catch(() => undefined)
            goBack()
          }}
          onMore={async () => {
            await api.freeUp.clear(accountId).catch(() => undefined)
            setPage({ kind: 'loading' })
            void load()
          }}
        />
      )}
      {page.kind === 'plan' && <PlanView key={page.plan.account.id} p={page.plan} reload={load} onStarted={applyJob} />}
    </div>
  )
}

// ------------------------------------------------------------------ Plan

function defaultSelection(p: FreeUpPlan) {
  const bytes = new Map(p.categories.map((c) => [c.key, n(c.bytes)]))
  const media = ['photos', 'videos'].filter((k) => (bytes.get(k) ?? 0) > 0)
  return new Set(media.length ? media : p.categories.filter((c) => n(c.bytes) > 0).map((c) => c.key))
}

/** What the plan screen is busy with. One thing at a time: every action button is off unless this is idle. */
type Activity =
  | { kind: 'idle' }
  | { kind: 'emptying' }
  | { kind: 'importStarting' }
  | { kind: 'importing' }
  | { kind: 'startingMove' }
  /** The Google Photos picker. `destinationId` is fixed when it opens, so a plan reload can't change where the copy goes. */
  | { kind: 'photos'; step: 'opening' | 'granting' | 'picking' | 'starting'; session: PhotosSession | null; destinationId: string | null; blocked: boolean }

type PhotosActivity = Extract<Activity, { kind: 'photos' }>

function PlanView({ p, reload, onStarted }: { p: FreeUpPlan; reload: () => Promise<void>; onStarted: (job: FreeUpJob) => void }) {
  const toast = useToast()
  const total = n(p.quota.totalBytes)
  const used = n(p.quota.usedBytes)
  const trashBytes = n(p.quota.driveTrashBytes) + n(p.nudgeTrash.bytes)
  const catBytes = new Map(p.categories.map((c) => [c.key, n(c.bytes)]))
  const catCount = new Map(p.categories.map((c) => [c.key, c.count]))

  /** null = the suggested kinds for the current plan. */
  const [picks, setPicks] = useState<Set<string> | null>(null)
  /** undefined = the suggested amount, null = everything selected. */
  const [amount, setAmount] = useState<number | null | undefined>(undefined)
  const [destChoice, setDestination] = useState<string | null>(null)
  const [activity, setActivity] = useState<Activity>(() => (p.importing?.state === 'running' ? { kind: 'importing' } : { kind: 'idle' }))
  const [confirmTrash, setConfirmTrash] = useState(false)

  // A fresh plan (after emptying the trash, an import or a new drive) keeps the choices; only what is gone drops out.
  const selected = new Set([...(picks ?? defaultSelection(p))].filter((k) => (catBytes.get(k) ?? 0) > 0))
  const destination = destChoice !== null && p.destinations.some((d) => d.id === destChoice) ? destChoice : null

  const selectedBytes = [...selected].reduce((sum, k) => sum + (catBytes.get(k) ?? 0), 0)
  // Suggested: enough to bring the Drive back under 80 %, so Gmail has room to breathe.
  const needed = total > 0 ? Math.max(0, used - Math.floor((total * 8) / 10)) : 0
  const amounts = [1, 2, 5, 10].map((x) => x * GB).filter((a) => a < selectedBytes)
  const suggested = needed > 0 ? amounts.find((a) => a >= needed) : undefined
  const chosen = amount === undefined ? (suggested ?? null) : amount
  const planned = chosen === null ? selectedBytes : Math.min(chosen, selectedBytes)
  const destFree = p.destinations.some((d) => d.availableBytes === null) ? null : p.destinations.reduce((sum, d) => sum + n(d.availableBytes), 0)
  const picked = destination ? p.destinations.find((d) => d.id === destination) : undefined
  const roomFor = destination ? (picked?.availableBytes != null ? n(picked.availableBytes) : null) : destFree
  const short = roomFor !== null && roomFor < planned

  // Most full accounts are full of Google Photos, which only the Photos picker can reach.
  const hasFiles = [...catBytes.values()].some((b) => b > 0)
  const photosWorth = n(p.quota.otherBytes) > 256 * MB
  const showMove = hasFiles || !photosWorth
  const known = n(p.knownBytes)
  const driveBytes = n(p.quota.driveBytes)
  const showImport = !p.account.fullSync && driveBytes > known + 50 * MB
  const preview = showMove ? p.largest.filter((f) => selected.has(f.category)).slice(0, 5) : []

  const google = useConnectGoogle((ok, err) => {
    if (!ok) return toast.error(err ?? 'Google Drive was not connected.')
    toast.show('Google Drive connected', { tone: 'success' })
    void reload()
  })
  const photos = usePhotosPicker(p, activity, setActivity, onStarted)
  const idle = activity.kind === 'idle' && !google.busy
  const importing = activity.kind === 'importing'

  // An import started elsewhere (the app, the Drives page) shows up in a reloaded plan.
  useEffect(() => {
    if (p.importing?.state === 'running') setActivity((a) => (a.kind === 'idle' ? { kind: 'importing' } : a))
  }, [p])

  useEffect(() => {
    if (!importing) return
    let stopped = false
    let timer = 0
    const tick = async () => {
      try {
        const st = await api.accounts.importStatus(p.account.id)
        if (stopped) return
        if (st.state !== 'running') {
          setActivity((a) => (a.kind === 'importing' ? { kind: 'idle' } : a))
          emitChange('files', 'folders', 'storage')
          return void reload()
        }
      } catch {
        /* try again */
      }
      if (!stopped) timer = window.setTimeout(tick, 2000)
    }
    timer = window.setTimeout(tick, 2000)
    return () => {
      stopped = true
      window.clearTimeout(timer)
    }
  }, [importing, p.account.id, reload])

  function toggle(key: string) {
    const next = new Set(selected)
    if (next.has(key)) next.delete(key)
    else next.add(key)
    setPicks(next)
    setAmount(undefined)
  }

  async function emptyTrash() {
    if (!idle) return
    setActivity({ kind: 'emptying' })
    try {
      const r = await api.freeUp.emptyTrash(p.account.id)
      toast.show(`Trash emptied${n(r.freedBytes) > 0 ? ` · ${formatBytes(r.freedBytes)} freed` : ''}`, { tone: 'success' })
      emitChange('files', 'storage', 'accounts')
      await reload()
    } catch (e) {
      toast.error(errorMessage(e))
    } finally {
      setActivity({ kind: 'idle' })
    }
  }

  async function startImport() {
    if (!idle) return
    setActivity({ kind: 'importStarting' })
    try {
      await api.accounts.importDrive(p.account.id)
      setActivity({ kind: 'importing' })
    } catch (e) {
      setActivity({ kind: 'idle' })
      toast.error(errorMessage(e))
    }
  }

  async function start() {
    if (!idle || selectedBytes === 0) return
    setActivity({ kind: 'startingMove' })
    try {
      const order = KINDS.map((k) => k.key).filter((k) => selected.has(k))
      const { job } = await api.freeUp.start(p.account.id, order, chosen, destination)
      setActivity({ kind: 'idle' })
      if (job) onStarted(job)
    } catch (e) {
      setActivity({ kind: 'idle' })
      toast.error(errorMessage(e))
    }
  }

  return (
    <div className="grid grid-cols-1 gap-3.5 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)] lg:items-start lg:gap-5">
      <div className="stagger grid grid-cols-1 gap-3.5">
        <HeroCard p={p} total={total} used={used} />
        <UsageBreakdown p={p} total={total} />
        {trashBytes > 0 && (
          <ActionCard icon={Trash2} tint="var(--warn)" title="Empty the trash first" body={`Frees about ${formatBytes(trashBytes)} right away. Files in the trash are deleted for good.`}>
            <Button variant="warn" size="lg" block icon={Trash2} loading={activity.kind === 'emptying'} disabled={!idle} onClick={() => setConfirmTrash(true)}>Empty trash · {formatBytes(trashBytes)}</Button>
          </ActionCard>
        )}
        {showImport && (
          <ActionCard
            icon={HardDriveDownload}
            tint={IRIS}
            title="Let Nudge see the whole Drive"
            body={`Nudge knows ${formatBytes(known)} of the ${formatBytes(driveBytes)} of files on this Drive. Import it so everything can be moved.`}
          >
            <Button size="lg" block icon={HardDriveDownload} loading={importing || activity.kind === 'importStarting'} disabled={!idle} onClick={() => void startImport()}>{importing ? 'Importing…' : 'Import this Drive'}</Button>
          </ActionCard>
        )}
      </div>

      <div className="grid grid-cols-1 gap-3.5">
        {photosWorth && (
          <>
            <SectionTitle title="Google Photos" className="pl-1 pt-2 lg:pt-0" />
            <PhotosCard
              p={p}
              photos={activity.kind === 'photos' ? activity : null}
              disabled={!idle}
              onBegin={() => void photos.begin(destination)}
              onOpen={photos.openAgain}
              onCancel={photos.cancel}
            />
            {p.photosCopied && <DeleteInPhotosCard p={p} count={p.photosCopied.count} takenFrom={p.photosCopied.takenFrom} takenTo={p.photosCopied.takenTo} />}
          </>
        )}
        {showMove && <SectionTitle title="Move files to your other drives" className={clsx('pl-1 pt-2', !photosWorth && 'lg:pt-0')} />}

        {p.destinations.length === 0 ? (
          <ActionCard icon={Plus} tint={IRIS} title="Add a drive to move files to" body="Connect another Google account (15 GB free each) or an S3 bucket. Then Nudge can move files there and empty this one.">
            <Button size="lg" block icon={Plus} loading={google.busy} disabled={activity.kind !== 'idle'} onClick={() => void google.connect()}>Connect Google Drive</Button>
          </ActionCard>
        ) : (
          <>
            {showMove && (
              <div role="group" aria-label="What to move" className="grid grid-cols-2 gap-2.5 sm:grid-cols-4 lg:grid-cols-2 2xl:grid-cols-4">
                {KINDS.map((k) => {
                  const bytes = catBytes.get(k.key) ?? 0
                  return <KindTile key={k.key} kind={k} bytes={bytes} count={catCount.get(k.key) ?? 0} selected={selected.has(k.key)} enabled={bytes > 0} onClick={() => toggle(k.key)} />
                })}
              </div>
            )}
            {showMove && selectedBytes > 0 && (
              <div>
                <p id="amount-h" className="mb-2 pl-1 text-[13px] font-semibold text-muted">How much to move</p>
                <div role="radiogroup" aria-labelledby="amount-h" className="flex flex-wrap gap-2">
                  {amounts.map((a) => <AmountChip key={a} label={formatBytes(a)} selected={chosen === a} suggested={needed > 0 && a === suggested} onClick={() => setAmount(a)} />)}
                  <AmountChip label={`All · ${formatBytes(selectedBytes)}`} selected={chosen === null} suggested={needed > 0 && suggested === undefined} onClick={() => setAmount(null)} />
                </div>
              </div>
            )}
            <div>
              <p id="dest-h" className="mb-2 pl-1 text-[13px] font-semibold text-muted">{showMove ? 'Move them to' : 'Copy them to'}</p>
              <GlassCard role="radiogroup" aria-labelledby="dest-h" className="grid grid-cols-1 gap-0.5 p-1.5">
                <DestRow title="Automatic" sub="Spread across the drives with the most free space" free={destFree} selected={destination === null} onClick={() => setDestination(null)} />
                {p.destinations.map((d) => (
                  <DestRow key={d.id} d={d} title={d.displayName || d.email} sub={d.email} free={d.availableBytes === null ? null : n(d.availableBytes)} selected={destination === d.id} onClick={() => setDestination(d.id)} />
                ))}
              </GlassCard>
            </div>
            {short && showMove && (
              <ActionCard
                icon={TriangleAlert}
                tint="var(--warn)"
                title="Not enough room for all of it"
                body={`Your other drives have ${formatBytes(roomFor)} free. Nudge will move as much as fits. Connect another Google account for 15 GB more.`}
              >
                <Button variant="secondary" size="lg" block icon={Plus} loading={google.busy} disabled={activity.kind !== 'idle'} onClick={() => void google.connect()}>Connect another Google account</Button>
              </ActionCard>
            )}
            {preview.length > 0 && (
              <div>
                <p className="mb-2 pl-1 text-[13px] font-semibold text-muted">Biggest files go first</p>
                <GlassCard className="px-4 py-2">
                  <ul>
                    {preview.map((f) => {
                      const k = KINDS.find((x) => x.key === f.category) ?? KINDS[KINDS.length - 1]
                      return (
                        <li key={f.id} className="flex items-center gap-3 py-[7px]">
                          <SoftTile color={k.color} size={32} radius={10}><k.icon size={16} aria-hidden /></SoftTile>
                          <span className="min-w-0 flex-1 truncate text-[14px] text-ink">{f.name}</span>
                          <span className="shrink-0 text-[12.5px] font-semibold text-muted">{formatBytes(f.sizeBytes)}</span>
                        </li>
                      )
                    })}
                  </ul>
                </GlassCard>
              </div>
            )}
            {showMove && (
              <div className="pt-1">
                <Button size="lg" block icon={Sparkles} loading={activity.kind === 'startingMove'} disabled={selectedBytes === 0 || !idle} onClick={() => void start()}>
                  {selectedBytes === 0 ? 'Pick what to move' : `Free up ${formatBytes(planned)}`}
                </Button>
                <p className="mx-2 mt-2.5 text-center text-[12.5px] text-muted">
                  Each file is copied to the other drive first and only then removed here. It keeps its folder, shares and links in Nudge.
                </p>
              </div>
            )}
          </>
        )}
      </div>

      <ConfirmDialog
        open={confirmTrash}
        onClose={() => setConfirmTrash(false)}
        title="Empty the trash?"
        body={`Everything in this Drive's trash (and Nudge's trash for it) is deleted for good. This frees about ${formatBytes(trashBytes)}.`}
        confirmLabel="Empty trash"
        danger
        onConfirm={() => {
          void emptyTrash()
        }}
      />
    </div>
  )
}

function HeroCard({ p, total, used }: { p: FreeUpPlan; total: number; used: number }) {
  const frac = total > 0 ? used / total : 0
  const color = frac >= 0.95 ? 'var(--danger)' : frac >= 0.85 ? 'var(--warn)' : IRIS
  const [status, body]: [string, string] =
    frac >= 0.99
      ? ['Full', "Gmail can't receive new email and Google Photos can't back up until space is freed."]
      : frac >= 0.85
        ? ['Almost full', 'Free some space now so Gmail and Google Photos keep working.']
        : ['Healthy', "There's room on this Drive. You can still move files off it."]
  return (
    <GlassCard strong className="p-5">
      <div className="flex items-center gap-2.5">
        <Avatar url={p.account.avatarUrl} size={34} />
        <div className="min-w-0 flex-1">
          <p className="truncate text-[15px] font-semibold text-ink">{p.account.displayName || p.account.email}</p>
          <p className="truncate text-[12.5px] text-muted">{p.account.email}</p>
        </div>
        <span className="shrink-0 rounded-[12px] px-2.5 py-1 text-[12px] font-semibold" style={{ color, background: `color-mix(in srgb, ${color} 14%, transparent)` }}>{status}</span>
      </div>
      <div className="mt-[18px] flex items-center gap-[18px]">
        <BigRing fraction={frac} color={color} size={116} label={`${Math.round(Math.min(1, frac) * 100)} percent used`}>
          <span className="font-display text-[26px] font-bold leading-none text-ink">{Math.min(100, Math.floor(frac * 100))}%</span>
          <span className="mt-1 text-[11px] font-semibold text-muted">used</span>
        </BigRing>
        <div className="min-w-0 flex-1">
          <p className="font-display text-[24px] font-bold leading-tight tracking-[-0.03em] text-ink">{formatBytes(used)}</p>
          <p className="text-[14px] text-muted">of {formatBytes(total)}</p>
          <p className="mt-2 text-[12.5px] text-muted">{body}</p>
        </div>
      </div>
    </GlassCard>
  )
}

function UsageBreakdown({ p, total }: { p: FreeUpPlan; total: number }) {
  const drive = n(p.quota.driveBytes)
  const trash = n(p.quota.driveTrashBytes)
  const other = n(p.quota.otherBytes)
  const parts = [
    { label: 'Drive files', bytes: drive, color: IRIS },
    { label: 'Trash', bytes: trash, color: 'var(--warn)' },
    { label: 'Gmail & Google Photos', bytes: other, color: 'var(--subtle)' },
  ]
  const denom = Math.max(1, total > 0 ? total : drive + trash + other)
  const shown = useAppeared()
  return (
    <GlassCard className="p-5">
      <h2 className="text-[17px] font-semibold text-ink">What's using space</h2>
      <div className="mt-3 flex h-3.5 w-full overflow-hidden rounded-full bg-mist" aria-hidden>
        {parts.map((part) => (
          <span key={part.label} className="h-full transition-[width] duration-[900ms] ease-[var(--ease-standard)]" style={{ width: shown ? `${Math.min(100, (part.bytes / denom) * 100)}%` : 0, background: part.color }} />
        ))}
      </div>
      <ul className="mt-2.5">
        {parts.map((part) => (
          <li key={part.label} className="flex items-center gap-2.5 py-1">
            <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ background: part.color }} aria-hidden />
            <span className="flex-1 text-[14px] text-ink">{part.label}</span>
            <span className="text-[14px] font-semibold text-ink">{formatBytes(part.bytes)}</span>
          </li>
        ))}
      </ul>
      {other > 512 * MB && (
        <p className="mt-1.5 text-[12.5px] text-muted">Gmail and Google Photos share the same 15 GB. Nudge can copy your Google Photos out (below). Gmail has to be cleaned up in Gmail.</p>
      )}
    </GlassCard>
  )
}

function ActionCard({ icon: Icon, tint, title, body, children }: { icon: LucideIcon; tint: string; title: string; body: string; children: ReactNode }) {
  return (
    <GlassCard className="p-5">
      <div className="flex items-start gap-3">
        <SoftTile color={tint} size={40} radius={13}><Icon size={20} aria-hidden /></SoftTile>
        <div className="min-w-0 flex-1">
          <h3 className="text-[15px] font-semibold text-ink">{title}</h3>
          <p className="mt-0.5 text-[13px] text-muted">{body}</p>
        </div>
      </div>
      <div className="mt-3">{children}</div>
    </GlassCard>
  )
}

function KindTile({ kind: k, bytes, count, selected, enabled, onClick }: { kind: Kind; bytes: number; count: number; selected: boolean; enabled: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      disabled={!enabled}
      onClick={onClick}
      className={clsx('press flex flex-col rounded-[22px] border p-3.5 text-left transition-colors duration-200 disabled:cursor-not-allowed', !selected && 'glass hover:bg-glass-strong')}
      style={selected ? { borderColor: k.color, borderWidth: 1.5, background: `color-mix(in srgb, ${k.color} 13%, transparent)` } : undefined}
    >
      <span className="flex w-full items-center">
        <SoftTile color={enabled ? k.color : 'var(--subtle)'} size={34} radius={11}><k.icon size={18} aria-hidden /></SoftTile>
        <span className="flex-1" />
        {selected ? <CircleCheck size={20} style={{ color: k.color }} aria-hidden /> : <Circle size={20} className="text-subtle" aria-hidden />}
      </span>
      <span className={clsx('mt-2.5 block text-[14.5px] font-semibold', enabled ? 'text-ink' : 'text-subtle')}>{k.label}</span>
      <span className="block text-[12.5px] text-muted">{enabled ? `${formatBytes(bytes)} · ${count}` : 'None here'}</span>
    </button>
  )
}

function AmountChip({ label, selected, suggested, onClick }: { label: string; selected: boolean; suggested: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      onClick={onClick}
      className={clsx(
        'press flex min-h-[42px] flex-col items-center justify-center rounded-[18px] border px-3.5 py-1.5 transition-colors duration-200',
        selected ? 'border-transparent bg-inverse text-on-inverse' : 'glass text-ink hover:bg-glass-strong',
      )}
    >
      <span className="whitespace-nowrap text-[13.5px] font-semibold">{label}</span>
      {suggested && <span className={clsx('text-[11px] font-semibold leading-tight', selected ? 'opacity-75' : 'text-link')}>Suggested</span>}
    </button>
  )
}

function DestRow({ d, title, sub, free, selected, onClick }: { d?: FreeUpDestination; title: string; sub: string; free: number | null; selected: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      onClick={onClick}
      className={clsx('press press-row flex w-full items-center gap-3 rounded-[18px] px-3 py-2.5 text-left transition-colors duration-200', selected ? 'bg-mist' : 'hover:bg-hover')}
    >
      {d ? <Avatar url={d.avatarUrl} s3={d.provider === 's3'} size={36} /> : (
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-surface text-iris"><Sparkles size={18} aria-hidden /></span>
      )}
      <span className="min-w-0 flex-1">
        <span className="block truncate text-[14.5px] font-semibold text-ink">{title}</span>
        <span className="block truncate text-[12.5px] text-muted">{sub}</span>
      </span>
      <span className="shrink-0 px-1 text-[12.5px] font-semibold text-muted">{free === null ? 'No limit' : `${formatBytes(free)} free`}</span>
      {selected ? <CircleDot size={20} className="shrink-0 text-iris" aria-hidden /> : <Circle size={20} className="shrink-0 text-subtle" aria-hidden />}
    </button>
  )
}

function Avatar({ url, size, s3 }: { url: string | null | undefined; size: number; s3?: boolean }) {
  const [broken, setBroken] = useState(false)
  const icon = Math.round(size * 0.5)
  return (
    <span className="flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-surface" style={{ width: size, height: size }}>
      {url && !broken ? (
        <img src={url} alt="" referrerPolicy="no-referrer" onError={() => setBroken(true)} className="h-full w-full object-cover" />
      ) : s3 ? (
        <Database size={icon} className="text-iris" aria-hidden />
      ) : (
        <DriveGlyph size={icon} />
      )}
    </span>
  )
}

/** True one frame after mount, so bars and rings grow from zero. */
function useAppeared() {
  const [on, setOn] = useState(false)
  useEffect(() => {
    let inner = 0
    const outer = requestAnimationFrame(() => {
      inner = requestAnimationFrame(() => setOn(true))
    })
    return () => {
      cancelAnimationFrame(outer)
      cancelAnimationFrame(inner)
    }
  }, [])
  return on
}

function BigRing({ fraction, color, size, stroke = 11, label, children }: { fraction: number; color: string; size: number; stroke?: number; label?: string; children: ReactNode }) {
  const f = Math.min(1, Math.max(0, fraction))
  const r = (size - stroke) / 2
  const c = 2 * Math.PI * r
  const shown = useAppeared()
  return (
    <span className="relative inline-flex shrink-0 items-center justify-center" style={{ width: size, height: size }} role={label ? 'img' : undefined} aria-label={label}>
      <svg width={size} height={size} className="absolute inset-0 -rotate-90" aria-hidden>
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" style={{ stroke: 'var(--mist)' }} strokeWidth={stroke} />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          style={{ stroke: color, transition: 'stroke-dashoffset 1100ms var(--ease-standard), stroke 300ms ease' }}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={c}
          strokeDashoffset={shown ? c * (1 - (f > 0 ? Math.max(f, 0.01) : 0)) : c}
        />
      </svg>
      <span className="relative flex flex-col items-center">{children}</span>
    </span>
  )
}

function Step({ n: num, children }: { n: number; children: ReactNode }) {
  return (
    <li className="flex items-start gap-2.5 py-1">
      <span className="flex h-[22px] w-[22px] shrink-0 items-center justify-center rounded-full bg-mist text-[11px] font-bold text-ink">{num}</span>
      <span className="pt-0.5 text-[12.5px] text-ink">{children}</span>
    </li>
  )
}

// ------------------------------------------------------------------ Job

function JobView({ p, j, refreshError, onCancel, onDone, onMore }: { p: FreeUpPlan; j: FreeUpJob; refreshError: string | null; onCancel: () => Promise<void>; onDone: () => Promise<void>; onMore: () => Promise<void> }) {
  const toast = useToast()
  const [confirmCancel, setConfirmCancel] = useState(false)
  const [showFailures, setShowFailures] = useState(false)
  const [leaving, setLeaving] = useState<'done' | 'more' | null>(null)
  const google = useConnectGoogle((ok, err) => (ok ? toast.show('Google Drive connected', { tone: 'success' }) : toast.error(err ?? 'Google Drive was not connected.')))

  const moved = n(j.movedBytes)
  const totalBytes = Math.max(1, n(j.totalBytes))
  const running = j.state === 'running'
  const freed = n(j.freedBytes) > 0 ? n(j.freedBytes) : moved
  const photos = j.kind === 'photos'
  const copied = j.doneFiles - j.skipped
  const progress = photos ? j.doneFiles / Math.max(1, j.totalFiles) : moved / totalBytes
  const success = j.state === 'done' && j.doneFiles > 0
  const ringColor = running ? IRIS : success ? 'var(--success)' : j.state === 'failed' ? 'var(--danger)' : 'var(--warn)'
  const folder = j.folderName ?? 'Google Photos'
  const email = p.account.email

  const title = photos && running
    ? 'Copying from Google Photos…'
    : photos && j.state === 'done' && copied > 0
      ? `Copied ${copied} photo${s(copied)} & videos`
      : photos && j.state === 'done' && j.skipped > 0
        ? 'Already copied'
        : photos && j.state === 'done'
          ? 'Nothing could be copied'
          : running
            ? 'Freeing up space…'
            : success
              ? `Freed ${formatBytes(freed)}`
              : j.state === 'done'
                ? 'Nothing could be moved'
                : j.state === 'cancelled'
                  ? 'Stopped'
                  : 'Something went wrong'

  const subtitle = photos && running
    ? `Copying ${j.doneFiles} of ${j.totalFiles} from Google Photos · ${formatBytes(moved)} so far`
    : photos && j.state === 'done' && copied > 0
      ? `${formatBytes(moved)} is safe on your other drives, in the folder “${folder}”.${j.skipped > 0 ? ` ${j.skipped} were already copied before.` : ''}`
      : photos && j.state === 'done' && j.skipped > 0
        ? `All ${j.skipped} were copied before. They're in “${folder}”.`
        : photos && j.state === 'cancelled'
          ? `Copied ${copied} (${formatBytes(moved)}) before stopping. Nothing was removed from Google Photos.`
          : photos && j.state === 'done'
            ? 'None of them could be copied. Everything is still in Google Photos.'
            : running
              ? `Moving ${j.doneFiles} of ${j.totalFiles} files from ${email}`
              : success
                ? `Moved ${j.doneFiles} files off ${email}. Gmail and Google Photos can use this space again. Google may take a few minutes to show the new total.`
                : j.state === 'cancelled'
                  ? `Moved ${j.doneFiles} files (${formatBytes(moved)}) before stopping. The rest stayed where they were.`
                  : j.state === 'failed'
                    ? j.error ?? "The move stopped. Files that weren't moved are still safe on this Drive."
                    : 'None of the files could be moved. They are all still safe on this Drive.'

  const StateIcon = success ? Check : j.state === 'failed' ? X : Pause

  return (
    <div className="mx-auto grid w-full max-w-[640px] grid-cols-1 gap-3.5">
      <GlassCard strong className="page-enter flex flex-col items-center p-[22px] text-center">
        {running && <TransferVisual p={p} />}
        <BigRing fraction={running ? progress : 1} color={ringColor} size={168} stroke={14} label={running ? `${Math.round(progress * 100)} percent done` : undefined}>
          {running && photos ? (
            <>
              <span className="font-display text-[30px] font-bold leading-none text-ink">{j.doneFiles}</span>
              <span className="mt-1 text-[12.5px] text-muted">{j.totalFiles > 0 ? `of ${j.totalFiles}` : 'getting ready'}</span>
            </>
          ) : running ? (
            <>
              <span className="font-display text-[26px] font-bold leading-none text-ink">{formatBytes(moved)}</span>
              <span className="mt-1 text-[12.5px] text-muted">of {formatBytes(totalBytes)}</span>
            </>
          ) : (
            <StateIcon size={56} className="pop-in" style={{ color: success ? 'var(--success)' : 'var(--warn)' }} aria-hidden />
          )}
        </BigRing>
        <h2 className="mt-4 font-display text-[24px] font-bold leading-tight tracking-[-0.02em] text-ink" aria-live="polite">{title}</h2>
        <p className="mt-1.5 text-[14px] text-muted">{subtitle}</p>
        {running && !photos && (
          <div className="mt-3.5 h-2 w-full overflow-hidden rounded-full bg-mist" role="progressbar" aria-label="Files moved" aria-valuemin={0} aria-valuemax={j.totalFiles} aria-valuenow={j.doneFiles}>
            <div className="h-full rounded-full bg-iris transition-[width] duration-[600ms]" style={{ width: `${j.totalFiles === 0 ? 0 : (j.doneFiles / j.totalFiles) * 100}%` }} />
          </div>
        )}
      </GlassCard>

      {refreshError && <p role="alert" className="rounded-[18px] bg-danger-soft p-4 text-[14px] text-danger">{refreshError}</p>}

      {running && j.current.length > 0 && (
        <GlassCard className="px-4 py-2.5">
          <ul>
            {j.current.slice(0, 3).map((name, i) => (
              <li key={`${name}-${i}`} className="flex items-center gap-3 py-1.5">
                <Spinner size={16} className="shrink-0 text-iris" />
                <span className="min-w-0 truncate text-[14px] text-ink">{name}</span>
              </li>
            ))}
          </ul>
        </GlassCard>
      )}

      {j.destinations.length > 0 && (
        <GlassCard className="px-4 py-2.5">
          <p className="py-1.5 text-[13px] font-semibold text-muted">{running ? (photos ? 'Copying to' : 'Going to') : 'Now stored on'}</p>
          <ul>
            {j.destinations.map((d) => (
              <li key={d.id} className="flex items-center gap-3 py-[7px]">
                <Avatar url={p.destinations.find((x) => x.id === d.id)?.avatarUrl} s3={d.provider === 's3'} size={32} />
                <span className="min-w-0 flex-1 truncate text-[14px] text-ink">{d.email}</span>
                <span className="shrink-0 text-[12.5px] font-semibold text-muted">{d.files} · {formatBytes(d.bytes)}</span>
              </li>
            ))}
          </ul>
        </GlassCard>
      )}

      {photos && !running && j.doneFiles > 0 && <DeleteInPhotosCard p={p} count={j.doneFiles} takenFrom={j.takenFrom} takenTo={j.takenTo} />}

      {!running && j.outOfSpace && (
        <ActionCard icon={TriangleAlert} tint="var(--warn)" title="Your other drives ran out of space" body="Connect another Google account (15 GB free) and run Free up space again to move the rest.">
          <Button variant="secondary" size="lg" block icon={Plus} loading={google.busy} onClick={() => void google.connect()}>Connect another Google account</Button>
        </ActionCard>
      )}

      {j.failedCount > 0 && (
        <GlassCard className="p-0">
          <button type="button" aria-expanded={showFailures} onClick={() => setShowFailures((v) => !v)} className="press flex w-full items-center gap-2.5 rounded-[26px] p-4 text-left">
            <CircleAlert size={20} className="shrink-0 text-warn" aria-hidden />
            <span className="flex-1 text-[15px] font-semibold text-ink">{j.failedCount} file{s(j.failedCount)} not {photos ? 'copied' : 'moved'}</span>
            <ChevronDown size={20} className={clsx('shrink-0 text-muted transition-transform duration-200', showFailures && 'rotate-180')} aria-hidden />
          </button>
          {showFailures && (
            <ul className="rise px-4 pb-3">
              {j.failures.map((f, i) => (
                <li key={`${f.name}-${i}`} className="py-1.5">
                  <p className="truncate text-[14px] text-ink">{f.name}</p>
                  <p className="text-[12.5px] text-muted">{f.reason}</p>
                </li>
              ))}
            </ul>
          )}
        </GlassCard>
      )}

      <div className="grid grid-cols-1 gap-2.5">
        {running ? (
          <>
            <p className="mx-2 text-center text-[12.5px] text-muted">This runs on your server. You can leave this page or close the browser and it keeps going.</p>
            <Button variant="secondary" size="lg" block icon={Pause} onClick={() => setConfirmCancel(true)}>Stop after the current files</Button>
          </>
        ) : (
          <>
            <Button size="lg" block icon={Check} loading={leaving === 'done'} disabled={leaving !== null} onClick={() => { setLeaving('done'); void onDone() }}>Done</Button>
            <Button
              variant="secondary"
              size="lg"
              block
              icon={Sparkles}
              loading={leaving === 'more'}
              disabled={leaving !== null}
              onClick={() => {
                setLeaving('more')
                void onMore()
              }}
            >
              {photos ? 'Copy more photos' : 'Free up more'}
            </Button>
          </>
        )}
      </div>

      <ConfirmDialog
        open={confirmCancel}
        onClose={() => setConfirmCancel(false)}
        title={photos ? 'Stop copying?' : 'Stop moving files?'}
        body={photos ? 'Photos already copied stay copied. Nothing is removed from Google Photos.' : `Files already moved stay on their new drive. The rest stay on ${email}.`}
        confirmLabel="Stop"
        onConfirm={async () => {
          try {
            await onCancel()
          } catch (e) {
            toast.error(errorMessage(e))
          }
        }}
      />
    </div>
  )
}

/** Source drive → a stream of dots → the destination drives, while files are moving. */
function TransferVisual({ p }: { p: FreeUpPlan }) {
  const first = p.destinations[0]
  const extra = Math.max(0, p.destinations.length - 1)
  return (
    <div className="mb-[18px] flex w-full items-center" aria-hidden>
      <Bubble url={p.account.avatarUrl} />
      <span className="relative mx-3 h-5 flex-1">
        {[0, 1, 2, 3, 4, 5].map((i) => <span key={i} className="flow-dot" style={{ animationDelay: `${-(i * 1400) / 6}ms` } as CSSProperties} />)}
      </span>
      <span className="relative">
        <Bubble url={first?.avatarUrl} s3={first?.provider === 's3'} />
        {extra > 0 && <span className="absolute -bottom-0.5 -right-1 rounded-full bg-inverse px-1.5 text-[11px] font-semibold text-on-inverse">+{extra}</span>}
      </span>
    </div>
  )
}

function Bubble({ url, s3 }: { url: string | null | undefined; s3?: boolean }) {
  return (
    <span className="flex rounded-full border border-glass-border">
      <Avatar url={url} s3={s3} size={52} />
    </span>
  )
}

// ------------------------------------------------------------------ Google Photos

/** Resolves when the Google permission pop-up reports back (/google-connected), is closed, or the wait is called off. */
function waitForGoogle(win: Window, signal: AbortSignal) {
  return new Promise<'ok' | 'failed' | 'closed'>((resolve) => {
    let grace = 0
    const onMessage = (e: MessageEvent) => {
      if (e.origin !== window.location.origin || e.data?.type !== 'GOOGLE_CONNECTED') return
      finish(e.data.status === 'success' ? 'ok' : 'failed')
    }
    const onAbort = () => finish('closed')
    // The "connected" page posts and then closes itself, so give its message a moment before calling it closed.
    const timer = window.setInterval(() => {
      if (!win.closed) return
      window.clearInterval(timer)
      grace = window.setTimeout(() => finish('closed'), 500)
    }, 800)
    function finish(result: 'ok' | 'failed' | 'closed') {
      window.removeEventListener('message', onMessage)
      signal.removeEventListener('abort', onAbort)
      window.clearInterval(timer)
      window.clearTimeout(grace)
      resolve(result)
    }
    window.addEventListener('message', onMessage)
    signal.addEventListener('abort', onAbort)
    if (signal.aborted) finish('closed')
  })
}

function waitingPage(win: Window) {
  try {
    win.document.title = 'Google Photos · Nudge Drive'
    win.document.body.style.cssText = 'margin:0;display:flex;min-height:100vh;align-items:center;justify-content:center;font:15px system-ui,sans-serif;color:#4e4670;background:#f7f4ff'
    win.document.body.textContent = 'Opening Google Photos…'
  } catch {
    /* not ours to write to */
  }
}

/** One run of the picker: its pop-up, and a signal that ends everything still waiting (Cancel, leaving the page). */
type PhotosRun = { ctrl: AbortController; win: Window | null }

/**
 * Google Photos can't be read through Drive. Google lets apps see only what the user picks in Google's
 * own picker, so: pick → the server copies the originals to the other drives → the user deletes them
 * in Google Photos (no app may delete there). The run lives in PlanView's activity (kind 'photos').
 */
function usePhotosPicker(p: FreeUpPlan, activity: Activity, setActivity: Dispatch<SetStateAction<Activity>>, onStarted: (job: FreeUpJob) => void) {
  const toast = useToast()
  const accountId = p.account.id
  const run = useRef<PhotosRun | null>(null)
  const startedRef = useRef(onStarted)
  startedRef.current = onStarted

  const setPhotos = useCallback((patch: Partial<Omit<PhotosActivity, 'kind'>>) => setActivity((a) => (a.kind === 'photos' ? { ...a, ...patch } : a)), [setActivity])

  /** Ends the run: whatever is still waiting stops, the pop-up closes and the page is free again. */
  const end = useCallback((r: PhotosRun) => {
    r.ctrl.abort()
    try {
      r.win?.close()
    } catch {
      /* already gone */
    }
    if (run.current !== r) return
    run.current = null
    setActivity({ kind: 'idle' })
  }, [setActivity])

  // Leaving the page (or a job starting) closes the picker; nothing may carry on in the background.
  useEffect(() => () => {
    const r = run.current
    run.current = null
    if (!r) return
    r.ctrl.abort()
    try {
      r.win?.close()
    } catch {
      /* already gone */
    }
  }, [])

  /** Google's documented web flow: the picker in a pop-up that closes itself after Done (`/autoclose`). */
  function openPicker(r: PhotosRun, sess: PhotosSession, win?: Window | null) {
    if (!sess.pickerUri) return setPhotos({ step: 'picking', session: sess })
    const url = `${sess.pickerUri.replace(/\/+$/, '')}/autoclose`
    let w = win && !win.closed ? win : null
    if (w) w.location.href = url
    else w = window.open(url, POPUP_NAME, POPUP_FEATURES)
    r.win = w
    setPhotos({ step: 'picking', session: sess, blocked: !w })
    w?.focus()
  }

  async function newSession(r: PhotosRun, allowGrant: boolean): Promise<void> {
    const { signal } = r.ctrl
    const win = r.win
    try {
      const { session: sess } = await api.freeUp.photosSession(accountId)
      if (signal.aborted) return
      // The waiting window was closed meanwhile: the user changed their mind.
      if (win?.closed) return end(r)
      openPicker(r, sess, win)
    } catch (e) {
      if (signal.aborted) return
      if (!(e instanceof ApiError) || e.code !== 'PHOTOS_ACCESS_NEEDED' || !allowGrant || !win) throw e
      // Google asks once to let Nudge see the photos the user picks; same pop-up, then carry on by itself.
      const { url } = await api.accounts.googlePhotosConnectUrl(p.account.email)
      if (signal.aborted) return
      if (win.closed) return end(r)
      setPhotos({ step: 'granting' })
      win.location.href = url
      const result = await waitForGoogle(win, signal)
      if (signal.aborted) return
      if (result === 'closed') return end(r)
      if (result === 'failed') {
        end(r)
        toast.error('Nudge needs permission to see the photos you pick. Please try again and allow it.')
        return
      }
      // Keep the pop-up (the "connected" page would close it) and show the picker in it.
      try {
        win.location.replace('about:blank')
      } catch {
        /* it closed */
      }
      setPhotos({ step: 'opening' })
      await newSession(r, false)
    }
  }

  async function begin(destinationId: string | null) {
    if (run.current) return
    // Opened right away, while the click still counts, so the browser doesn't block it.
    const win = window.open('', POPUP_NAME, POPUP_FEATURES)
    if (!win) {
      toast.error('Your browser blocked the Google Photos window. Allow pop-ups for this site and try again.')
      return
    }
    waitingPage(win)
    const r: PhotosRun = { ctrl: new AbortController(), win }
    run.current = r
    setActivity({ kind: 'photos', step: 'opening', session: null, destinationId, blocked: false })
    try {
      await newSession(r, true)
    } catch (e) {
      if (r.ctrl.signal.aborted) return
      end(r)
      toast.error(errorMessage(e))
    }
  }

  async function startCopy(r: PhotosRun, sess: PhotosSession, destinationId: string | null) {
    setPhotos({ step: 'starting' })
    try {
      r.win?.close()
    } catch {
      /* closed itself */
    }
    try {
      const { job } = await api.freeUp.photosStart(accountId, sess.id, destinationId)
      if (r.ctrl.signal.aborted) return
      end(r)
      if (job) startedRef.current(job)
    } catch (e) {
      if (r.ctrl.signal.aborted) return
      toast.error(errorMessage(e))
      end(r)
    }
  }

  // Waiting for the picks; once Google says they're set, the server starts copying.
  const session = activity.kind === 'photos' && activity.step === 'picking' ? activity.session : null
  const destinationId = activity.kind === 'photos' ? activity.destinationId : null
  useEffect(() => {
    const r = run.current
    if (!session || !r) return
    let stopped = false
    let timer = 0
    let checking = false
    /** Checks made since the pop-up was seen closed. */
    let afterClose = 0
    const interval = Math.min(10_000, Math.max(2000, session.pollIntervalMs || 4000))
    // The picker closes itself after Done; a pop-up closed with nothing picked means the user gave up.
    const popupClosed = () => r.win !== null && r.win.closed
    const tick = async () => {
      let now: PhotosSession | null = null
      checking = true
      try {
        now = (await api.freeUp.photosSessionStatus(accountId, session.id)).session
      } catch (e) {
        if (e instanceof ApiError && e.code === 'PHOTOS_SESSION_GONE') {
          if (!stopped) {
            toast.error(e.message)
            end(r)
          }
          return
        }
      } finally {
        checking = false
      }
      if (stopped || r.ctrl.signal.aborted) return
      if (now?.mediaItemsSet) return void startCopy(r, session, destinationId)
      if (popupClosed()) {
        // Google may still be saving the picks: look once more, then give up.
        if (++afterClose > 1) return end(r)
        timer = window.setTimeout(tick, 1500)
        return
      }
      afterClose = 0
      timer = window.setTimeout(tick, interval)
    }
    timer = window.setTimeout(tick, interval)
    // Notice the pop-up closing right away instead of at the next poll.
    const watch = window.setInterval(() => {
      if (checking || afterClose > 0 || !popupClosed()) return
      window.clearTimeout(timer)
      void tick()
    }, 800)
    return () => {
      stopped = true
      window.clearTimeout(timer)
      window.clearInterval(watch)
    }
    // `startCopy` only reads refs and stable setters.
  }, [session, destinationId, accountId, toast, end])

  return {
    begin,
    openAgain: (sess: PhotosSession) => {
      if (run.current) openPicker(run.current, sess)
    },
    cancel: () => {
      if (run.current) end(run.current)
    },
  }
}

function PhotosCard({ p, photos, disabled, onBegin, onOpen, onCancel }: { p: FreeUpPlan; photos: PhotosActivity | null; disabled: boolean; onBegin: () => void; onOpen: (session: PhotosSession) => void; onCancel: () => void }) {
  const other = n(p.quota.otherBytes)
  const session = photos?.session ?? null
  const blocked = photos?.blocked ?? false
  const starting = photos?.step === 'starting'

  return (
    <GlassCard strong className="p-5">
      <div className="flex items-start gap-3">
        <SoftTile color="#1e9e57" size={44} radius={14}><Images size={22} aria-hidden /></SoftTile>
        <div className="min-w-0 flex-1">
          <h3 className="text-[16px] font-semibold text-ink">Copy your Google Photos out</h3>
          <p className="mt-0.5 text-[13px] text-muted">
            {formatBytes(other)} of this account is Google Photos and Gmail. Pick photos and videos, and Nudge copies the full-quality originals to your other drives.
          </p>
        </div>
      </div>
      {session ? (
        <div className="rise mt-4" role="status">
          <div className="flex items-center gap-3">
            <Spinner size={18} className="shrink-0 text-[#1e9e57]" />
            <p className="flex-1 text-[14px] text-ink">
              {blocked
                ? 'Your browser blocked the Google Photos window. Open it to pick your photos and videos.'
                : 'Select photos and videos in the Google Photos window, then click Done. Nudge starts copying by itself.'}
            </p>
          </div>
          <div className="mt-3.5 grid grid-cols-2 gap-2.5">
            <Button variant={blocked ? 'primary' : 'secondary'} icon={ExternalLink} disabled={starting} onClick={() => onOpen(session)}>{blocked ? 'Open Google Photos' : 'Open again'}</Button>
            <Button variant="secondary" icon={X} disabled={starting} onClick={onCancel}>Cancel</Button>
          </div>
        </div>
      ) : (
        <div className="mt-4">
          <Button size="lg" block icon={Images} loading={photos !== null} disabled={disabled || p.destinations.length === 0} onClick={onBegin}>Choose from Google Photos</Button>
          <ol className="mt-3.5">
            <Step n={1}>Pick photos and videos in Google Photos (up to 2,000 at a time).</Step>
            <Step n={2}>Nudge copies the originals to your other drives. Nothing is removed yet.</Step>
            <Step n={3}>You delete them in Google Photos, and the space is free again.</Step>
          </ol>
          {!p.photosAccess && <p className="mt-2 text-[12.5px] text-muted">Google will ask once to let Nudge see the photos you pick. Nudge never sees the rest of your library.</p>}
        </div>
      )}
    </GlassCard>
  )
}

/**
 * No app may delete from Google Photos, so the last step is the user's: Google Photos opens in a new
 * tab with the dates to select, and the space freed is shown when the user comes back to this tab.
 */
function DeleteInPhotosCard({ p, count, takenFrom, takenTo }: { p: FreeUpPlan; count: number; takenFrom: string | null; takenTo: string | null }) {
  const usedBefore = n(p.quota.usedBytes)
  const [usedNow, setUsedNow] = useState<number | null>(null)
  const [checking, setChecking] = useState(false)
  const opened = useRef(false)
  const inFlight = useRef(false)
  const accountId = p.account.id

  const check = useCallback(async () => {
    if (inFlight.current) return
    inFlight.current = true
    setChecking(true)
    try {
      const plan = await api.freeUp.plan(accountId)
      setUsedNow(n(plan.quota.usedBytes))
    } catch {
      /* leave the last number */
    } finally {
      inFlight.current = false
      setChecking(false)
    }
  }, [accountId])

  // Back from Google Photos: show what came free.
  useEffect(() => {
    const onFocus = () => {
      if (opened.current) void check()
    }
    window.addEventListener('focus', onFocus)
    return () => window.removeEventListener('focus', onFocus)
  }, [check])

  function open(url: string) {
    opened.current = true
    window.open(url, '_blank', 'noopener')
  }

  const from = takenFrom ? shortDate(takenFrom) : null
  const to = takenTo ? shortDate(takenTo) : null
  const range = !from || !to ? null : from === to ? `all taken on ${from}` : `taken between ${from} and ${to}`
  const freed = usedNow === null ? 0 : Math.max(0, usedBefore - usedNow)

  return (
    <GlassCard strong className="p-5">
      <div className="flex items-center gap-3">
        <SoftTile color="var(--warn)" size={40} radius={13}><Trash2 size={20} aria-hidden /></SoftTile>
        <div className="min-w-0 flex-1">
          <h3 className="text-[15px] font-semibold text-ink">Now delete the originals</h3>
          <p className="text-[13px] text-muted">Google only lets you delete in Google Photos itself, so it opens in a new tab. Your copies are safe.</p>
        </div>
      </div>
      {range && (
        <div className="mt-3 flex items-center gap-2.5 rounded-[14px] bg-mist p-3">
          <CalendarDays size={18} className="shrink-0 text-iris" aria-hidden />
          <p className="text-[12.5px] text-ink">The {count} copied are {range}.</p>
        </div>
      )}
      <ol className="mt-3">
        <Step n={1}>Click the circle next to a date to select that whole day, or Shift-click to select a range of photos.</Step>
        <Step n={2}>Click the bin icon to delete them.</Step>
        <Step n={3}>Then empty the bin, or Google keeps using the space for 60 days.</Step>
      </ol>
      <div className="mt-3.5 grid grid-cols-1 gap-2.5">
        <Button variant="warn" size="lg" block icon={Trash2} onClick={() => open(PHOTOS_URL)}>1 · Delete in Google Photos</Button>
        <Button variant="secondary" size="lg" block icon={Trash} onClick={() => open(PHOTOS_BIN_URL)}>2 · Empty the bin</Button>
      </div>
      {(usedNow !== null || checking) && (
        <div className={clsx('mt-3.5 flex items-center gap-2.5 rounded-[14px] p-3', freed > 0 ? 'bg-success-soft' : 'bg-mist')} role="status">
          {checking ? (
            <Spinner size={16} className="shrink-0 text-iris" />
          ) : freed > 0 ? (
            <CircleCheck size={18} className="shrink-0 text-success" aria-hidden />
          ) : (
            <Clock size={18} className="shrink-0 text-muted" aria-hidden />
          )}
          <p className="flex-1 text-[12.5px] text-ink">
            {checking
              ? `Checking the space on ${p.account.email}…`
              : freed > 0
                ? `${formatBytes(freed)} freed · ${formatBytes(usedNow)} used now`
                : `Still ${formatBytes(usedNow)} used. Google can take a few minutes after the bin is emptied.`}
          </p>
          {!checking && <button type="button" onClick={() => void check()} className="press shrink-0 rounded-xl px-2 py-1 text-[13px] font-semibold text-link hover:bg-hover">Check</button>}
        </div>
      )}
    </GlassCard>
  )
}
