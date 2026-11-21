import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import clsx from 'clsx'
import { CircleAlert, CircleCheck, X } from 'lucide-react'
import { LogoTile } from '@/components/brand/Brand'

type Toast = { id: number; message: string; tone: 'info' | 'error' | 'success'; action?: { label: string; onClick: () => void }; leaving?: boolean }
type ToastOptions = { tone?: Toast['tone']; action?: Toast['action']; duration?: number }
type ToastApi = { show: (message: string, options?: ToastOptions) => void; error: (message: string) => void }

const ToastContext = createContext<ToastApi | null>(null)

/** Glass toast that drops in from the top with the app tile (ToastHost in the Android app). */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const seq = useRef(0)
  const region = useRef<HTMLDivElement>(null)

  // Modal dialogs live in the browser's top layer; re-show the toast region as a popover so it sits above them.
  useEffect(() => {
    const el = region.current as (HTMLDivElement & { showPopover?: () => void; hidePopover?: () => void }) | null
    if (!el?.showPopover) return
    try {
      if (el.matches(':popover-open')) el.hidePopover?.()
      if (toasts.length) el.showPopover()
    } catch {
      /* popover unsupported: the region stays a normal fixed element */
    }
  }, [toasts.length])

  const dismiss = useCallback((id: number) => {
    setToasts((t) => t.map((x) => (x.id === id ? { ...x, leaving: true } : x)))
    window.setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 260)
  }, [])

  const show = useCallback((message: string, options: ToastOptions = {}) => {
    const id = ++seq.current
    setToasts((t) => [...t.filter((x) => !x.leaving).slice(-2), { id, message, tone: options.tone ?? 'info', action: options.action }])
    window.setTimeout(() => dismiss(id), options.duration ?? (options.action ? 5500 : 3200))
  }, [dismiss])

  const error = useCallback((message: string) => show(message, { tone: 'error' }), [show])
  const value = useMemo(() => ({ show, error }), [show, error])

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        ref={region}
        {...{ popover: 'manual' }}
        aria-live="polite"
        className="pointer-events-none fixed inset-x-0 bottom-auto top-[max(10px,env(safe-area-inset-top))] z-[90] m-0 flex w-auto flex-col items-center gap-2 overflow-visible border-0 bg-transparent p-0 px-4 lg:top-4"
      >
        {toasts.map((t) => (
          <div
            key={t.id}
            role={t.tone === 'error' ? 'alert' : 'status'}
            className={clsx(
              'glass-strong pointer-events-auto flex max-w-[400px] items-center gap-2.5 rounded-[24px] py-2 pl-2 pr-2 shadow-lift',
              t.leaving ? 'toast-out' : 'toast-in',
            )}
          >
            {t.tone === 'error' ? (
              <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-[10px] bg-danger-soft text-danger"><CircleAlert size={16} aria-hidden /></span>
            ) : (
              <span className="relative shrink-0">
                <LogoTile size={28} />
                {t.tone === 'success' && <CircleCheck size={14} className="pop-in absolute -bottom-1 -right-1 rounded-full bg-surface text-success" aria-hidden />}
              </span>
            )}
            <span className="min-w-0 flex-1 pl-0.5 text-[13.5px] font-semibold text-ink">{t.message}</span>
            {t.action && (
              <button
                type="button"
                onClick={() => {
                  dismiss(t.id)
                  t.action?.onClick()
                }}
                className="press rounded-[14px] bg-inverse px-3 py-1.5 text-[13.5px] font-bold text-on-inverse"
              >
                {t.action.label}
              </button>
            )}
            <button type="button" onClick={() => dismiss(t.id)} aria-label="Dismiss" className="press flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-subtle hover:bg-hover hover:text-ink">
              <X size={16} aria-hidden />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast() {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be used inside ToastProvider')
  return ctx
}
