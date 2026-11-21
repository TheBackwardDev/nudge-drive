import { useCallback, useEffect, useRef, useState } from 'react'
import { errorMessage } from '@/lib/api'

/** App-wide change notifications so every open screen reloads after an upload, move or delete. */
export type Topic = 'files' | 'folders' | 'storage' | 'accounts' | 'shares'

export function emitChange(...topics: Topic[]) {
  for (const t of topics) window.dispatchEvent(new Event(`nd:changed:${t}`))
}

export function useOnChange(topics: Topic[], handler: () => void) {
  const ref = useRef(handler)
  ref.current = handler
  const key = topics.join(',')
  useEffect(() => {
    let timer = 0
    // Coalesce bursts (20 finished uploads → one reload).
    const fire = () => {
      window.clearTimeout(timer)
      timer = window.setTimeout(() => ref.current(), 250)
    }
    const names = key.split(',').map((t) => `nd:changed:${t}`)
    names.forEach((n) => window.addEventListener(n, fire))
    return () => {
      window.clearTimeout(timer)
      names.forEach((n) => window.removeEventListener(n, fire))
    }
  }, [key])
}

/** Loads data on mount (and when `deps` change); `reload()` refreshes without blanking the screen. */
export function useLoad<T>(loader: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const loaderRef = useRef(loader)
  loaderRef.current = loader
  const seq = useRef(0)

  const reload = useCallback(async () => {
    const id = ++seq.current
    try {
      const value = await loaderRef.current()
      if (id === seq.current) {
        setData(value)
        setError('')
      }
    } catch (e) {
      if (id === seq.current) setError(errorMessage(e))
    } finally {
      if (id === seq.current) setLoading(false)
    }
  }, [])

  useEffect(() => {
    setLoading(true)
    void reload()
  }, deps) // the caller lists what the loader depends on

  return { data, setData, error, loading, reload }
}

export function useMediaQuery(query: string) {
  const [matches, setMatches] = useState(() => window.matchMedia(query).matches)
  useEffect(() => {
    const mq = window.matchMedia(query)
    const on = () => setMatches(mq.matches)
    on()
    mq.addEventListener('change', on)
    return () => mq.removeEventListener('change', on)
  }, [query])
  return matches
}

export const useIsDesktop = () => useMediaQuery('(min-width: 1024px)')

/** A small persisted preference (per browser). */
export function usePref<T extends string>(key: string, fallback: T, allowed: readonly T[]) {
  const [value, setValue] = useState<T>(() => {
    try {
      const v = localStorage.getItem(key) as T | null
      return v && allowed.includes(v) ? v : fallback
    } catch {
      return fallback
    }
  })
  const set = useCallback((v: T) => {
    setValue(v)
    try {
      localStorage.setItem(key, v)
    } catch {
      /* ignore */
    }
  }, [key])
  return [value, set] as const
}

export function useDebounced<T>(value: T, ms = 300) {
  const [v, setV] = useState(value)
  useEffect(() => {
    const t = window.setTimeout(() => setV(value), ms)
    return () => window.clearTimeout(t)
  }, [value, ms])
  return v
}

/** Seconds left on a resend cooldown. */
export function useCountdown() {
  const [left, setLeft] = useState(0)
  useEffect(() => {
    if (left <= 0) return
    const t = window.setTimeout(() => setLeft((s) => s - 1), 1000)
    return () => window.clearTimeout(t)
  }, [left])
  return [left, setLeft] as const
}

export function useDocumentTitle(title: string) {
  useEffect(() => {
    document.title = title ? `${title} · Nudge Drive` : 'Nudge Drive'
  }, [title])
}

export async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    // Fallback for non-secure contexts / older browsers.
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    const ok = document.execCommand('copy')
    ta.remove()
    return ok
  }
}
