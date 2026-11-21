import { useEffect, useState } from 'react'
import { authedFetch } from '@/lib/api'

/**
 * Thumbnails need the bearer token, so they are fetched as blobs and shown through object URLs.
 * Results are cached for the session and requests run a few at a time, only once a tile is on screen.
 */
const cache = new Map<string, string | null>()
const pending = new Map<string, Promise<string | null>>()
const MAX_CACHE = 400
const PARALLEL = 6
let inFlight = 0
const waiting: (() => void)[] = []

async function slot<T>(fn: () => Promise<T>) {
  if (inFlight >= PARALLEL) await new Promise<void>((r) => waiting.push(r))
  inFlight++
  try {
    return await fn()
  } finally {
    inFlight--
    waiting.shift()?.()
  }
}

function remember(key: string, url: string | null) {
  cache.set(key, url)
  if (cache.size > MAX_CACHE) {
    const [oldKey, oldUrl] = cache.entries().next().value as [string, string | null]
    cache.delete(oldKey)
    if (oldUrl) URL.revokeObjectURL(oldUrl)
  }
}

export function loadThumb(fileId: string, size: number): Promise<string | null> {
  const key = `${fileId}@${size}`
  if (cache.has(key)) return Promise.resolve(cache.get(key) ?? null)
  let p = pending.get(key)
  if (!p) {
    p = slot(async () => {
      try {
        const res = await authedFetch(`/files/${encodeURIComponent(fileId)}/thumbnail?s=${size}`)
        const blob = await res.blob()
        return blob.type.startsWith('image/') ? URL.createObjectURL(blob) : null
      } catch {
        return null
      }
    }).then((url) => {
      remember(key, url)
      pending.delete(key)
      return url
    })
    pending.set(key, p)
  }
  return p
}

/** Object URL for a file's thumbnail once its element scrolls into view; null while loading / unavailable. */
export function useThumbnail(fileId: string | null, size: number) {
  const [el, ref] = useState<HTMLElement | null>(null)
  const key = fileId ? `${fileId}@${size}` : ''
  const [url, setUrl] = useState<string | null>(() => (key ? cache.get(key) ?? null : null))
  const [failed, setFailed] = useState(() => (key ? cache.has(key) && cache.get(key) === null : false))

  useEffect(() => {
    if (!fileId) return
    const hit = cache.get(key)
    if (hit !== undefined) {
      setUrl(hit)
      setFailed(hit === null)
      return
    }
    setUrl(null)
    setFailed(false)
    let alive = true
    const start = () =>
      void loadThumb(fileId, size).then((u) => {
        if (!alive) return
        setUrl(u)
        setFailed(u === null)
      })
    if (!el) return // wait for the element to mount
    if (!('IntersectionObserver' in window)) {
      start()
      return () => { alive = false }
    }
    const io = new IntersectionObserver((entries) => {
      if (entries.some((e) => e.isIntersecting)) {
        io.disconnect()
        start()
      }
    }, { rootMargin: '300px' })
    io.observe(el)
    return () => {
      alive = false
      io.disconnect()
    }
  }, [fileId, size, key, el])

  return { ref, url, failed }
}
