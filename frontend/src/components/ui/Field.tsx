import { forwardRef, useId, useState, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from 'react'
import clsx from 'clsx'
import { ChevronDown, Eye, EyeOff, type LucideIcon } from 'lucide-react'

type FieldProps = InputHTMLAttributes<HTMLInputElement> & {
  label?: string
  /** Show the label to sighted users too (default: screen readers only, the placeholder explains). */
  showLabel?: boolean
  icon?: LucideIcon
  trailing?: ReactNode
  error?: string
  hint?: string
  glass?: boolean
}

const shell = 'flex h-[54px] w-full items-center rounded-[28px] border transition-colors focus-within:border-accent focus-within:ring-2 focus-within:ring-accent/25'

/** Pill-shaped input in the Lavender style (PillField). */
export const TextField = forwardRef<HTMLInputElement, FieldProps>(function TextField(
  { label, showLabel, icon: Icon, trailing, error, hint, glass, className, id, ...rest },
  ref,
) {
  const autoId = useId()
  const inputId = id ?? autoId
  const msgId = `${inputId}-msg`
  return (
    <div className={clsx('grid grid-cols-1 gap-1.5', className)}>
      {label && <label htmlFor={inputId} className={showLabel ? 'pl-4 text-[13px] font-semibold text-muted' : 'sr-only'}>{label}</label>}
      <div className={clsx(shell, glass ? 'border-glass-border bg-field' : 'border-line bg-field-solid', error && 'border-danger', Icon ? 'pl-4' : 'pl-5', trailing ? 'pr-1.5' : 'pr-5')}>
        {Icon && <Icon size={19} className="mr-2.5 shrink-0 text-muted" aria-hidden />}
        <input
          ref={ref}
          id={inputId}
          aria-invalid={error ? true : undefined}
          aria-describedby={error || hint ? msgId : undefined}
          className="h-full min-w-0 flex-1 bg-transparent text-[15px] font-medium text-ink outline-none placeholder:font-normal placeholder:text-subtle"
          {...rest}
        />
        {trailing}
      </div>
      {(error || hint) && <p id={msgId} className={clsx('pl-4 text-[12.5px]', error ? 'text-danger' : 'text-subtle')}>{error || hint}</p>}
    </div>
  )
})

export const PasswordField = forwardRef<HTMLInputElement, Omit<FieldProps, 'type' | 'trailing'>>(function PasswordField(props, ref) {
  const [show, setShow] = useState(false)
  return (
    <TextField
      ref={ref}
      type={show ? 'text' : 'password'}
      trailing={
        <button
          type="button"
          onClick={() => setShow((s) => !s)}
          aria-label={show ? 'Hide password' : 'Show password'}
          aria-pressed={show}
          className="press flex h-10 w-10 items-center justify-center rounded-full text-muted hover:bg-hover hover:text-ink"
        >
          {show ? <EyeOff size={18} aria-hidden /> : <Eye size={18} aria-hidden />}
        </button>
      }
      {...props}
    />
  )
})

type SelectProps = SelectHTMLAttributes<HTMLSelectElement> & { label: string; showLabel?: boolean; hint?: string }

export function Select({ label, showLabel = true, hint, className, id, children, ...rest }: SelectProps) {
  const autoId = useId()
  const selectId = id ?? autoId
  return (
    <div className={clsx('grid grid-cols-1 gap-1.5', className)}>
      <label htmlFor={selectId} className={showLabel ? 'pl-4 text-[13px] font-semibold text-muted' : 'sr-only'}>{label}</label>
      <div className="relative">
        <select
          id={selectId}
          className="h-[50px] w-full appearance-none rounded-[25px] border border-line bg-field-solid pl-5 pr-11 text-[15px] font-medium text-ink outline-none focus:border-accent focus:ring-2 focus:ring-accent/25"
          {...rest}
        >
          {children}
        </select>
        <ChevronDown size={18} className="pointer-events-none absolute right-4 top-1/2 -translate-y-1/2 text-muted" aria-hidden />
      </div>
      {hint && <p className="pl-4 text-[12.5px] text-subtle">{hint}</p>}
    </div>
  )
}

/** Six-digit code entry (email verification, account deletion). */
export function CodeField({ value, onChange, label = 'Six-digit code', autoFocus }: { value: string; onChange: (v: string) => void; label?: string; autoFocus?: boolean }) {
  const id = useId()
  return (
    <div className="grid grid-cols-1 gap-1.5">
      <label htmlFor={id} className="sr-only">{label}</label>
      <input
        id={id}
        value={value}
        onChange={(e) => onChange(e.target.value.replace(/\D/g, '').slice(0, 6))}
        inputMode="numeric"
        autoComplete="one-time-code"
        pattern="[0-9]{6}"
        maxLength={6}
        autoFocus={autoFocus}
        placeholder="000000"
        className="h-16 w-full rounded-[24px] border border-line bg-field-solid text-center font-display text-[28px] font-bold tracking-[0.5em] text-ink outline-none placeholder:text-lilac focus:border-accent focus:ring-2 focus:ring-accent/25"
      />
    </div>
  )
}

export function Checkbox({ checked, onChange, label, className }: { checked: boolean; onChange: (v: boolean) => void; label: ReactNode; className?: string }) {
  return (
    <label className={clsx('flex cursor-pointer items-center gap-3 text-[14px] font-medium text-ink', className)}>
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} className="h-5 w-5 accent-[#7059f0]" />
      {label}
    </label>
  )
}
