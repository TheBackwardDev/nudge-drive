import { useEffect, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Layers, ShieldCheck, Zap } from 'lucide-react'
import { GoogleLogo, LogoTile } from '@/components/brand/Brand'
import { Button } from '@/components/ui/Button'
import { api, errorMessage } from '@/lib/api'
import { useDocumentTitle } from '@/lib/hooks'

export function LegalLinks({ className = '' }: { className?: string }) {
  return (
    <nav aria-label="Legal" className={`flex flex-wrap justify-center gap-x-4 gap-y-1 text-[12.5px] text-muted ${className}`}>
      <Link to="/privacy" className="hover:text-ink hover:underline">Privacy</Link>
      <Link to="/terms" className="hover:text-ink hover:underline">Terms</Link>
      <Link to="/delete-account" className="hover:text-ink hover:underline">Delete account</Link>
    </nav>
  )
}

const POINTS = [
  { icon: Layers, title: 'One pool for every drive', body: '15 GB free per Google account, added together.' },
  { icon: Zap, title: 'Smart uploads', body: 'Each file goes to the drive with the most room.' },
  { icon: ShieldCheck, title: 'Your files stay yours', body: 'Everything lives in your own Google Drive or S3.' },
]

/** Lavender auth backdrop with the brand story beside the form on wide screens. */
export function AuthLayout({ title, subtitle, children, footer }: { title: string; subtitle: ReactNode; children: ReactNode; footer?: ReactNode }) {
  useDocumentTitle(title)
  return (
    <div className="flex min-h-dvh flex-col">
      <div className="auth-bg" aria-hidden />
      <div className="mx-auto grid w-full max-w-6xl flex-1 items-center gap-10 px-5 py-8 lg:grid-cols-[1fr_460px] lg:px-10">
        <section className="hidden lg:block">
          <Link to="/" className="inline-flex items-center gap-3"><LogoTile size={48} /><span className="font-display text-[22px] font-bold text-ink">Nudge Drive</span></Link>
          <h2 className="mt-10 max-w-md text-[44px] font-bold leading-[1.08] tracking-[-0.04em] text-ink">All your drives.<br />One calm place.</h2>
          <ul className="mt-8 grid max-w-md gap-4">
            {POINTS.map((p) => (
              <li key={p.title} className="glass flex items-center gap-4 rounded-[22px] p-4">
                <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[14px] bg-primary text-white"><p.icon size={21} aria-hidden /></span>
                <span>
                  <span className="block text-[15px] font-semibold text-ink">{p.title}</span>
                  <span className="block text-[13.5px] text-muted">{p.body}</span>
                </span>
              </li>
            ))}
          </ul>
        </section>
        <main className="mx-auto w-full max-w-[460px]">
          <div className="mb-7 flex flex-col items-center text-center lg:hidden">
            <Link to="/" aria-label="Nudge Drive home"><LogoTile size={64} /></Link>
          </div>
          <div className="glass-strong rounded-[32px] p-6 shadow-lift sm:p-8">
            <h1 className="text-[28px] font-bold leading-tight tracking-[-0.03em] text-ink">{title}</h1>
            <p className="mt-1.5 text-[14.5px] text-muted">{subtitle}</p>
            <div className="mt-6">{children}</div>
          </div>
          {footer && <div className="mt-5 text-center text-[14px] text-muted">{footer}</div>}
          <LegalLinks className="mt-6" />
        </main>
      </div>
    </div>
  )
}

export function OrDivider() {
  return (
    <div className="my-5 flex items-center gap-3 text-[12px] font-semibold uppercase tracking-wider text-subtle" role="separator">
      <span className="h-px flex-1 bg-line" />or<span className="h-px flex-1 bg-line" />
    </div>
  )
}

/** "Continue with Google": the server returns Google's consent URL and later redirects to /google-auth. */
export function GoogleButton({ label = 'Continue with Google', onError, next }: { label?: string; onError: (m: string) => void; next?: string }) {
  const [state, setState] = useState<'idle' | 'redirecting'>('idle')
  const busy = state === 'redirecting'

  // Back from Google with the browser's Back button: the page can come back from the bfcache still "redirecting".
  useEffect(() => {
    const onShow = (e: PageTransitionEvent) => {
      if (e.persisted) setState('idle')
    }
    window.addEventListener('pageshow', onShow)
    return () => window.removeEventListener('pageshow', onShow)
  }, [])

  async function go() {
    if (busy) return
    setState('redirecting')
    try {
      if (next) sessionStorage.setItem('nd:next', next)
      const { url } = await api.auth.googleUrl()
      window.location.href = url
    } catch (e) {
      onError(errorMessage(e, 'Google sign-in is not available right now.'))
      setState('idle')
    }
  }
  return (
    <Button variant="secondary" size="lg" block loading={busy} onClick={go} className="!font-semibold">
      {!busy && <GoogleLogo size={20} />}
      {label}
    </Button>
  )
}

