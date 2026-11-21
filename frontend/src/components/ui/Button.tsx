import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react'
import { Link, type LinkProps } from 'react-router-dom'
import clsx from 'clsx'
import type { LucideIcon } from 'lucide-react'
import { Spinner } from '@/components/ui/Spinner'

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'warn' | 'soft' | 'glass' | 'inverse'
type Size = 'lg' | 'md' | 'sm'

const variants: Record<Variant, string> = {
  primary: 'bg-primary text-white shadow-[0_12px_28px_-12px_rgb(112_89_240/0.7)] hover:bg-primary-hover disabled:shadow-none',
  secondary: 'bg-surface text-ink border border-line hover:border-lilac hover:bg-field-solid',
  ghost: 'text-ink hover:bg-hover',
  danger: 'bg-danger-soft text-danger hover:brightness-95 dark:hover:brightness-125',
  warn: 'bg-warn text-white hover:brightness-95 dark:text-black dark:hover:brightness-110',
  soft: 'bg-mist text-link hover:bg-lilac/60',
  glass: 'glass text-ink hover:bg-glass-strong',
  inverse: 'bg-inverse text-on-inverse hover:opacity-90',
}

const sizes: Record<Size, string> = {
  lg: 'h-14 px-6 text-[16px] gap-2.5 rounded-[28px]',
  md: 'h-11 px-5 text-[14px] gap-2 rounded-[22px]',
  sm: 'h-9 px-3.5 text-[13px] gap-1.5 rounded-[18px]',
}

const base = 'press inline-flex shrink-0 select-none items-center justify-center font-bold whitespace-nowrap disabled:opacity-50'

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: Variant
  size?: Size
  icon?: LucideIcon
  loading?: boolean
  block?: boolean
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', size = 'md', icon: Icon, loading, block, className, children, disabled, type = 'button', ...rest },
  ref,
) {
  const iconSize = size === 'lg' ? 20 : size === 'md' ? 18 : 16
  return (
    <button ref={ref} type={type} disabled={disabled || loading} aria-busy={loading || undefined} className={clsx(base, variants[variant], sizes[size], block && 'w-full', className)} {...rest}>
      {loading ? <Spinner size={iconSize} /> : Icon ? <Icon size={iconSize} aria-hidden /> : null}
      {children}
    </button>
  )
})

/** A router link styled like a button. */
export function ButtonLink({ variant = 'primary', size = 'md', icon: Icon, block, className, children, ...rest }: LinkProps & { variant?: Variant; size?: Size; icon?: LucideIcon; block?: boolean; children: ReactNode }) {
  const iconSize = size === 'lg' ? 20 : size === 'md' ? 18 : 16
  return (
    <Link className={clsx(base, variants[variant], sizes[size], block && 'w-full', className)} {...rest}>
      {Icon && <Icon size={iconSize} aria-hidden />}
      {children}
    </Link>
  )
}

type IconButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  icon: LucideIcon
  label: string
  size?: number
  tone?: 'glass' | 'primary' | 'plain' | 'inverse' | 'danger'
  badge?: number
}

/** Round icon button (GlassIconButton). `label` is required for screen readers. */
export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(function IconButton(
  { icon: Icon, label, size = 44, tone = 'glass', badge, className, type = 'button', ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      aria-label={label}
      title={label}
      className={clsx(
        'press relative inline-flex shrink-0 items-center justify-center rounded-full disabled:opacity-40',
        tone === 'glass' && 'glass text-ink hover:bg-glass-strong',
        tone === 'primary' && 'bg-primary text-white shadow-[0_10px_24px_-10px_rgb(112_89_240/0.8)] hover:bg-primary-hover',
        tone === 'plain' && 'text-ink hover:bg-hover',
        tone === 'inverse' && 'text-on-inverse hover:bg-white/15 dark:hover:bg-black/10',
        tone === 'danger' && 'text-danger hover:bg-danger-soft',
        className,
      )}
      style={{ width: size, height: size }}
      {...rest}
    >
      <Icon size={Math.round(size * 0.44)} aria-hidden />
      {badge ? (
        <span className="absolute -right-0.5 -top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full border-2 border-surface bg-danger px-1 text-[10px] font-bold text-white dark:text-black">
          {badge > 9 ? '9+' : badge}
        </span>
      ) : null}
    </button>
  )
})
