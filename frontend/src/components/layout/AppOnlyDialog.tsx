import { ExternalLink, Radar, ShieldCheck, Smartphone } from 'lucide-react'
import { Dialog } from '@/components/ui/Dialog'
import { PLAY_URL } from '@/lib/links'

/** Phone-only features (auto backup, Nearby share) explained, with a link to the Android app. */
export function AppOnlyDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <Dialog open={open} onClose={onClose} title="Available in the Android app" size="sm">
      <div className="grid grid-cols-1 gap-3">
        {[
          { icon: ShieldCheck, title: 'Auto backup', body: 'Pick phone folders like Camera or WhatsApp Images. New photos upload on their own, on Wi-Fi.' },
          { icon: Radar, title: 'Nearby share', body: 'Send files to another phone with Nudge Drive, no internet needed.' },
        ].map((f) => (
          <div key={f.title} className="flex items-start gap-3 rounded-[18px] bg-mist/70 p-3">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[13px] bg-surface text-link"><f.icon size={19} aria-hidden /></span>
            <span>
              <span className="block text-[14px] font-semibold text-ink">{f.title}</span>
              <span className="block text-[13px] text-muted">{f.body}</span>
            </span>
          </div>
        ))}
        <p className="text-[13px] text-muted">Everything the phone backs up shows up here on the web straight away.</p>
        {PLAY_URL ? (
          <a href={PLAY_URL} target="_blank" rel="noreferrer" className="press mt-1 inline-flex h-12 items-center justify-center gap-2 rounded-[24px] bg-primary px-5 text-[15px] font-bold text-white shadow-[0_12px_28px_-12px_rgb(112_89_240/0.7)] hover:bg-primary-hover">
            <Smartphone size={18} aria-hidden />Get Nudge Drive for Android<ExternalLink size={15} className="opacity-70" aria-hidden />
          </a>
        ) : (
          <p className="mt-1 inline-flex h-12 items-center justify-center gap-2 rounded-[24px] bg-mist px-5 text-[15px] font-bold text-muted">
            <Smartphone size={18} aria-hidden />Nudge Drive for Android is coming soon to Google Play
          </p>
        )}
      </div>
    </Dialog>
  )
}
