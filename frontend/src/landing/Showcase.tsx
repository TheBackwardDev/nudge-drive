import { useEffect, useRef, useState, type CSSProperties } from 'react'
import { Check, Eye, FolderOpen, Images, Layers, Link2, SlidersHorizontal, type LucideIcon } from 'lucide-react'
import { Phone } from '@/landing/devices'
import { PHONE } from '@/landing/shots'

type Step = { key: string; icon: LucideIcon; color: string; kicker: string; title: string; body: string; points: string[]; img: string; alt: string }

const STEPS: Step[] = [
  {
    key: 'home', icon: Layers, color: '#7059f0', kicker: 'Storage pool',
    title: 'All your drives in one place',
    body: 'Connect several Google Drive accounts and S3-compatible buckets, and see their total, used and free space as one pool.',
    points: ['Space by type: photos, videos, docs', 'Upload, Nearby, new folder and backup one tap away'],
    img: PHONE.home, alt: 'Nudge Drive Home screen: a storage pool of two drives with 30.0 GB free, quick actions and recent files',
  },
  {
    key: 'files', icon: FolderOpen, color: '#2e64e0', kicker: 'Files',
    title: 'Every file, neatly organised',
    body: 'Folders with colours, search across every drive, and filters by type. Move, rename, download and share in a tap.',
    points: ['Trash with restore, so nothing disappears by accident', 'Save to Nudge Drive from any app’s Share menu'],
    img: PHONE.files, alt: 'Nudge Drive Files screen with folders, search and type filters',
  },
  {
    key: 'photos', icon: Images, color: '#1e9e57', kicker: 'Photos',
    title: 'Your photos on a timeline',
    body: 'Browse every picture by month, with each photo’s original date kept, wherever it is stored.',
    points: ['Filter to images, videos or PDFs', 'Grid that loads fast, even on big libraries'],
    img: PHONE.photos, alt: 'Nudge Drive photo timeline grouped by month',
  },
  {
    key: 'viewer', icon: Eye, color: '#f59e0b', kicker: 'Viewer',
    title: 'Open files right in the app',
    body: 'View photos, play videos, read PDFs, documents and spreadsheets without downloading them first.',
    points: ['Edit documents, or open them in Google Docs, Sheets and Slides', 'Swipe through a folder like an album'],
    img: PHONE.viewer, alt: 'Nudge Drive photo viewer showing a fjord landscape with a thumbnail strip',
  },
  {
    key: 'drives', icon: SlidersHorizontal, color: '#a23fcf', kicker: 'Upload rules',
    title: 'You decide where uploads go',
    body: 'Each upload goes to the drive with the most free space, takes turns, or follows the order you pick.',
    points: ['Every Google account adds its own 15 GB', 'Files already in a Drive are imported as soon as you connect it', 'An account is full? Free up space moves files to the others'],
    img: PHONE.drives, alt: 'Nudge Drive Drives screen: two connected accounts and the "Most free space" upload rule',
  },
  {
    key: 'share', icon: Link2, color: '#e8402b', kicker: 'Sharing',
    title: 'Share with a simple link',
    body: 'Create a share link in a tap and turn it off whenever you like, or invite other Nudge Drive users to a file or folder.',
    points: ['Public links open in any browser', 'See your recent activity'],
    img: PHONE.share, alt: 'Nudge Drive share sheet with a public link switch',
  },
]

/** Feature tour: copy scrolls on the left while a sticky phone crossfades to the matching real screen. */
export function Showcase() {
  const [active, setActive] = useState(0)
  const refs = useRef<(HTMLLIElement | null)[]>([])

  useEffect(() => {
    const io = new IntersectionObserver(
      (entries) => {
        for (const e of entries) if (e.isIntersecting) setActive(Number((e.target as HTMLElement).dataset.i))
      },
      { rootMargin: '-48% 0px -48% 0px' },
    )
    refs.current.forEach((el) => el && io.observe(el))
    return () => io.disconnect()
  }, [])

  const jump = (i: number) => refs.current[i]?.scrollIntoView({ block: 'center' })
  const tilt = active % 2 === 0 ? -9 : 9

  return (
    <div className="grid grid-cols-1 gap-10 lg:grid-cols-[minmax(0,1fr)_400px] lg:gap-20">
      <ol className="grid grid-cols-1 gap-16 lg:gap-0">
        {STEPS.map((s, i) => (
          <li key={s.key} ref={(el) => { refs.current[i] = el }} data-i={i} className={`ld-show-step ${active === i ? 'is-active' : ''}`}>
            <div className="ld-show-copy w-full"><div className="ld-reveal grid w-full grid-cols-1 items-center gap-8 md:grid-cols-[1fr_auto] lg:grid-cols-1">
              <div>
                <span className="soft-tile inline-flex h-12 w-12 items-center justify-center rounded-[16px]" style={{ '--k': s.color } as CSSProperties}><s.icon size={23} aria-hidden /></span>
                <p className="ld-kicker mt-5 text-[13px] font-bold uppercase tracking-[0.14em]" style={{ '--k': s.color } as CSSProperties}>{s.kicker}</p>
                <h3 className="mt-2 text-[30px] font-bold leading-[1.1] tracking-[-0.035em] text-ink sm:text-[38px]">{s.title}</h3>
                <p className="mt-4 max-w-lg text-[16.5px] leading-relaxed text-muted">{s.body}</p>
                <ul className="mt-5 grid max-w-lg grid-cols-1 gap-2.5">
                  {s.points.map((p) => (
                    <li key={p} className="flex items-start gap-2.5 text-[15px] text-ink">
                      <span className="mt-0.5 grid h-5 w-5 flex-none place-items-center rounded-full bg-primary/12 text-primary"><Check size={13} strokeWidth={3} aria-hidden /></span>
                      {p}
                    </li>
                  ))}
                </ul>
              </div>
              {/* Phones and tablets: each step carries its own screen. */}
              <div className="relative mx-auto lg:hidden" style={{ fontSize: '2.5px' }}>
                <div className="ld-show-glow" style={{ '--k': s.color } as CSSProperties} aria-hidden />
                <Phone depth={false} img={{ src: s.img, alt: s.alt }} />
              </div>
            </div></div>
          </li>
        ))}
      </ol>

      <div className="relative hidden lg:block">
        <div className="ld-show-sticky">
          <div className="relative mx-auto w-max" style={{ perspective: '1600px' }}>
            <div className="ld-show-glow" style={{ '--k': STEPS[active].color } as CSSProperties} aria-hidden />
            <div className="ld-show-phone" style={{ transform: `rotateY(${tilt}deg) rotateX(4deg)`, transformStyle: 'preserve-3d' }}>
              <Phone>
                {STEPS.map((s, i) => (
                  <img
                    key={s.key}
                    src={s.img}
                    alt={i === active ? s.alt : ''}
                    aria-hidden={i !== active}
                    width={720}
                    height={1600}
                    loading="lazy"
                    decoding="async"
                    draggable={false}
                    className={`ld-show-screen ${i === active ? 'is-active' : ''}`}
                  />
                ))}
              </Phone>
            </div>
          </div>
          <div className="ld-dots mt-8 flex items-center justify-center gap-2" role="group" aria-label="Feature screens">
            {STEPS.map((s, i) => (
              <button key={s.key} type="button" aria-label={`Show ${s.kicker}`} aria-current={i === active} onClick={() => jump(i)} />
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
