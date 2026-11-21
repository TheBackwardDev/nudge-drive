export function toNumber(value: string | number | null | undefined) {
  const n = Number(value ?? 0)
  return Number.isFinite(n) ? n : 0
}

/** "4.2 GB" style sizes (same rules as the app's formatBytes). `null` means no limit. */
export function formatBytes(value: string | number | null | undefined, unlimited = 'Unlimited') {
  if (value === null) return unlimited
  const bytes = toNumber(value)
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  const exp = Math.min(4, Math.max(1, Math.floor(Math.log(bytes) / Math.log(1024))))
  const v = bytes / 1024 ** exp
  return `${v >= 100 ? v.toFixed(0) : v.toFixed(1)} ${units[exp - 1]}`
}

const dateFmt = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
const timeFmt = new Intl.DateTimeFormat('en-US', { hour: 'numeric', minute: '2-digit' })
const monthFmt = new Intl.DateTimeFormat('en-US', { month: 'long', year: 'numeric' })

export function parseDate(iso: string | null | undefined) {
  if (!iso) return null
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? null : d
}

/** "Just now", "5 min ago", "Yesterday", "3 days ago", else "4 Nov 2026". */
export function friendlyDate(iso: string | null | undefined) {
  const d = parseDate(iso)
  if (!d) return ''
  const mins = Math.floor((Date.now() - d.getTime()) / 60_000)
  if (mins < 1) return 'Just now'
  if (mins < 60) return `${mins} min ago`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours} h ago`
  const days = Math.floor(hours / 24)
  if (days < 2) return 'Yesterday'
  if (days < 7) return `${days} days ago`
  return dateFmt.format(d)
}

export function fullDate(iso: string | null | undefined) {
  const d = parseDate(iso)
  return d ? `${dateFmt.format(d)} · ${timeFmt.format(d)}` : '—'
}

export const shortDate = (iso: string | null | undefined) => {
  const d = parseDate(iso)
  return d ? dateFmt.format(d) : '—'
}

export const timeOnly = (iso: string | null | undefined) => {
  const d = parseDate(iso)
  return d ? timeFmt.format(d) : ''
}

export const monthLabel = (d: Date) => monthFmt.format(d)

/** "Today" / "Yesterday" / "12 Nov 2026" heading for grouping lists by day. */
export function dayLabel(iso: string) {
  const d = parseDate(iso)
  if (!d) return 'Earlier'
  const start = new Date()
  start.setHours(0, 0, 0, 0)
  const diff = (start.getTime() - new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()) / 86_400_000
  if (diff <= 0) return 'Today'
  if (diff <= 1) return 'Yesterday'
  return dateFmt.format(d)
}

export const plural = (n: number, word: string, many = `${word}s`) => `${n} ${n === 1 ? word : many}`

export function greeting() {
  const h = new Date().getHours()
  if (h >= 5 && h <= 11) return 'Good morning'
  if (h >= 12 && h <= 16) return 'Good afternoon'
  if (h >= 17 && h <= 21) return 'Good evening'
  return 'Hello'
}
