import type { CSSProperties, ReactNode } from 'react'
import clsx from 'clsx'

type ImgProps = { src: string; alt: string; eager?: boolean; high?: boolean }

function ScreenImg({ src, alt, eager, high }: ImgProps) {
  return (
    <img
      src={src}
      alt={alt}
      width={720}
      height={1600}
      loading={eager ? 'eager' : 'lazy'}
      decoding="async"
      fetchPriority={high ? 'high' : undefined}
      draggable={false}
    />
  )
}

/**
 * A modern Android phone (thin bezels, punch-hole camera, metal rim) in CSS. It is 100em wide,
 * so its `font-size` sets the size. `depth` adds four thin side walls so it has real thickness in 3D
 * (cheaper to composite than stacked full-size layers).
 */
export function Phone({ img, children, depth = true, className, style }: { img?: ImgProps; children?: ReactNode; depth?: boolean; className?: string; style?: CSSProperties }) {
  return (
    <div className={clsx('ld-phone', className)} style={style}>
      {depth && (
        <div className="ld-phone-depth" aria-hidden>
          <i className="ld-edge-l" /><i className="ld-edge-r" /><i className="ld-edge-t" /><i className="ld-edge-b" />
        </div>
      )}
      {depth && (
        <>
          <span className="ld-phone-btn" style={{ top: '38em', height: '13em' }} aria-hidden />
          <span className="ld-phone-btn" style={{ top: '58em', height: '24em' }} aria-hidden />
        </>
      )}
      <div className="ld-phone-body">
        <div className="ld-phone-glass">
          <div className="ld-phone-screen">
            {img ? <ScreenImg {...img} /> : children}
            <span className="ld-phone-cam" aria-hidden />
            <span className="ld-glare" aria-hidden />
          </div>
        </div>
      </div>
    </div>
  )
}

/**
 * A thin-bezel laptop in CSS 3D: the lid stands up and the keyboard deck lies flat towards the
 * viewer (only visible when the parent is rotated). 100em = lid width. `flat` swaps the deck for a
 * simple front-view stand.
 */
export function Laptop({ src, alt, eager, high, flat, className, style }: { src: string; alt: string; eager?: boolean; high?: boolean; flat?: boolean; className?: string; style?: CSSProperties }) {
  return (
    <div className={clsx(className)} style={style}>
      <div className={clsx('ld-laptop', flat && 'ld-laptop--flat')}>
        {!flat && (
          <div className="ld-laptop-base" aria-hidden>
            <div className="ld-laptop-keys" />
            <div className="ld-laptop-pad" />
            <div className="ld-laptop-lip" />
          </div>
        )}
        <div className="ld-laptop-lid">
          <span className="ld-laptop-cam" aria-hidden />
          <div className="ld-laptop-screen">
            <img
              src={src}
              alt={alt}
              width={1440}
              height={900}
              loading={eager ? 'eager' : 'lazy'}
              decoding="async"
              fetchPriority={high ? 'high' : undefined}
              draggable={false}
            />
          </div>
          <span className="ld-laptop-chin" aria-hidden />
        </div>
      </div>
      {flat && <div className="ld-laptop-stand" aria-hidden />}
    </div>
  )
}
