import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Moon, Sun } from 'lucide-react'
import { Wordmark } from '@/components/brand/Brand'
import { ButtonLink, IconButton } from '@/components/ui/Button'
import { useAuth } from '@/context/AuthContext'
import { useTheme } from '@/context/ThemeContext'

/** Header + footer for pages anyone can open (landing, legal, account deletion). */
export function PublicLayout({ children }: { children: ReactNode }) {
  const { signedIn } = useAuth()
  const { dark, toggle } = useTheme()
  return (
    <div className="flex min-h-dvh flex-col">
      <div className="app-bg" aria-hidden />
      <header className="sticky top-0 z-30 border-b border-glass-border bg-[var(--nav-tint)] backdrop-blur-xl">
        <div className="mx-auto flex h-16 max-w-6xl items-center gap-3 px-5">
          <Link to="/" className="rounded-xl"><Wordmark size={17} /></Link>
          <nav aria-label="Site" className="ml-auto flex items-center gap-2">
            <IconButton icon={dark ? Sun : Moon} label={dark ? 'Switch to light mode' : 'Switch to dark mode'} onClick={toggle} size={40} />
            {signedIn ? (
              <ButtonLink to="/home" size="sm">Open Nudge Drive</ButtonLink>
            ) : (
              <>
                <ButtonLink to="/login" size="sm" variant="ghost" className="max-sm:hidden">Sign in</ButtonLink>
                <ButtonLink to="/register" size="sm">Get started</ButtonLink>
              </>
            )}
          </nav>
        </div>
      </header>
      <main id="main" className="flex-1">{children}</main>
      <footer className="border-t border-line/70">
        <div className="mx-auto flex max-w-6xl flex-col items-center gap-3 px-5 py-8 text-[13px] text-muted sm:flex-row">
          <Wordmark size={14} />
          <nav aria-label="Legal" className="flex flex-wrap justify-center gap-x-5 gap-y-1 sm:ml-auto">
            <Link to="/privacy" className="hover:text-ink hover:underline">Privacy policy</Link>
            <Link to="/terms" className="hover:text-ink hover:underline">Terms of service</Link>
            <Link to="/delete-account" className="hover:text-ink hover:underline">Delete account</Link>
            <Link to="/login" className="hover:text-ink hover:underline">Sign in</Link>
          </nav>
          <p className="sm:ml-6">© {new Date().getFullYear()} Nudge Drive</p>
        </div>
      </footer>
    </div>
  )
}
