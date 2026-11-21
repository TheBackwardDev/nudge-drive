import { useEffect, useState, type CSSProperties, type HTMLAttributes, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import clsx from 'clsx'
import { ChevronRight, type LucideIcon } from 'lucide-react'

/** Frosted card (GlassCard). */
export function GlassCard({ strong, className, children, ...rest }: HTMLAttributes<HTMLDivElement> & { strong?: boolean }) {
  return (
    <div className={clsx(strong ? 'glass-strong' : 'glass', 'rounded-[26px] shadow-soft', className)} {...rest}>
      {children}
    </div>
  )
}

/** Solid card used for list rows and panels. */
export function Panel({ className, children, ...rest }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={clsx('rounded-[24px] border border-card-border bg-surface shadow-soft', className)} {...rest}>
      {children}
    </div>
  )
}

export function SectionTitle({ title, action, onAction, to, className, as: Tag = 'h2' }: { title: ReactNode; action?: string; onAction?: () => void; to?: string; className?: string; as?: 'h2' | 'h3' }) {
  return (
    <div className={clsx('flex items-center gap-2', className)}>
      <Tag className="flex-1 text-[19px] font-semibold text-ink">{title}</Tag>
      {action && (onAction || to) ? (
        to ? (
          <Link to={to} className="press flex items-center rounded-xl px-2 py-1.5 text-[13px] font-semibold text-link hover:bg-hover">
            {action}<ChevronRight size={16} aria-hidden />
          </Link>
        ) : (
          <button type="button" onClick={onAction} className="press flex items-center rounded-xl px-2 py-1.5 text-[13px] font-semibold text-link hover:bg-hover">
            {action}<ChevronRight size={16} aria-hidden />
          </button>
        )
      ) : null}
    </div>
  )
}

export function EmptyState({ icon: Icon, title, body, action, className }: { icon: LucideIcon; title: string; body?: ReactNode; action?: ReactNode; className?: string }) {
  return (
    <div className={clsx('flex flex-col items-center px-6 py-10 text-center', className)}>
      <div className="glass flex h-[84px] w-[84px] items-center justify-center rounded-[28px] text-iris">
        <Icon size={38} aria-hidden />
      </div>
      <h3 className="mt-4 text-[16px] font-semibold text-ink">{title}</h3>
      {body && <p className="mt-1.5 max-w-sm text-[14px] text-muted">{body}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}

export function Shimmer({ className, style }: { className?: string; style?: CSSProperties }) {
  return <div aria-hidden className={clsx('shimmer rounded-2xl', className)} style={style} />
}

export function Chip({ selected, icon: Icon, children, onClick, count }: { selected: boolean; icon?: LucideIcon; children: ReactNode; onClick: () => void; count?: number }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={selected}
      className={clsx(
        'press inline-flex h-[38px] shrink-0 items-center gap-1.5 rounded-[18px] border px-3.5 text-[13px] font-semibold',
        selected ? 'border-inverse bg-inverse text-on-inverse' : 'glass text-ink hover:bg-glass-strong',
      )}
    >
      {Icon && <Icon size={16} aria-hidden />}
      {children}
      {count !== undefined && <span className={clsx('text-[11px]', selected ? 'opacity-70' : 'text-subtle')}>{count}</span>}
    </button>
  )
}

/** Small rounded label: "3 drives", "active", "Public link on". */
export function Pill({ tone = 'mist', className, children }: { tone?: 'mist' | 'success' | 'warn' | 'danger' | 'primary'; className?: string; children: ReactNode }) {
  return (
    <span
      className={clsx(
        'inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-[11.5px] font-semibold',
        tone === 'mist' && 'bg-mist text-ink',
        tone === 'success' && 'bg-success-soft text-success',
        tone === 'warn' && 'bg-warn-soft text-warn',
        tone === 'danger' && 'bg-danger-soft text-danger',
        tone === 'primary' && 'bg-primary text-white',
        className,
      )}
    >
      {children}
    </span>
  )
}

/** Coloured tile behind an icon (Nd.soft). */
export function SoftTile({ color, size = 40, radius = 13, children, className }: { color: string; size?: number; radius?: number; children: ReactNode; className?: string }) {
  return (
    <span className={clsx('soft-tile inline-flex shrink-0 items-center justify-center', className)} style={{ '--k': color, width: size, height: size, borderRadius: radius } as CSSProperties}>
      {children}
    </span>
  )
}

/** True one frame after mount, so bars and rings can animate up from zero (animateFloatAsState). */
function useAppeared() {
  const [on, setOn] = useState(false)
  useEffect(() => {
    const id = requestAnimationFrame(() => requestAnimationFrame(() => setOn(true)))
    return () => cancelAnimationFrame(id)
  }, [])
  return on
}

/** Diagonal-striped storage bar; slides to its value (transform only) while the stripes drift. */
export function StripedBar({ fraction, height = 16, label }: { fraction: number; height?: number; label: string }) {
  const pct = Math.round(Math.min(1, Math.max(0, fraction)) * 100)
  const shown = useAppeared()
  return (
    <div role="progressbar" aria-label={label} aria-valuemin={0} aria-valuemax={100} aria-valuenow={pct} className="stripe-track w-full" style={{ height }}>
      <div className="stripe-fill" style={{ '--p': shown ? Math.max(2, Math.min(1, Math.max(0, fraction)) * 100) : 0 } as CSSProperties} />
    </div>
  )
}

/** Usage ring with a percentage (or custom) label; the arc sweeps in from zero. */
export function UsageRing({ fraction, size = 56, stroke = 7, label, color = '#8b72f5' }: { fraction: number; size?: number; stroke?: number; label?: string; color?: string }) {
  const f = Math.min(1, Math.max(0, fraction))
  const r = (size - stroke) / 2
  const c = 2 * Math.PI * r
  const shown = useAppeared()
  const arc = f > 0 ? Math.max(f, 0.015) : 0
  return (
    <span className="relative inline-flex shrink-0 items-center justify-center" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90" aria-hidden>
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--mist)" strokeWidth={stroke} />
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={color} strokeWidth={stroke} strokeLinecap="round" strokeDasharray={c} strokeDashoffset={shown ? c * (1 - arc) : c} className="ring-arc" />
      </svg>
      <span className="absolute font-display font-bold text-ink" style={{ fontSize: Math.round(size * 0.22) }}>{label ?? `${Math.round(f * 100)}%`}</span>
    </span>
  )
}

/** Tinted notice row (AlertCard / OfflineCard). */
export function Notice({ tone = 'warn', icon: Icon, title, children, action, className }: { tone?: 'warn' | 'danger' | 'success' | 'info'; icon: LucideIcon; title: ReactNode; children?: ReactNode; action?: ReactNode; className?: string }) {
  return (
    <div
      role={tone === 'danger' ? 'alert' : 'status'}
      className={clsx(
        'flex items-start gap-3 rounded-[22px] p-4',
        tone === 'warn' && 'bg-warn-soft',
        tone === 'danger' && 'bg-danger-soft',
        tone === 'success' && 'bg-success-soft',
        tone === 'info' && 'bg-mist',
        className,
      )}
    >
      <Icon size={22} aria-hidden className={clsx('mt-0.5 shrink-0', tone === 'warn' && 'text-warn', tone === 'danger' && 'text-danger', tone === 'success' && 'text-success', tone === 'info' && 'text-accent')} />
      <div className="min-w-0 flex-1">
        <p className="text-[14px] font-semibold text-ink">{title}</p>
        {children && <div className="mt-0.5 text-[13px] text-muted">{children}</div>}
      </div>
      {action}
    </div>
  )
}

export function Switch({ checked, onChange, label, description }: { checked: boolean; onChange: (v: boolean) => void; label: string; description?: string }) {
  return (
    <label className="flex cursor-pointer items-center gap-4 py-2">
      <span className="min-w-0 flex-1">
        <span className="block text-[14.5px] font-semibold text-ink">{label}</span>
        {description && <span className="block text-[13px] text-muted">{description}</span>}
      </span>
      <input type="checkbox" role="switch" checked={checked} onChange={(e) => onChange(e.target.checked)} className="peer sr-only" />
      <span aria-hidden className="relative h-7 w-12 shrink-0 rounded-full bg-lilac transition-colors duration-300 peer-checked:bg-primary peer-focus-visible:ring-2 peer-focus-visible:ring-accent peer-focus-visible:ring-offset-2 after:absolute after:left-1 after:top-1 after:h-5 after:w-5 after:rounded-full after:bg-white after:shadow after:transition-transform after:duration-500 after:ease-[var(--spring-bouncy)] peer-checked:after:translate-x-5" />
    </label>
  )
}

/** Segmented control (theme picker, view mode). */
export function Segmented<T extends string>({ value, options, onChange, label }: { value: T; options: { value: T; label: string; icon?: LucideIcon }[]; onChange: (v: T) => void; label: string }) {
  return (
    <div role="radiogroup" aria-label={label} className="inline-flex rounded-[20px] bg-mist p-1">
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          role="radio"
          aria-checked={value === o.value}
          onClick={() => onChange(o.value)}
          className={clsx('press inline-flex h-9 items-center gap-1.5 rounded-[16px] px-3.5 text-[13px] font-semibold', value === o.value ? 'bg-surface text-ink shadow-soft' : 'text-muted hover:text-ink')}
        >
          {o.icon && <o.icon size={16} aria-hidden />}
          {o.label}
        </button>
      ))}
    </div>
  )
}

/** Tappable list row with icon, title, subtitle and chevron (Profile / Settings lists, ProfileRow). */
export function ListRow({ icon: Icon, color, title, subtitle, onClick, to, href, trailing, danger, badge }: { icon: LucideIcon; color?: string; title: string; subtitle?: string; onClick?: () => void; to?: string; href?: string; trailing?: ReactNode; danger?: boolean; badge?: number }) {
  const body = (
    <>
      {color && !danger ? (
        <SoftTile color={color} size={40} radius={13}><Icon size={19} aria-hidden /></SoftTile>
      ) : (
        <span className={clsx('relative inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-[13px]', danger ? 'bg-danger-soft text-danger' : 'bg-mist text-link')}>
          <Icon size={19} aria-hidden />
          {badge ? <span className="pop-in absolute -right-1 -top-1 flex h-[18px] min-w-[18px] items-center justify-center rounded-full border-2 border-surface bg-danger px-1 text-[9.5px] font-bold text-white dark:text-black">{badge > 9 ? '9+' : badge}</span> : null}
        </span>
      )}
      <span className="min-w-0 flex-1 text-left">
        <span className={clsx('block truncate text-[14.5px] font-semibold', danger ? 'text-danger' : 'text-ink')}>{title}</span>
        {subtitle && <span className="block truncate text-[12.5px] text-muted">{subtitle}</span>}
      </span>
      {trailing ?? (danger ? null : <ChevronRight size={18} className="shrink-0 text-subtle transition-transform duration-200 group-hover:translate-x-0.5" aria-hidden />)}
    </>
  )
  const cls = 'group press press-row flex w-full items-center gap-3 rounded-[18px] px-3 py-2.5 hover:bg-hover'
  if (to) return <Link to={to} className={cls}>{body}</Link>
  if (href) return <a href={href} target={href.startsWith('http') ? '_blank' : undefined} rel="noreferrer" className={cls}>{body}</a>
  return <button type="button" onClick={onClick} className={cls}>{body}</button>
}
