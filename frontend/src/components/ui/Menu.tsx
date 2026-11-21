import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties, type KeyboardEvent, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import clsx from 'clsx'
import type { LucideIcon } from 'lucide-react'
import { Dialog } from '@/components/ui/Dialog'
import { useMediaQuery } from '@/lib/hooks'

export type MenuItem =
  | { label: string; icon?: LucideIcon; onSelect: () => void; danger?: boolean; disabled?: boolean; hint?: string; checked?: boolean }
  | 'divider'

export type MenuAnchor = { x: number; y: number } | HTMLElement

type MenuProps = {
  open: boolean
  anchor: MenuAnchor | null
  onClose: () => void
  items: (MenuItem | false | null | undefined)[]
  /** Shown as the sheet title on phones (and to screen readers). */
  title: string
  header?: ReactNode
}

/**
 * Dropdown / context menu. A positioned popover with arrow-key navigation on wide screens, a bottom
 * sheet on phones (the app's options sheet).
 */
export function Menu({ open, anchor, onClose, items, title, header }: MenuProps) {
  const phone = useMediaQuery('(max-width: 639px)')
  const list = items.filter(Boolean) as MenuItem[]

  if (phone) {
    return (
      <Dialog open={open} onClose={onClose} title={title} size="sm">
        {header}
        <div role="menu" aria-label={title} className="-mx-2 grid grid-cols-1 gap-0.5">
          {list.map((item, i) =>
            item === 'divider' ? <div key={i} className="mx-3 my-1.5 h-px bg-line" /> : <Item key={i} item={item} onClose={onClose} large />,
          )}
        </div>
      </Dialog>
    )
  }
  if (!open || !anchor) return null
  return createPortal(<Popover anchor={anchor} onClose={onClose} list={list} title={title} header={header} />, document.body)
}

function Popover({ anchor, onClose, list, title, header }: { anchor: MenuAnchor; onClose: () => void; list: MenuItem[]; title: string; header?: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null)
  const [pos, setPos] = useState<{ left: number; top: number; origin: string } | null>(null)
  const returnFocus = useRef<Element | null>(document.activeElement)
  const closeRef = useRef(onClose)
  closeRef.current = onClose

  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    const { width, height } = el.getBoundingClientRect()
    let x: number
    let y: number
    let above = false
    let ax: number
    if (anchor instanceof HTMLElement) {
      const r = anchor.getBoundingClientRect()
      ax = r.left + r.width / 2
      x = r.right - width < 8 ? r.left : r.right - width
      y = r.bottom + 6
      if (y + height > window.innerHeight - 8) {
        y = r.top - height - 6
        above = true
      }
    } else {
      ax = anchor.x
      x = anchor.x
      y = anchor.y
      if (y + height > window.innerHeight - 8) above = true
    }
    const left = Math.max(8, Math.min(x, window.innerWidth - width - 8))
    const top = Math.max(8, Math.min(y, window.innerHeight - height - 8))
    // Grow out of the corner nearest the button / pointer.
    setPos({ left, top, origin: `${ax - left < width / 2 ? 'left' : 'right'} ${above ? 'bottom' : 'top'}` })
    el.querySelector<HTMLElement>('[role="menuitem"]:not(:disabled)')?.focus()
  }, [anchor])

  useEffect(() => {
    const focusBack = returnFocus.current
    const onDown = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) closeRef.current()
    }
    const onScroll = () => closeRef.current()
    window.addEventListener('mousedown', onDown, true)
    window.addEventListener('resize', onScroll)
    return () => {
      window.removeEventListener('mousedown', onDown, true)
      window.removeEventListener('resize', onScroll)
      if (focusBack instanceof HTMLElement && document.contains(focusBack)) focusBack.focus()
    }
  }, [])

  function onKey(e: KeyboardEvent<HTMLDivElement>) {
    const items = Array.from(ref.current?.querySelectorAll<HTMLElement>('[role="menuitem"]:not(:disabled)') ?? [])
    const idx = items.indexOf(document.activeElement as HTMLElement)
    if (e.key === 'Escape' || e.key === 'Tab') {
      e.preventDefault()
      onClose()
    } else if (e.key === 'ArrowDown') {
      e.preventDefault()
      items[(idx + 1) % items.length]?.focus()
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      items[(idx - 1 + items.length) % items.length]?.focus()
    } else if (e.key === 'Home') {
      e.preventDefault()
      items[0]?.focus()
    } else if (e.key === 'End') {
      e.preventDefault()
      items[items.length - 1]?.focus()
    }
  }

  return (
    <div
      ref={ref}
      role="menu"
      aria-label={title}
      onKeyDown={onKey}
      onContextMenu={(e) => e.preventDefault()}
      className={clsx('fixed z-[80] min-w-[220px] max-w-[300px] rounded-[20px] border border-card-border bg-surface p-1.5 shadow-lift', pos && 'menu-pop')}
      style={pos ? ({ left: pos.left, top: pos.top, '--origin': pos.origin } as CSSProperties) : { left: -9999, top: -9999 }}
    >
      {header}
      {list.map((item, i) => (item === 'divider' ? <div key={i} className="mx-2 my-1 h-px bg-line" /> : <Item key={i} item={item} onClose={onClose} />))}
    </div>
  )
}

function Item({ item, onClose, large }: { item: Exclude<MenuItem, 'divider'>; onClose: () => void; large?: boolean }) {
  const Icon = item.icon
  return (
    <button
      type="button"
      role={item.checked === undefined ? 'menuitem' : 'menuitemradio'}
      aria-checked={item.checked}
      disabled={item.disabled}
      onClick={() => {
        onClose()
        item.onSelect()
      }}
      className={clsx(
        'press press-row flex w-full items-center gap-3 rounded-[14px] px-3 text-left font-medium outline-none hover:bg-mist focus-visible:bg-mist disabled:opacity-40',
        large ? 'h-[52px] text-[15px]' : 'h-10 text-[14px]',
        item.danger ? 'text-danger' : 'text-ink',
        item.checked && 'text-link',
      )}
    >
      {Icon && <Icon size={large ? 20 : 17} aria-hidden className={item.danger ? '' : 'text-muted'} />}
      <span className="flex-1 truncate">{item.label}</span>
      {item.hint && <span className="text-[11.5px] text-subtle">{item.hint}</span>}
    </button>
  )
}

/** Helper: open a menu from a button click or a right-click. */
export function useMenu() {
  const [anchor, setAnchor] = useState<MenuAnchor | null>(null)
  return {
    anchor,
    open: anchor !== null,
    openAt: (a: MenuAnchor) => setAnchor(a),
    close: () => setAnchor(null),
  }
}
