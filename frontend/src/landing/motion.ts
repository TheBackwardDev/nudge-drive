import { useEffect, useLayoutEffect, type RefObject } from 'react'

export const prefersReducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches

/** Few cores or little memory: keep the scene still instead of animating it every frame. */
export function isLowEnd() {
  const cores = navigator.hardwareConcurrency || 8
  const memory = (navigator as Navigator & { deviceMemory?: number }).deviceMemory ?? 8
  return cores <= 2 || memory <= 2
}

export const clamp = (v: number, lo = 0, hi = 1) => Math.min(hi, Math.max(lo, v))

/**
 * Fades/slides `.ld-reveal` elements in as they enter the viewport. The `ld-js` class is added
 * before paint, so without this hook (or without JS) everything is simply visible.
 */
export function useReveal(root: RefObject<HTMLElement | null>) {
  useLayoutEffect(() => {
    const el = root.current
    if (!el) return
    el.classList.add('ld-js')
    if (prefersReducedMotion() || isLowEnd()) el.classList.add('ld-still')
    const items = el.querySelectorAll<HTMLElement>('.ld-reveal')
    if (!('IntersectionObserver' in window)) {
      items.forEach((i) => i.classList.add('is-in'))
      return
    }
    const io = new IntersectionObserver(
      (entries) => {
        for (const e of entries) {
          if (e.isIntersecting) {
            e.target.classList.add('is-in')
            io.unobserve(e.target)
          }
        }
      },
      { rootMargin: '0px 0px -8% 0px', threshold: 0.12 },
    )
    items.forEach((i) => io.observe(i))
    return () => io.disconnect()
  }, [root])
}

/**
 * Calls `fn(progress)` on scroll (once per frame) while the element is on screen. Progress runs
 * 0 → 1 as the element's top travels from the bottom of the viewport to `end` (fraction of the
 * viewport height from the top).
 */
export function useScrollProgress(ref: RefObject<HTMLElement | null>, fn: (p: number) => void, end = 0.25) {
  useEffect(() => {
    const el = ref.current
    if (!el) return
    if (prefersReducedMotion()) {
      fn(1)
      return
    }
    let raf = 0
    let visible = true
    const update = () => {
      raf = 0
      const r = el.getBoundingClientRect()
      const vh = window.innerHeight
      fn(clamp((vh - r.top) / (vh - vh * end)))
    }
    const onScroll = () => {
      if (visible && !raf) raf = requestAnimationFrame(update)
    }
    const io = new IntersectionObserver(([e]) => {
      visible = e.isIntersecting
      if (visible) onScroll()
    })
    io.observe(el)
    update()
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll)
    return () => {
      io.disconnect()
      cancelAnimationFrame(raf)
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onScroll)
    }
    // fn is expected to be stable (module-level or ref-backed).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ref, end])
}
