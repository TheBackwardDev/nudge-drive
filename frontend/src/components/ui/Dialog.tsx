import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import clsx from 'clsx'
import { X } from 'lucide-react'
import { Button } from '@/components/ui/Button'

type DialogProps = {
  open: boolean
  onClose: () => void
  title: ReactNode
  description?: ReactNode
  children?: ReactNode
  footer?: ReactNode
  size?: 'sm' | 'md' | 'lg'
  /** `drawer` slides in from the right on wide screens (details panel); both become bottom sheets on phones. */
  variant?: 'center' | 'drawer'
  /** Hide the visible title (still announced to screen readers). */
  hideTitle?: boolean
  className?: string
}

/**
 * Accessible modal built on the native <dialog>: focus is trapped, Esc closes, the page behind is inert.
 * Centred card on wide screens, bottom sheet on phones (NudgeSheet).
 */
export function Dialog({ open, onClose, title, description, children, footer, size = 'md', variant = 'center', hideTitle, className }: DialogProps) {
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const descId = useId()

  // Keep the content mounted while the sheet slides away / the card scales out.
  const [mounted, setMounted] = useState(open)
  const [closing, setClosing] = useState(false)

  useEffect(() => {
    const el = ref.current
    if (!el) return
    if (open) {
      setMounted(true)
      setClosing(false)
      if (!el.open) {
        // React's autoFocus runs before showModal(), which would move focus to the close button.
        const wanted = document.activeElement
        el.showModal()
        if (wanted instanceof HTMLElement && wanted !== document.body && el.contains(wanted)) wanted.focus()
      }
      return
    }
    if (!el.open) return
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      el.close()
      setMounted(false)
      return
    }
    setClosing(true)
    const t = window.setTimeout(() => {
      el.close()
      setClosing(false)
      setMounted(false)
    }, 230)
    return () => window.clearTimeout(t)
  }, [open])

  const width = size === 'sm' ? 'sm:w-[400px]' : size === 'lg' ? 'sm:w-[720px]' : 'sm:w-[520px]'

  return (
    <dialog
      ref={ref}
      data-variant={variant}
      data-closing={closing || undefined}
      aria-labelledby={titleId}
      aria-describedby={description ? descId : undefined}
      onCancel={(e) => {
        e.preventDefault()
        onClose()
      }}
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
      className={clsx(
        'nd-dialog m-auto max-sm:mx-0 max-sm:mb-0 max-sm:mt-auto max-sm:w-full',
        variant === 'drawer' ? 'sm:my-0 sm:ml-auto sm:mr-0 sm:h-full sm:w-[440px]' : width,
      )}
    >
      {(open || mounted) && (
        <div
          className={clsx(
            'dlg relative flex max-h-[92dvh] flex-col bg-sheet text-ink shadow-lift max-sm:rounded-t-[30px]',
            variant === 'drawer' ? 'sm:h-dvh sm:max-h-none sm:rounded-l-[30px]' : 'sm:max-h-[86dvh] sm:rounded-[30px] sm:border sm:border-card-border',
            className,
          )}
        >
          <div className="mx-auto mt-2.5 h-1.5 w-11 shrink-0 rounded-full bg-lilac sm:hidden" aria-hidden />
          <div className={hideTitle ? 'absolute right-4 top-4 z-10 max-sm:top-6' : 'flex items-start gap-3 px-6 pt-5'}>
            <div className={hideTitle ? 'sr-only' : 'min-w-0 flex-1'}>
              <h2 id={titleId} className="text-[20px] font-semibold leading-tight text-ink">{title}</h2>
              {description && <p id={descId} className="mt-1 text-[13.5px] text-muted">{description}</p>}
            </div>
            <button
              type="button"
              onClick={onClose}
              aria-label="Close"
              className={clsx(
                'press flex h-10 w-10 shrink-0 items-center justify-center rounded-full',
                hideTitle ? 'bg-surface/85 text-ink shadow-soft backdrop-blur hover:bg-surface' : '-mr-2 -mt-1 text-muted hover:bg-hover hover:text-ink',
              )}
            >
              <X size={20} aria-hidden />
            </button>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto px-6 pb-6 pt-4">{children}</div>
          {footer && <div className="flex flex-wrap justify-end gap-2.5 border-t border-line px-6 py-4 max-sm:pb-[max(1rem,env(safe-area-inset-bottom))]">{footer}</div>}
        </div>
      )}
    </dialog>
  )
}

type ConfirmProps = {
  open: boolean
  onClose: () => void
  title: string
  body?: ReactNode
  confirmLabel: string
  danger?: boolean
  onConfirm: () => Promise<unknown> | void
}

export function ConfirmDialog({ open, onClose, title, body, confirmLabel, danger, onConfirm }: ConfirmProps) {
  const [busy, setBusy] = useState(false)
  async function confirm() {
    setBusy(true)
    try {
      await onConfirm()
      onClose()
    } catch {
      /* the caller reports the error; keep the dialog open so the user can retry */
    } finally {
      setBusy(false)
    }
  }
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={title}
      size="sm"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={busy}>Cancel</Button>
          <Button variant={danger ? 'danger' : 'primary'} onClick={confirm} loading={busy} autoFocus>{confirmLabel}</Button>
        </>
      }
    >
      {body && <div className="text-[14px] text-muted">{body}</div>}
    </Dialog>
  )
}
