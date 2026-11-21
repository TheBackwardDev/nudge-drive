import { FileText, Mail, ShieldCheck, Smartphone } from 'lucide-react'
import { LogoTile } from '@/components/brand/Brand'
import { ListRow, Panel } from '@/components/ui/Surface'
import { PageHeader } from '@/components/layout/PageHeader'
import { api } from '@/lib/api'
import { formatBytes } from '@/lib/format'
import { useLoad } from '@/lib/hooks'
import { PLAY_URL, SUPPORT_EMAIL } from '@/lib/links'


/** About Nudge Drive (AboutScreen): version, server, pool numbers and the legal / support links. */
export default function AboutPage() {
  const { data } = useLoad(() => Promise.all([api.storage.summary(), api.accounts.list()]))
  const [summary, accounts] = data ?? [null, null]
  const rows: [string, string][] = [
    ['Server', window.location.origin],
    ['Connected drives', accounts ? String(accounts.accounts.length) : '…'],
    ['Pool size', summary ? formatBytes(summary.totalBytes) : '…'],
    ['Free space', summary ? formatBytes(summary.availableBytes) : '…'],
  ]
  return (
    <div className="mx-auto grid max-w-2xl grid-cols-1 gap-5">
      <PageHeader title="About" back="/profile" className="!pb-0" />
      <div className="flex flex-col items-center py-3 text-center">
        <span className="pop-in"><LogoTile size={96} className="drop-shadow-[0_18px_30px_rgb(112_89_240/0.35)]" /></span>
        <h2 className="mt-4 text-[24px] font-bold text-ink">Nudge Drive</h2>
        <p className="mt-0.5 text-[13.5px] text-muted">Version {__APP_VERSION__} · web</p>
      </div>

      <Panel className="divide-y divide-line px-4 py-1">
        {rows.map(([k, v]) => (
          <div key={k} className="flex items-center justify-between gap-4 py-3">
            <span className="text-[14px] text-muted">{k}</span>
            <span className="truncate text-[14px] font-semibold text-ink">{v}</span>
          </div>
        ))}
      </Panel>

      <Panel className="p-2">
        <ListRow icon={ShieldCheck} title="Privacy policy" to="/privacy" />
        <ListRow icon={FileText} title="Terms of service" to="/terms" />
        <ListRow icon={Mail} title="Contact support" subtitle={SUPPORT_EMAIL} href={`mailto:${SUPPORT_EMAIL}?subject=Nudge%20Drive%20help`} />
        {PLAY_URL
          ? <ListRow icon={Smartphone} title="Nudge Drive for Android" subtitle="Auto backup, Nearby share and more" href={PLAY_URL} />
          : <ListRow icon={Smartphone} title="Nudge Drive for Android" subtitle="Coming soon to Google Play" />}
      </Panel>

      <p className="px-4 pb-4 text-center text-[12.5px] leading-relaxed text-subtle">
        Your files stream straight between this browser and your own Google Drive / S3 accounts through your private server.
      </p>
    </div>
  )
}
