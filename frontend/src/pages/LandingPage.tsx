import { useCallback, useRef, type CSSProperties, type ReactNode } from 'react'
import { preload } from 'react-dom'
import { Link } from 'react-router-dom'
import {
  ArrowDown, ArrowRight, BatteryCharging, Ban, Bell, Bluetooth, Check, CircleCheck, Clock, Cloud, CodeXml, Database, FolderSync, Globe,
  HardDrive, HardDriveUpload, Images, KeyRound, Layers, Link2, Monitor, MonitorDown, PenLine, Plus, Radar, ShieldCheck, Smartphone,
  Trash2, CloudUpload, Wifi, type LucideIcon,
} from 'lucide-react'
import { DriveGlyph } from '@/components/brand/Brand'
import { ButtonLink } from '@/components/ui/Button'
import { useTheme } from '@/context/ThemeContext'
import { useDocumentTitle } from '@/lib/hooks'
import { GooglePlayBadge, LandingFooter, LandingHeader, SUPPORT_EMAIL } from '@/landing/Chrome'
import { Laptop, Phone } from '@/landing/devices'
import { HeroScene } from '@/landing/HeroScene'
import { useReveal, useScrollProgress } from '@/landing/motion'
import { DESK, PHONE } from '@/landing/shots'
import { Showcase } from '@/landing/Showcase'
import '@/landing/landing.css'

function Kicker({ children }: { children: ReactNode }) {
  return <p className="text-[13px] font-bold uppercase tracking-[0.16em] text-link">{children}</p>
}

function SectionHead({ id, kicker, title, sub }: { id: string; kicker: string; title: ReactNode; sub?: ReactNode }) {
  return (
    <div className="ld-reveal mx-auto max-w-2xl text-center">
      <Kicker>{kicker}</Kicker>
      <h2 id={id} className="mt-3 text-[34px] font-bold leading-[1.08] tracking-[-0.04em] text-ink sm:text-[46px]">{title}</h2>
      {sub && <p className="mt-4 text-[17px] leading-relaxed text-muted">{sub}</p>}
    </div>
  )
}

/* ------------------------------------------------------------------------------------------ Hero */

function Hero({ dark }: { dark: boolean }) {
  return (
    <section className="ld-hero relative overflow-hidden" aria-labelledby="hero-h">
      <div className="ld-grid-bg" aria-hidden />
      <div className="mx-auto grid max-w-[1240px] grid-cols-1 items-center gap-6 px-4 pb-4 pt-6 sm:px-6 sm:pt-14 lg:min-h-[min(780px,calc(100svh-64px))] lg:grid-cols-[minmax(0,540px)_minmax(0,1fr)] lg:gap-4 lg:pb-6 lg:pt-6">
        <div className="relative z-10 text-center lg:text-left">
          <span className="ld-reveal ld-pill" style={{ '--d': '0ms' } as CSSProperties}>
            <span className="grid h-6 w-6 place-items-center rounded-full bg-primary text-white"><HardDrive size={13} aria-hidden /></span>
            Free · uses the space you already have
          </span>
          <h1 id="hero-h" className="ld-reveal mt-5 text-[38px] font-bold leading-[1.03] tracking-[-0.05em] text-ink sm:text-[58px] xl:text-[62px]" style={{ '--d': '80ms' } as CSSProperties}>
            All your Google Drives. <span className="ld-gradient-text lg:block">One storage pool.</span>
          </h1>
          <p className="ld-reveal mx-auto mt-4 max-w-xl text-[16px] leading-relaxed sm:mt-5 sm:text-[17.5px] text-muted lg:mx-0" style={{ '--d': '160ms' } as CSSProperties}>
            Nudge Drive joins your Google Drive accounts and S3 buckets into one place to upload, browse, preview and share, and clears space when a Google account fills up. On Android, Windows and the web.
          </p>
          <div className="ld-reveal mt-6 flex flex-wrap items-center justify-center gap-3 sm:mt-8 lg:justify-start" style={{ '--d': '240ms' } as CSSProperties}>
            <ButtonLink to="/register" size="lg">Get started free<ArrowRight size={18} aria-hidden /></ButtonLink>
            <ButtonLink to="/login" size="lg" variant="secondary">Sign in</ButtonLink>
          </div>
          <div className="ld-reveal mt-5 flex flex-wrap items-center justify-center gap-x-5 gap-y-3 lg:justify-start" style={{ '--d': '320ms' } as CSSProperties}>
            <GooglePlayBadge />
            <ul className="hidden gap-1 text-left text-[13px] font-medium text-muted sm:grid">
              <li className="flex items-center gap-1.5"><ShieldCheck size={15} className="text-success" aria-hidden />No ads, no tracking</li>
              <li className="flex items-center gap-1.5"><Cloud size={15} className="text-accent" aria-hidden />Files stay in your own storage</li>
            </ul>
          </div>
        </div>
        <div className="relative -mx-2 sm:mx-0 lg:-mr-10 xl:-mr-16">
          <HeroScene dark={dark} />
        </div>
      </div>
    </section>
  )
}

/* ---------------------------------------------------------------------------------- How it works */

function HowItWorks() {
  const steps: { n: number; title: string; body: string; visual: ReactNode }[] = [
    {
      n: 1, title: 'Connect your drives', body: 'Add Google accounts, 15 GB each on a free account, or any S3-compatible bucket. Files already in a Drive come in by themselves.',
      visual: (
        <div className="grid gap-2">
          {[
            { name: 'Personal', sub: 'Google Drive · 15 GB', icon: <DriveGlyph size={18} /> },
            { name: 'Work', sub: 'Google Drive · 15 GB', icon: <DriveGlyph size={18} /> },
            { name: 'Backups', sub: 'S3 bucket', icon: <Database size={17} className="text-accent" aria-hidden /> },
          ].map((d, i) => (
            <div key={d.name} className="ld-reveal flex items-center gap-3 rounded-[16px] bg-[var(--ld-card-solid)] px-3 py-2.5 ring-1 ring-[var(--ld-hairline)]" style={{ '--d': `${200 + i * 120}ms` } as CSSProperties}>
              <span className="grid h-9 w-9 place-items-center rounded-[12px] bg-mist">{d.icon}</span>
              <span className="min-w-0 leading-tight"><span className="block text-[14px] font-semibold text-ink">{d.name}</span><span className="block text-[12px] text-muted">{d.sub}</span></span>
              <span className="ml-auto grid h-6 w-6 place-items-center rounded-full bg-success-soft text-success"><Plus size={13} strokeWidth={3} aria-hidden /></span>
            </div>
          ))}
        </div>
      ),
    },
    {
      n: 2, title: 'Get one pool', body: 'Their free space adds up. See total, used and free at a glance, by photos, videos and docs.',
      visual: (
        <div className="rounded-[20px] bg-[var(--ld-card-solid)] p-4 ring-1 ring-[var(--ld-hairline)]">
          <div className="flex items-center gap-2 text-[13px] font-semibold text-muted"><Layers size={16} className="text-primary" aria-hidden />Storage pool<span className="ml-auto rounded-full bg-mist px-2 py-0.5 text-[11.5px] text-ink">2 drives</span></div>
          <p className="mt-2 font-display text-[30px] font-bold tracking-[-0.03em] text-ink">30.0 GB <span className="text-[15px] font-semibold text-muted">free</span></p>
          <div className="ld-pool-bar mt-3" aria-hidden>
            <span style={{ background: 'linear-gradient(90deg,#8b72f5,#b9a6ff)' }} />
            <span style={{ background: 'linear-gradient(90deg,#7059f0,#8b72f5)' }} />
          </div>
          <div className="mt-2.5 flex justify-between text-[12px] font-medium text-muted"><span>Personal 15 GB</span><span>Work 15 GB</span></div>
        </div>
      ),
    },
    {
      n: 3, title: 'Upload and share', body: 'Drop files in. Nudge Drive picks a drive with room, keeps track of it all, and makes sharing a tap.',
      visual: (
        <div className="grid gap-2">
          <div className="flex items-center gap-3 rounded-[16px] bg-primary px-3 py-2.5 text-white shadow-[0_14px_30px_-14px_rgb(112_89_240/0.8)]">
            <CloudUpload size={20} aria-hidden />
            <span className="leading-tight"><span className="block text-[14px] font-semibold">Uploading 3 photos</span><span className="block text-[12px] text-white/80">to Work · most free space</span></span>
          </div>
          <div className="flex items-center gap-3 rounded-[16px] bg-[var(--ld-card-solid)] px-3 py-2.5 ring-1 ring-[var(--ld-hairline)]">
            <span className="grid h-9 w-9 place-items-center rounded-[12px] bg-mist text-link"><Link2 size={17} aria-hidden /></span>
            <span className="min-w-0 truncate text-[13px] font-medium text-muted">nudge.qd.je/public/files/…</span>
            <span className="ml-auto rounded-full bg-success-soft px-2 py-0.5 text-[11.5px] font-semibold text-success">Link on</span>
          </div>
        </div>
      ),
    },
  ]
  return (
    <section id="how" aria-labelledby="how-h" className="relative px-4 py-24 sm:px-6 sm:py-28 max-sm:py-20">
      <SectionHead id="how-h" kicker="How it works" title="Up and running in a minute" sub="Create an account with email or Google, then bring the storage you already have." />
      <ol className="relative mx-auto mt-16 grid max-w-[1180px] grid-cols-1 gap-5 md:grid-cols-3">
        <svg className="pointer-events-none absolute left-[16%] right-[16%] top-[38px] hidden h-2 w-[68%] md:block" viewBox="0 0 100 2" preserveAspectRatio="none" aria-hidden>
          <line x1="0" y1="1" x2="100" y2="1" stroke="#8b72f5" strokeOpacity="0.5" strokeWidth="2" vectorEffect="non-scaling-stroke" className="ld-flow-line" />
        </svg>
        {steps.map((s, i) => (
          <li key={s.n} className="ld-reveal ld-card relative flex flex-col p-6 sm:p-7" style={{ '--d': `${i * 120}ms` } as CSSProperties}>
            <span className="relative z-10 grid h-12 w-12 place-items-center rounded-full bg-primary font-display text-[19px] font-bold text-white shadow-[0_10px_24px_-10px_rgb(112_89_240/0.9)]">{s.n}</span>
            <h3 className="mt-5 text-[21px] font-bold tracking-[-0.025em] text-ink">{s.title}</h3>
            <p className="mt-2 text-[15px] leading-relaxed text-muted">{s.body}</p>
            <div className="mt-6 flex-1 content-end">{s.visual}</div>
          </li>
        ))}
      </ol>
    </section>
  )
}

/* -------------------------------------------------------------------------------- Free up space */

function DriveMeter({ name, sub, used, fill, badge }: { name: string; sub: string; used: number; fill: string; badge?: ReactNode }) {
  return (
    <div className="rounded-[16px] bg-[var(--ld-card-solid)] px-3.5 py-3 ring-1 ring-[var(--ld-hairline)]">
      <div className="flex items-center gap-3">
        <span className="grid h-9 w-9 flex-none place-items-center rounded-[12px] bg-mist"><DriveGlyph size={18} /></span>
        <span className="min-w-0 leading-tight"><span className="block text-[14px] font-semibold text-ink">{name}</span><span className="block text-[12px] text-muted">{sub}</span></span>
        {badge && <span className="ml-auto">{badge}</span>}
      </div>
      <div className="ld-meter mt-2.5" aria-hidden><span style={{ '--w': `${used}%`, background: fill } as CSSProperties} /></div>
    </div>
  )
}

const PHOTO_TILES = [
  'linear-gradient(135deg,#ffb37a,#ff6f61)', 'linear-gradient(135deg,#7ad0ff,#2e64e0)', 'linear-gradient(135deg,#9be7a8,#1e9e57)', 'linear-gradient(135deg,#ffd86b,#f59e0b)',
  'linear-gradient(135deg,#c9b8ff,#7059f0)', 'linear-gradient(135deg,#ff9ac1,#d6457f)', 'linear-gradient(135deg,#8fe3df,#14929b)', 'linear-gradient(135deg,#b6c2d9,#56657f)',
]

function FreeUp() {
  return (
    <section id="freeup" aria-labelledby="freeup-h" className="px-4 py-24 sm:px-6 sm:py-28 max-sm:py-20">
      <SectionHead
        id="freeup-h"
        kicker="Free up space"
        title="Google account full? Get the space back."
        sub="Gmail, Google Photos and Google Drive share one 15 GB. When an account fills up, Nudge Drive moves things to the drives that still have room."
      />
      <div className="mx-auto mt-14 grid max-w-[1180px] grid-cols-1 gap-5 lg:grid-cols-2">
        <article className="ld-reveal ld-zoom ld-bento flex flex-col" aria-labelledby="fu-drive-h">
          <div className="p-7 sm:p-9">
            <span className="soft-tile grid h-12 w-12 place-items-center rounded-[16px]" style={{ '--k': '#e8402b' } as CSSProperties}><HardDriveUpload size={23} aria-hidden /></span>
            <h3 id="fu-drive-h" className="mt-5 text-[26px] font-bold tracking-[-0.03em] text-ink sm:text-[30px]">Move Drive files off</h3>
            <p className="mt-2 max-w-md text-[15.5px] leading-relaxed text-muted">Choose a full Google account. Nudge Drive moves its files to your other drives, then removes the originals so the space comes back.</p>
            <ul className="mt-5 grid gap-3">
              <Point icon={Cloud}>It keeps going on the server, even if you close the app</Point>
              <Point icon={ShieldCheck}>A file that changes during the move is skipped, never lost</Point>
            </ul>
          </div>
          <div className="mt-auto grid gap-2 px-7 pb-8 sm:px-9 sm:pb-9">
            <DriveMeter
              name="Personal" sub="14.8 of 15 GB used" used={99} fill="linear-gradient(90deg,#ff8a65,#e8402b)"
              badge={<span className="rounded-full bg-warn-soft px-2 py-0.5 text-[11.5px] font-semibold text-warn">Full</span>}
            />
            <span className="mx-auto inline-flex items-center gap-1.5 rounded-full bg-primary px-3 py-1 text-[12.5px] font-semibold text-white shadow-[0_10px_24px_-12px_rgb(112_89_240/0.9)]">
              <ArrowDown size={14} aria-hidden />Moving 412 files
            </span>
            <DriveMeter name="Work" sub="9.4 GB free" used={37} fill="linear-gradient(90deg,#7059f0,#b9a6ff)" />
            <div className="flex items-center gap-2 rounded-[14px] bg-success-soft px-3 py-2 text-[13px] font-semibold text-success">
              <CircleCheck size={16} aria-hidden />3.8 GB freed on Personal
            </div>
          </div>
        </article>

        <article className="ld-reveal ld-zoom ld-bento flex flex-col" style={{ '--d': '120ms' } as CSSProperties} aria-labelledby="fu-photos-h">
          <div className="p-7 sm:p-9">
            <span className="soft-tile grid h-12 w-12 place-items-center rounded-[16px]" style={{ '--k': '#1e9e57' } as CSSProperties}><Images size={23} aria-hidden /></span>
            <h3 id="fu-photos-h" className="mt-5 text-[26px] font-bold tracking-[-0.03em] text-ink sm:text-[30px]">Copy your Google Photos out</h3>
            <p className="mt-2 max-w-md text-[15.5px] leading-relaxed text-muted">Pick photos and videos in Google Photos, up to 2,000 at a time. Nudge Drive copies the originals to your other drives, then you delete them in Google Photos.</p>
            <ul className="mt-5 grid gap-3">
              <Point icon={KeyRound}>Nudge Drive only sees the photos you pick</Point>
              <Point icon={Clock}>Every copy keeps the date it was taken</Point>
            </ul>
          </div>
          <div className="mt-auto px-7 pb-8 sm:px-9 sm:pb-9">
            <div className="rounded-[20px] bg-[var(--ld-card-solid)] p-3.5 ring-1 ring-[var(--ld-hairline)]">
              <div className="grid grid-cols-4 gap-2" aria-hidden>
                {PHOTO_TILES.map((g, i) => (
                  <div key={g} className="relative aspect-square rounded-[12px]" style={{ background: g }}>
                    {i < 6 && (
                      <span className="absolute right-1.5 top-1.5 grid h-5 w-5 place-items-center rounded-full bg-primary text-white ring-2 ring-white/90">
                        <Check size={11} strokeWidth={3} />
                      </span>
                    )}
                  </div>
                ))}
              </div>
              <div className="mt-3 flex items-center gap-2 rounded-[14px] bg-success-soft px-3 py-2 text-[13px] font-semibold text-success">
                <CircleCheck size={16} aria-hidden />Copied 248 photos and videos to Work
              </div>
              <p className="mt-2 px-1 text-[12.5px] text-muted">Next: delete them in Google Photos to free 3.1 GB.</p>
            </div>
          </div>
        </article>
      </div>
      <p className="ld-reveal mx-auto mt-8 max-w-2xl text-center text-[14.5px] text-muted">
        On Android, Windows and the web. Nothing leaves Google Photos until you delete it yourself.
      </p>
    </section>
  )
}

/* ------------------------------------------------------------------------- Nearby share + backup */

function Point({ icon: Icon, children }: { icon: LucideIcon; children: ReactNode }) {
  return (
    <li className="flex items-start gap-3 text-[15px] text-ink">
      <span className="grid h-8 w-8 flex-none place-items-center rounded-[11px] bg-mist text-link"><Icon size={16} aria-hidden /></span>
      <span className="pt-1">{children}</span>
    </li>
  )
}

function NearbyAndBackup() {
  return (
    <section id="nearby" aria-labelledby="nearby-h" className="px-4 py-24 sm:px-6 sm:py-28 max-sm:py-20">
      <SectionHead id="nearby-h" kicker="More than storage" title="Share nearby. Back up on autopilot." sub="Two things your phone does better with Nudge Drive." />
      <div className="mx-auto mt-14 grid max-w-[1180px] grid-cols-1 gap-5 lg:grid-cols-2">
        <article className="ld-reveal ld-zoom ld-bento flex flex-col" aria-labelledby="nb-h">
          <div className="p-7 sm:p-9">
            <span className="soft-tile grid h-12 w-12 place-items-center rounded-[16px]" style={{ '--k': '#1e9e57' } as CSSProperties}><Radar size={23} aria-hidden /></span>
            <h3 id="nb-h" className="mt-5 text-[26px] font-bold tracking-[-0.03em] text-ink sm:text-[30px]">Nearby share</h3>
            <p className="mt-2 max-w-md text-[15.5px] leading-relaxed text-muted">Send files phone to phone, or to a computer running Nudge Drive on the same Wi-Fi, with no upload.</p>
            <ul className="mt-5 grid gap-3">
              <Point icon={Bluetooth}>Over Bluetooth and Wi-Fi, no internet needed</Point>
              <Point icon={KeyRound}>Both sides see a matching code, and nothing moves until the receiver accepts</Point>
            </ul>
          </div>
          <div className="relative mt-auto h-[360px] overflow-hidden">
            <div className="ld-rings" aria-hidden><i /><i /><i /></div>
            <div className="ld-bento-phone relative z-[1]">
              <Phone depth={false} img={{ src: PHONE.nearby, alt: 'Nudge Drive Nearby share screen: received 3 files, saved in Downloads' }} />
            </div>
            <span className="ld-float-chip left-[6%] top-[18%] max-sm:hidden"><span className="grid h-7 w-7 place-items-center rounded-[10px] bg-success-soft text-success"><Wifi size={15} aria-hidden /></span>Same Wi-Fi</span>
            <span className="ld-float-chip right-[6%] top-[42%] max-sm:hidden" style={{ animationDelay: '-3s' }}><span className="grid h-7 w-7 place-items-center rounded-[10px] bg-mist text-link"><Smartphone size={15} aria-hidden /></span>Code 4271</span>
          </div>
        </article>

        <article className="ld-reveal ld-zoom ld-bento flex flex-col" style={{ '--d': '120ms' } as CSSProperties} aria-labelledby="bk-h">
          <div className="p-7 sm:p-9">
            <span className="soft-tile grid h-12 w-12 place-items-center rounded-[16px]" style={{ '--k': '#2e64e0' } as CSSProperties}><FolderSync size={23} aria-hidden /></span>
            <h3 id="bk-h" className="mt-5 text-[26px] font-bold tracking-[-0.03em] text-ink sm:text-[30px]">Auto backup</h3>
            <p className="mt-2 max-w-md text-[15.5px] leading-relaxed text-muted">Pick the phone folders to keep safe, like Camera and Screenshots, and Nudge Drive backs them up every few hours.</p>
            <ul className="mt-5 grid gap-3">
              <Point icon={BatteryCharging}>Only on Wi-Fi, or only while charging, if you like</Point>
              <Point icon={Images}>Back up only new photos, or older ones too</Point>
            </ul>
          </div>
          <div className="relative mt-auto h-[360px] overflow-hidden">
            <div className="ld-bento-phone relative z-[1]">
              <Phone depth={false} img={{ src: PHONE.backup, alt: 'Nudge Drive Auto backup settings with folders and Wi-Fi only switches' }} />
            </div>
            <span className="ld-float-chip left-[6%] top-[22%] max-sm:hidden" style={{ animationDelay: '-2s' }}><span className="grid h-7 w-7 place-items-center rounded-[10px] bg-mist text-link"><Images size={15} aria-hidden /></span>Camera ✓</span>
            <span className="ld-float-chip right-[6%] top-[46%] max-sm:hidden" style={{ animationDelay: '-4s' }}><span className="grid h-7 w-7 place-items-center rounded-[10px] bg-warn-soft text-warn"><BatteryCharging size={15} aria-hidden /></span>While charging</span>
          </div>
        </article>
      </div>
    </section>
  )
}

/* ------------------------------------------------------------------------------- Web + desktop */

function Desktop({ dark }: { dark: boolean }) {
  const ref = useRef<HTMLDivElement>(null)
  const rig = useRef<HTMLDivElement>(null)
  const onProgress = useCallback((p: number) => {
    const e = 1 - Math.pow(1 - p, 3)
    if (rig.current) rig.current.style.transform = `rotateX(${(1 - e) * 26}deg) scale(${0.9 + e * 0.1}) translateY(${(1 - e) * 40}px)`
  }, [])
  useScrollProgress(ref, onProgress, 0.3)
  const tiles: { icon: LucideIcon; title: string; body: string; badge?: string }[] = [
    { icon: Globe, title: 'Any modern browser', body: 'Drag in files or whole folders. Big uploads resume where they left off.' },
    { icon: Monitor, title: 'A Windows app', body: 'Backs up the PC folders you choose, receives Nearby shares from your phone and frees up space, like the phone app.', badge: 'Coming soon' },
    { icon: MonitorDown, title: 'Install it as an app', body: 'Add Nudge Drive to your desktop or home screen from the browser.' },
    { icon: PenLine, title: 'Edit documents in place', body: 'Open Word, Excel and PowerPoint files and edit them right in the browser, without downloading.' },
    { icon: Bell, title: 'One inbox for updates', body: 'Finished uploads, Drive imports and storage warnings land in one inbox, next to a log of recent activity.' },
    { icon: CodeXml, title: 'An API for your scripts', body: 'Create API keys and automate uploads and downloads.' },
  ]
  return (
    <section id="desktop" aria-labelledby="desk-h" className="overflow-hidden px-4 py-24 sm:px-6 sm:py-28 max-sm:py-20">
      <SectionHead id="desk-h" kicker="Web & desktop" title="The same pool on a big screen" sub="Everything you do on your phone works on the web too, in light or dark, and a Windows app is on its way." />
      <div ref={ref} className="ld-desk mx-auto mt-14 max-w-[1080px]">
        <div ref={rig} className="ld-desk-rig" style={{ transform: 'rotateX(26deg) scale(0.9) translateY(40px)' }}>
          <Laptop flat src={dark ? DESK.photosDark : DESK.photosLight} alt="Nudge Drive in a web browser: the Photos page with a grid of pictures grouped by month" style={{ fontSize: 'min(9.4px, calc((100vw - 32px) / 100 * 0.86))', margin: '0 auto', width: 'max-content' }} />
        </div>
      </div>
      <ul className="mx-auto mt-16 grid max-w-[1080px] grid-cols-1 gap-4 md:grid-cols-3">
        {tiles.map((t, i) => (
          <li key={t.title} className="ld-reveal ld-card p-6" style={{ '--d': `${i * 100}ms` } as CSSProperties}>
            <span className="grid h-11 w-11 place-items-center rounded-[14px] bg-mist text-link"><t.icon size={21} aria-hidden /></span>
            <h3 className="mt-4 flex flex-wrap items-center gap-2 text-[18px] font-bold tracking-[-0.02em] text-ink">
              {t.title}
              {t.badge && <span className="rounded-full bg-mist px-2 py-0.5 text-[11.5px] font-semibold tracking-normal text-link">{t.badge}</span>}
            </h3>
            <p className="mt-1.5 text-[14.5px] leading-relaxed text-muted">{t.body}</p>
          </li>
        ))}
      </ul>
    </section>
  )
}

/* ------------------------------------------------------------------------------------- Privacy */

function Privacy() {
  const items: { icon: LucideIcon; color: string; title: string; body: string }[] = [
    { icon: HardDrive, color: '#7059f0', title: 'Files stay in your storage', body: 'Everything lives in your own Google Drive or S3 bucket, not on our servers.' },
    { icon: Ban, color: '#e8402b', title: 'No ads, no tracking', body: 'No analytics, crash-reporting or ad SDKs. Your files are never used to train AI.' },
    { icon: Link2, color: '#1e9e57', title: 'You control sharing', body: 'A share link only exists once you create it, and you can turn any link off again.' },
    { icon: Trash2, color: '#2e64e0', title: 'Leave any time', body: 'Delete your account from the app or the website. Your files stay in your own storage.' },
  ]
  return (
    <section aria-labelledby="privacy-h" className="px-4 py-24 sm:px-6 sm:py-28 max-sm:py-20">
      <SectionHead id="privacy-h" kicker="Private by design" title="Your files stay yours" />
      <ul className="mx-auto mt-14 grid max-w-[1180px] grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {items.map((it, i) => (
          <li key={it.title} className="ld-reveal ld-card p-6" style={{ '--d': `${i * 90}ms` } as CSSProperties}>
            <span className="soft-tile grid h-12 w-12 place-items-center rounded-[16px]" style={{ '--k': it.color } as CSSProperties}><it.icon size={22} aria-hidden /></span>
            <h3 className="mt-4 text-[18px] font-bold tracking-[-0.02em] text-ink">{it.title}</h3>
            <p className="mt-1.5 text-[14.5px] leading-relaxed text-muted">{it.body}</p>
          </li>
        ))}
      </ul>
    </section>
  )
}

/* ----------------------------------------------------------------------------------------- FAQ */

const FAQ: { q: string; a: ReactNode }[] = [
  { q: 'Is Nudge Drive free?', a: 'Yes. Nudge Drive is free to use. The storage you get is the storage your connected accounts have: each free Google account comes with 15 GB, shared with its Gmail and Google Photos.' },
  { q: 'Where are my files stored?', a: 'In the Google Drive accounts and S3-compatible buckets you connect. Nudge Drive keeps track of where each file is, so you see one pool, but the files themselves stay in your own storage.' },
  { q: 'How does it choose where an upload goes?', a: 'You pick the rule: the drive with the most free space, take turns between drives, or follow an order you set. You can change it any time on the Drives screen.' },
  { q: 'Can I use S3 instead of Google Drive?', a: 'Yes. Add any S3-compatible bucket alongside your Google accounts, or use buckets on their own.' },
  {
    q: 'My Google storage is full. Can Nudge Drive help?',
    a: 'Yes. Open Free up space and pick the full account. Nudge Drive can move its Drive files to your other drives and remove the originals, and copy photos and videos out of Google Photos so you can delete them there. Gmail shares the same 15 GB but has to be cleaned up in Gmail itself.',
  },
  { q: 'What happens to files already in my Google Drive?', a: 'When you connect a Drive, the files already in it are imported into Nudge Drive by themselves, so you can see and use them right away. They stay where they are.' },
  { q: 'Is there an Android app?', a: 'The Android app is coming soon to Google Play. Until then you can use Nudge Drive in any modern browser and install it as an app from there.' },
  { q: 'Is there a desktop app?', a: 'A Windows app is on its way. It backs up the PC folders you choose and sends and receives Nearby shares. Until then, the website works in any modern browser and can be installed as an app.' },
  { q: 'Is Nudge Drive made by Google?', a: 'No. Nudge Drive is an independent app and is not affiliated with or endorsed by Google. Google Drive is a trademark of Google LLC.' },
  {
    q: 'How do I delete my account?',
    a: <>From the app (Profile › Delete account) or on the <Link to="/delete-account" className="font-semibold text-link underline underline-offset-2">delete account page</Link>. Your files stay in your own storage. Questions? Email <a href={`mailto:${SUPPORT_EMAIL}`} className="font-semibold text-link underline underline-offset-2">{SUPPORT_EMAIL}</a>.</>,
  },
]

function Faq() {
  return (
    <section id="faq" aria-labelledby="faq-h" className="px-4 py-24 sm:px-6 sm:py-28 max-sm:py-20">
      <SectionHead id="faq-h" kicker="FAQ" title="Questions, answered" />
      <div className="ld-faq ld-reveal mx-auto mt-12 max-w-3xl">
        {FAQ.map((f) => (
          <details key={f.q}>
            <summary>{f.q}<span className="ld-faq-icon" aria-hidden><Plus size={17} /></span></summary>
            <p className="pb-6 pl-1 pr-12 text-[15.5px] leading-relaxed text-muted">{f.a}</p>
          </details>
        ))}
      </div>
    </section>
  )
}

/* ----------------------------------------------------------------------------------------- CTA */

function FinalCta() {
  return (
    <section aria-labelledby="cta-h" className="px-4 pb-24 sm:px-6">
      <div className="ld-reveal ld-zoom ld-cta mx-auto grid max-w-[1180px] grid-cols-1 items-center gap-8 px-7 py-14 sm:px-14 lg:grid-cols-[1fr_auto] lg:py-16">
        <div className="relative z-10 text-center lg:text-left">
          <h2 id="cta-h" className="text-[34px] font-bold leading-[1.08] tracking-[-0.04em] sm:text-[46px]">Start your storage pool today</h2>
          <p className="mx-auto mt-4 max-w-xl text-[17px] leading-relaxed text-white/85 lg:mx-0">Free to use. Works in any modern browser, installs as an app, and the Android and Windows apps are on their way.</p>
          <div className="mt-8 flex flex-wrap justify-center gap-3 lg:justify-start">
            <ButtonLink to="/register" size="lg" variant="inverse" className="!bg-white !text-[#1e1440]">Create an account<ArrowRight size={18} aria-hidden /></ButtonLink>
            <ButtonLink to="/login" size="lg" variant="ghost" className="!text-white hover:!bg-white/15">Sign in</ButtonLink>
          </div>
          <div className="mt-5 flex justify-center lg:justify-start"><GooglePlayBadge onDark /></div>
        </div>
        <div className="relative z-10 mx-auto hidden h-[300px] w-[300px] sm:block" aria-hidden>
          <div className="absolute left-[10px] top-[20px] -rotate-[8deg]" style={{ fontSize: '1.45px' }}><Phone depth={false} img={{ src: PHONE.photos, alt: '' }} /></div>
          <div className="absolute left-[130px] top-[0px] rotate-[7deg]" style={{ fontSize: '1.45px' }}><Phone depth={false} img={{ src: PHONE.home, alt: '' }} /></div>
        </div>
      </div>
    </section>
  )
}

/* ---------------------------------------------------------------------------------------- Page */

/** Public landing page for signed-out visitors at "/". */
export default function LandingPage() {
  useDocumentTitle('')
  const { dark } = useTheme()
  const root = useRef<HTMLDivElement>(null)
  useReveal(root)
  // The hero laptop screen is the largest paint: start fetching it right away.
  preload(dark ? DESK.homeDark : DESK.homeLight, { as: 'image', fetchPriority: 'high' })
  preload(PHONE.home, { as: 'image' })

  return (
    <div ref={root} className="ld-root">
      <a href="#main" className="ld-skip">Skip to content</a>
      <LandingHeader />
      <main id="main">
        <Hero dark={dark} />
        <HowItWorks />
        <section id="features" aria-labelledby="features-h" className="px-4 py-24 sm:px-6 sm:py-28 max-sm:py-20">
          <SectionHead id="features-h" kicker="Inside the app" title="Everything in one calm place" sub="Real screens from the Nudge Drive Android app." />
          <div className="mx-auto mt-10 max-w-[1100px] lg:mt-0">
            <Showcase />
          </div>
        </section>
        <FreeUp />
        <NearbyAndBackup />
        <Desktop dark={dark} />
        <Privacy />
        <Faq />
        <FinalCta />
      </main>
      <LandingFooter />
    </div>
  )
}
