import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Mail, Moon, Play, Sun } from 'lucide-react'
import { Wordmark } from '@/components/brand/Brand'
import { ButtonLink, IconButton } from '@/components/ui/Button'
import { useAuth } from '@/context/AuthContext'
import { useTheme } from '@/context/ThemeContext'

/** Set when the Play Store listing is live. */
export const PLAY_STORE_URL = ''
export const SUPPORT_EMAIL = 'support@nudge.qd.je'

export const NAV = [
  { href: '#how', label: 'How it works' },
  { href: '#features', label: 'Features' },
  { href: '#freeup', label: 'Free up space' },
  { href: '#nearby', label: 'Nearby & backup' },
  { href: '#desktop', label: 'Web & desktop' },
  { href: '#faq', label: 'FAQ' },
]

export function GooglePlayBadge({ onDark = false }: { onDark?: boolean }) {
  const live = Boolean(PLAY_STORE_URL)
  const content = (
    <>
      <Play size={22} fill="currentColor" aria-hidden />
      <span className="text-left leading-tight">
        <span className="block text-[10.5px] font-semibold uppercase tracking-wider opacity-80">{live ? 'Get it on' : 'Coming soon to'}</span>
        <span className="block text-[16px] font-bold">Google Play</span>
      </span>
    </>
  )
  const cls = `press inline-flex h-14 items-center gap-3 rounded-[18px] px-5 ${onDark ? 'bg-white/14 text-white ring-1 ring-white/30' : 'bg-inverse text-on-inverse shadow-soft'}`
  return live ? (
    <a href={PLAY_STORE_URL} target="_blank" rel="noopener noreferrer" className={cls}>{content}</a>
  ) : (
    <span className={`${cls} cursor-default opacity-90`} role="img" aria-label="Android app coming soon to Google Play">{content}</span>
  )
}

export function LandingHeader() {
  const { signedIn } = useAuth()
  const { dark, toggle } = useTheme()
  const [scrolled, setScrolled] = useState(false)
  useEffect(() => {
    const on = () => setScrolled(window.scrollY > 8)
    on()
    window.addEventListener('scroll', on, { passive: true })
    return () => window.removeEventListener('scroll', on)
  }, [])
  return (
    <header className={`ld-header ${scrolled ? 'is-scrolled' : ''}`}>
      <div className="mx-auto flex h-16 max-w-[1240px] items-center gap-3 px-4 sm:px-6">
        <Link to="/" className="rounded-xl" aria-label="Nudge Drive home"><Wordmark size={17} /></Link>
        <nav aria-label="Sections" className="ml-6 hidden items-center gap-0.5 xl:flex">
          {NAV.map((n) => <a key={n.href} href={n.href} className="ld-navlink">{n.label}</a>)}
        </nav>
        <div className="ml-auto flex items-center gap-2">
          <IconButton icon={dark ? Sun : Moon} label={dark ? 'Switch to light mode' : 'Switch to dark mode'} onClick={toggle} size={40} />
          {signedIn ? (
            <ButtonLink to="/home" size="sm">Open Nudge Drive</ButtonLink>
          ) : (
            <>
              <ButtonLink to="/login" size="sm" variant="ghost" className="max-sm:hidden">Sign in</ButtonLink>
              <ButtonLink to="/register" size="sm">Get started</ButtonLink>
            </>
          )}
        </div>
      </div>
    </header>
  )
}

export function LandingFooter() {
  return (
    <footer className="border-t border-[var(--ld-hairline)]">
      <div className="mx-auto grid max-w-[1240px] grid-cols-2 gap-x-6 gap-y-10 px-4 py-14 sm:px-6 md:grid-cols-[1.4fr_1fr_1fr]">
        <div className="col-span-2 md:col-span-1">
          <Wordmark size={16} />
          <p className="mt-4 max-w-sm text-[14px] leading-relaxed text-muted">All your Google Drive accounts and S3 buckets in one storage pool. Free, with no ads and no tracking.</p>
          <a href={`mailto:${SUPPORT_EMAIL}`} className="mt-4 inline-flex items-center gap-2 rounded-lg text-[14px] font-semibold text-link hover:underline">
            <Mail size={16} aria-hidden />{SUPPORT_EMAIL}
          </a>
        </div>
        <nav aria-label="Product">
          <h2 className="text-[13px] font-bold uppercase tracking-[0.12em] text-subtle">Product</h2>
          <ul className="mt-4 grid gap-2.5 text-[14.5px]">
            {NAV.filter((n) => n.href !== '#faq').map((n) => <li key={n.href}><a href={n.href} className="text-muted hover:text-ink">{n.label}</a></li>)}
            <li><Link to="/register" className="text-muted hover:text-ink">Create an account</Link></li>
            <li><Link to="/login" className="text-muted hover:text-ink">Sign in</Link></li>
          </ul>
        </nav>
        <nav aria-label="Legal">
          <h2 className="text-[13px] font-bold uppercase tracking-[0.12em] text-subtle">Legal & help</h2>
          <ul className="mt-4 grid gap-2.5 text-[14.5px]">
            <li><Link to="/privacy" className="text-muted hover:text-ink">Privacy policy</Link></li>
            <li><Link to="/terms" className="text-muted hover:text-ink">Terms of service</Link></li>
            <li><Link to="/delete-account" className="text-muted hover:text-ink">Delete account</Link></li>
            <li><a href={`mailto:${SUPPORT_EMAIL}`} className="text-muted hover:text-ink">Contact support</a></li>
          </ul>
        </nav>
      </div>
      <div className="mx-auto flex max-w-[1240px] flex-col gap-2 border-t border-[var(--ld-hairline)] px-4 py-6 text-[12.5px] text-subtle sm:flex-row sm:px-6">
        <p>© {new Date().getFullYear()} Nudge Drive</p>
        <p className="sm:ml-auto">Nudge Drive is an independent app, not affiliated with or endorsed by Google. Google Drive is a trademark of Google LLC.</p>
      </div>
    </footer>
  )
}
