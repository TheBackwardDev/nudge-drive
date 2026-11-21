import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { flushSync } from 'react-dom'

export type ThemePref = 'light' | 'dark' | 'system'
const KEY = 'nudge:theme'

/** Where a theme switch was triggered from: a click event, an element, or a point. */
export type RevealOrigin = { clientX: number; clientY: number } | { currentTarget: EventTarget | null } | HTMLElement | null | undefined

type ThemeState = {
  pref: ThemePref
  dark: boolean
  setPref: (p: ThemePref, from?: RevealOrigin) => void
  toggle: (from?: RevealOrigin) => void
}
const ThemeContext = createContext<ThemeState | null>(null)

function readPref(): ThemePref {
  try {
    const v = localStorage.getItem(KEY)
    return v === 'light' || v === 'dark' || v === 'system' ? v : 'system'
  } catch {
    return 'system'
  }
}

const systemDark = () => window.matchMedia('(prefers-color-scheme: dark)').matches
const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches

/** Top of the screen backdrop, used for the browser / PWA title bar (LightNd / DarkNd background). */
const BAR_COLOR = { light: '#D9CEFF', dark: '#16121F' }

function applyDom(dark: boolean) {
  const root = document.documentElement
  root.classList.toggle('dark', dark)
  root.style.colorScheme = dark ? 'dark' : 'light'
  // The in-app choice wins over the OS setting, so both theme-color tags follow it.
  document.querySelectorAll<HTMLMetaElement>('meta[name="theme-color"]').forEach((m) => {
    m.content = dark ? BAR_COLOR.dark : BAR_COLOR.light
  })
}

function centerOf(from: RevealOrigin): { x: number; y: number } {
  const fallback = { x: window.innerWidth - 60, y: 40 }
  if (!from) return fallback
  if ('clientX' in from && (from.clientX || from.clientY)) return { x: from.clientX, y: from.clientY }
  const el = from instanceof HTMLElement ? from : 'currentTarget' in from && from.currentTarget instanceof HTMLElement ? from.currentTarget : null
  if (!el) return fallback
  const r = el.getBoundingClientRect()
  return { x: r.left + r.width / 2, y: r.top + r.height / 2 }
}

type VTDocument = Document & { startViewTransition?: (cb: () => void) => { ready: Promise<void>; finished: Promise<void> } }

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [pref, setPrefState] = useState<ThemePref>(readPref)
  const [sysDark, setSysDark] = useState(systemDark)
  const dark = pref === 'dark' || (pref === 'system' && sysDark)
  const running = useRef(false)

  useEffect(() => {
    const mq = window.matchMedia('(prefers-color-scheme: dark)')
    const on = () => setSysDark(mq.matches)
    mq.addEventListener('change', on)
    return () => mq.removeEventListener('change', on)
  }, [])

  useEffect(() => applyDom(dark), [dark])

  const setPref = useCallback((p: ThemePref, from?: RevealOrigin) => {
    try {
      localStorage.setItem(KEY, p)
    } catch {
      /* ignore */
    }
    const nextDark = p === 'dark' || (p === 'system' && systemDark())
    const changes = nextDark !== document.documentElement.classList.contains('dark')
    const doc = document as VTDocument
    if (!changes || reducedMotion() || running.current) {
      setPrefState(p)
      return
    }
    // ThemeReveal: freeze the old theme, apply the new one underneath, grow a circle from the button.
    if (doc.startViewTransition) {
      running.current = true
      const { x, y } = centerOf(from)
      const r = Math.hypot(Math.max(x, window.innerWidth - x), Math.max(y, window.innerHeight - y))
      try {
        const vt = doc.startViewTransition(() => {
          flushSync(() => setPrefState(p))
          applyDom(nextDark)
        })
        vt.ready
          .then(() => {
            document.documentElement.animate(
              { clipPath: [`circle(0px at ${x}px ${y}px)`, `circle(${r}px at ${x}px ${y}px)`] },
              { duration: 560, easing: 'cubic-bezier(0.4, 0, 0.2, 1)', pseudoElement: '::view-transition-new(root)' },
            )
          })
          .catch(() => undefined)
        vt.finished.finally(() => {
          running.current = false
        })
      } catch {
        running.current = false
        setPrefState(p)
      }
      return
    }
    // Fallback: a short colour cross-fade.
    const root = document.documentElement
    root.classList.add('theme-fade')
    setPrefState(p)
    window.setTimeout(() => root.classList.remove('theme-fade'), 380)
  }, [])

  const toggle = useCallback((from?: RevealOrigin) => setPref(dark ? 'light' : 'dark', from), [dark, setPref])

  return <ThemeContext.Provider value={{ pref, dark, setPref, toggle }}>{children}</ThemeContext.Provider>
}

export function useTheme() {
  const ctx = useContext(ThemeContext)
  if (!ctx) throw new Error('useTheme must be used inside ThemeProvider')
  return ctx
}
