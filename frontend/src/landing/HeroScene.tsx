import { useEffect, useRef } from 'react'
import { CircleCheck, HardDrive, Zap } from 'lucide-react'
import { Laptop, Phone } from '@/landing/devices'
import { clamp, isLowEnd, prefersReducedMotion } from '@/landing/motion'
import { DESK, PHONE } from '@/landing/shots'

/** Resting pose of the scene (matches .ld-rig in landing.css so there is no jump when JS starts). */
const BASE_RX = -10
const BASE_RY = -12

/**
 * The hero: a laptop and a phone showing the real app, floating in a CSS 3D scene. The scene tilts
 * towards the mouse, leans back as the page scrolls and bobs gently on its own. Everything is plain
 * DOM + CSS transforms (GPU composited), so it costs no extra JavaScript library. With reduced
 * motion or on a low-end device it holds a still pose.
 */
export function HeroScene({ dark }: { dark: boolean }) {
  const wrapRef = useRef<HTMLDivElement>(null)
  const rigRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const wrap = wrapRef.current
    const rig = rigRef.current
    if (!wrap || !rig || prefersReducedMotion() || isLowEnd()) return

    let tx = 0, ty = 0, ts = 0 // targets: pointer x/y in -1..1, scroll 0..1
    let x = 0, y = 0, s = 0 // eased values
    let raf = 0
    let onScreen = true

    const apply = () => {
      rig.style.transform =
        `translate3d(0, ${(-s * 5).toFixed(3)}em, ${(-s * 18).toFixed(3)}em) ` +
        `rotateX(${(BASE_RX + y * -6 + s * 16).toFixed(3)}deg) rotateY(${(BASE_RY + x * 11).toFixed(3)}deg)`
    }
    const tick = () => {
      x += (tx - x) * 0.07
      y += (ty - y) * 0.07
      s += (ts - s) * 0.14
      apply()
      raf = Math.abs(tx - x) + Math.abs(ty - y) + Math.abs(ts - s) > 0.0005 ? requestAnimationFrame(tick) : 0
    }
    const kick = () => {
      if (onScreen && !raf) raf = requestAnimationFrame(tick)
    }
    const onPointer = (e: PointerEvent) => {
      if (e.pointerType !== 'mouse') return
      tx = clamp((e.clientX / window.innerWidth) * 2 - 1, -1, 1)
      ty = clamp((e.clientY / window.innerHeight) * 2 - 1, -1, 1)
      kick()
    }
    const onScroll = () => {
      const r = wrap.getBoundingClientRect()
      ts = clamp((window.innerHeight * 0.55 - r.top) / (r.height + window.innerHeight * 0.3))
      kick()
    }
    const io = new IntersectionObserver(([e]) => {
      onScreen = e.isIntersecting
      wrap.classList.toggle('ld-paused', !onScreen) // no idle float while scrolled away
      if (onScreen) kick()
    })
    io.observe(wrap)
    onScroll()
    window.addEventListener('pointermove', onPointer, { passive: true })
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => {
      io.disconnect()
      cancelAnimationFrame(raf)
      window.removeEventListener('pointermove', onPointer)
      window.removeEventListener('scroll', onScroll)
    }
  }, [])

  const desk = dark ? DESK.homeDark : DESK.homeLight
  return (
    <div ref={wrapRef} className="ld-stage-wrap" role="img" aria-label="Nudge Drive on a laptop and an Android phone, both showing the Home screen with a 30 GB storage pool made from two drives">
      <div className="ld-stage" aria-hidden>
        <div className="ld-enter">
          <div ref={rigRef} className="ld-rig">
            <div className="ld-obj ld-hero-laptop">
              <div className="ld-float ld-float--slow">
                <Laptop src={desk} alt="" eager high className="ld-hero-laptop-dev" />
              </div>
            </div>

            <div className="ld-obj ld-hero-phone">
              <div className="ld-float">
                <Phone img={{ src: PHONE.home, alt: '', eager: true }} />
              </div>
            </div>

            <div className="ld-obj ld-hero-chip ld-hero-chip--pool">
              <div className="ld-float ld-float--fast">
                <div className="ld-chip3d">
                  <span className="ld-chip3d-icon" style={{ background: 'rgb(112 89 240 / 0.14)', color: '#7059f0' }}><HardDrive size="2.2em" /></span>
                  <span>
                    <span className="ld-chip3d-k block">Storage pool · 2 drives</span>
                    <span className="ld-chip3d-v block">30.0 GB free</span>
                    <span className="ld-chip3d-bar block"><span /></span>
                  </span>
                </div>
              </div>
            </div>

            <div className="ld-obj ld-hero-chip ld-hero-chip--nearby">
              <div className="ld-float">
                <div className="ld-chip3d">
                  <span className="ld-chip3d-icon" style={{ background: 'rgb(30 158 87 / 0.15)', color: '#1e9e57' }}><CircleCheck size="2.2em" /></span>
                  <span>
                    <span className="ld-chip3d-k block">Nearby share</span>
                    <span className="ld-chip3d-v block">Received 3 files</span>
                  </span>
                </div>
              </div>
            </div>

            <div className="ld-obj ld-hero-chip ld-hero-chip--rule">
              <div className="ld-float ld-float--slow">
                <div className="ld-chip3d">
                  <span className="ld-chip3d-icon" style={{ background: 'rgb(245 158 11 / 0.16)', color: '#d48806' }}><Zap size="2.2em" /></span>
                  <span>
                    <span className="ld-chip3d-k block">New uploads go to</span>
                    <span className="ld-chip3d-v block">Most free space</span>
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
