import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import clsx from 'clsx'
import { ArrowUp, Bell, BellRing, Moon, Sun } from 'lucide-react'
import { Shimmer } from '@/components/ui/Surface'
import { useTheme } from '@/context/ThemeContext'
import { useUnreadCount } from '@/lib/notices'

const round = 'press relative inline-flex shrink-0 items-center justify-center rounded-full glass text-ink hover:bg-glass-strong'

/** Sun / moon button. The switch itself is a circular reveal growing from this button. */
export function ThemeToggle({ size = 44, className }: { size?: number; className?: string }) {
  const { dark, toggle } = useTheme()
  const Icon = dark ? Sun : Moon
  const label = dark ? 'Switch to light mode' : 'Switch to dark mode'
  return (
    <button type="button" aria-label={label} title={label} onClick={(e) => toggle(e)} className={clsx(round, className)} style={{ width: size, height: size }}>
      <Icon key={dark ? 'sun' : 'moon'} size={Math.round(size * 0.44)} className="spin-in" aria-hidden />
    </button>
  )
}

/** Bell with an unread badge (Home header in the app). */
export function BellButton({ size = 44, className }: { size?: number; className?: string }) {
  const unread = useUnreadCount()
  const Icon = unread > 0 ? BellRing : Bell
  return (
    <Link to="/notifications" aria-label={unread ? `Notifications, ${unread} unread` : 'Notifications'} title="Notifications" className={clsx(round, className)} style={{ width: size, height: size }}>
      <Icon size={Math.round(size * 0.44)} aria-hidden />
      {unread > 0 && (
        <span key={unread} className="pop-in absolute -right-0.5 -top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full border-2 border-surface bg-danger px-1 text-[10px] font-bold text-white dark:text-black">
          {unread > 9 ? '9+' : unread}
        </span>
      )}
    </Link>
  )
}

/** Round "back to top" button that pops in once the page has scrolled a long way. */
export function ScrollToTop() {
  const [show, setShow] = useState(false)
  useEffect(() => {
    let raf = 0
    const on = () => {
      cancelAnimationFrame(raf)
      raf = requestAnimationFrame(() => setShow(window.scrollY > 1400))
    }
    window.addEventListener('scroll', on, { passive: true })
    return () => {
      cancelAnimationFrame(raf)
      window.removeEventListener('scroll', on)
    }
  }, [])
  if (!show) return null
  return (
    <button
      type="button"
      aria-label="Back to top"
      onClick={() => window.scrollTo({ top: 0, behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' })}
      className="press pop-in fixed bottom-[calc(100px+env(safe-area-inset-bottom))] right-5 z-40 flex h-[50px] w-[50px] items-center justify-center rounded-full border border-glass-border bg-primary text-white shadow-[0_14px_30px_-12px_rgb(112_89_240/0.8)] lg:bottom-8 lg:right-8"
    >
      <ArrowUp size={22} aria-hidden />
    </button>
  )
}

/** Loading placeholder while a screen's code arrives: the app's shimmer blocks, not a spinner. */
export function PageSkeleton() {
  return (
    <div aria-busy="true" aria-label="Loading" className="rise grid grid-cols-1 gap-4 pt-4">
      <Shimmer className="h-9 w-48 rounded-[14px]" />
      <Shimmer className="h-[54px] rounded-[28px]" />
      <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
        {[0, 1, 2, 3].map((i) => <Shimmer key={i} className="h-[66px] rounded-[20px]" />)}
      </div>
    </div>
  )
}
