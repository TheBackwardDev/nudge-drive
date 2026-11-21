import { useId } from 'react'
import clsx from 'clsx'

/** Centre line of the "Kick n" mark on a 256 grid, drawn with a 40-unit round stroke (ui/brand/Logo.kt). */
const KICK_N = 'M56 202V110a56 56 0 0 1 112 0v60a32 32 0 0 0 32 32'

export function KickN({ size = 24, color = 'currentColor', className }: { size?: number; color?: string; className?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 256 256" className={className} aria-hidden>
      <path d={KICK_N} fill="none" stroke={color} strokeWidth={40} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

/** App icon tile: white Kick-n on the lavender → iris glow. */
export function LogoTile({ size = 40, className }: { size?: number; className?: string }) {
  const id = useId()
  return (
    <svg width={size} height={size} viewBox="0 0 256 256" className={clsx('shrink-0', className)} role="img" aria-label="Nudge Drive">
      <defs>
        <linearGradient id={id} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#cdbfff" />
          <stop offset="1" stopColor="#8b72f5" />
        </linearGradient>
      </defs>
      <rect width="256" height="256" rx="72" fill={`url(#${id})`} />
      <g transform="translate(48.6 48.6) scale(0.62)">
        <path d={KICK_N} fill="none" stroke="#fff" strokeWidth={40} strokeLinecap="round" strokeLinejoin="round" />
      </g>
    </svg>
  )
}

export function Wordmark({ size = 18, className }: { size?: number; className?: string }) {
  return (
    <span className={clsx('flex items-center gap-2.5', className)}>
      <LogoTile size={Math.round(size * 1.9)} />
      <span className="font-display font-bold tracking-[-0.02em] text-ink" style={{ fontSize: size }}>Nudge Drive</span>
    </span>
  )
}

export function GoogleLogo({ size = 18 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden>
      <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
      <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C4 20.52 7.7 23 12 23z" />
      <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" />
      <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 4 3.48 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" />
    </svg>
  )
}

/** Google Drive triangle mark. */
export function DriveGlyph({ size = 16 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 87.3 78" aria-hidden>
      <path d="m6.6 66.85 3.85 6.65c.8 1.4 1.95 2.5 3.3 3.3l13.75-23.8h-27.5c0 1.55.4 3.1 1.2 4.5z" fill="#0066da" />
      <path d="m43.65 25-13.75-23.8c-1.35.8-2.5 1.9-3.3 3.3l-25.4 44a9.06 9.06 0 0 0 -1.2 4.5h27.5z" fill="#00ac47" />
      <path d="m73.55 76.8c1.35-.8 2.5-1.9 3.3-3.3l1.6-2.75 7.65-13.25c.8-1.4 1.2-2.95 1.2-4.5h-27.502l5.852 11.5z" fill="#ea4335" />
      <path d="m43.65 25 13.75-23.8c-1.35-.8-2.9-1.2-4.5-1.2h-18.5c-1.6 0-3.15.45-4.5 1.2z" fill="#00832d" />
      <path d="m59.8 53h-32.3l-13.75 23.8c1.35.8 2.9 1.2 4.5 1.2h50.8c1.6 0 3.15-.45 4.5-1.2z" fill="#2684fc" />
      <path d="m73.4 26.5-12.7-22c-.8-1.4-1.95-2.5-3.3-3.3l-13.75 23.8 16.15 28h27.45c0-1.55-.4-3.1-1.2-4.5z" fill="#ffba00" />
    </svg>
  )
}
