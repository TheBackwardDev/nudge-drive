import { useEffect, useState } from 'react'
import { getStoredUser } from '@/lib/session'

/**
 * The in-app inbox (Android NoticeStore). Like the phone app, notices are kept on this device only:
 * upload results, storage alerts, finished imports and files friends share with you.
 */
export type NoticeType = 'upload' | 'uploadFailed' | 'download' | 'drive' | 'storage' | 'share' | 'invite' | 'info'
export type Notice = { id: string; type: NoticeType; title: string; body: string; at: number; read: boolean; to?: string; fileId?: string }

const MAX = 150
const EVENT = 'nd:notices'
const key = () => `nudge:notices:${getStoredUser()?.id ?? 'anon'}`

/** Settings (Settings > Notifications). */
export const PREF_BROWSER = 'nudge:notify-browser'
export const PREF_ALERTS = 'nudge:storage-alerts'

export function readNotices(): Notice[] {
  try {
    const raw = localStorage.getItem(key())
    const list = raw ? (JSON.parse(raw) as Notice[]) : []
    return Array.isArray(list) ? list : []
  } catch {
    return []
  }
}

function write(list: Notice[]) {
  try {
    localStorage.setItem(key(), JSON.stringify(list.slice(0, MAX)))
  } catch {
    /* storage full / unavailable */
  }
  window.dispatchEvent(new Event(EVENT))
}

const prefOn = (k: string, fallback: boolean) => {
  try {
    const v = localStorage.getItem(k)
    return v === null ? fallback : v === 'on'
  } catch {
    return fallback
  }
}
export const storageAlertsOn = () => prefOn(PREF_ALERTS, true)
export const browserNotificationsOn = () => prefOn(PREF_BROWSER, false)

/** Adds a notice; also shows a system notification when enabled and the tab is in the background. */
export function postNotice(n: Omit<Notice, 'id' | 'at' | 'read'>) {
  const notice: Notice = { ...n, id: `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 7)}`, at: Date.now(), read: false }
  write([notice, ...readNotices()])
  try {
    if (browserNotificationsOn() && 'Notification' in window && Notification.permission === 'granted' && document.visibilityState === 'hidden') {
      const sys = new Notification(n.title, { body: n.body, icon: '/pwa-192x192.png', tag: notice.id })
      sys.onclick = () => {
        window.focus()
        if (n.to) window.location.assign(n.to)
      }
    }
  } catch {
    /* some browsers only allow notifications from a service worker */
  }
}

export const markAllRead = () => write(readNotices().map((n) => ({ ...n, read: true })))
export const markRead = (id: string) => write(readNotices().map((n) => (n.id === id ? { ...n, read: true } : n)))
export const removeNotice = (id: string) => write(readNotices().filter((n) => n.id !== id))
export const clearNotices = () => write([])

export function useNotices() {
  const [list, setList] = useState(readNotices)
  useEffect(() => {
    const on = () => setList(readNotices())
    on()
    window.addEventListener(EVENT, on)
    window.addEventListener('storage', on)
    return () => {
      window.removeEventListener(EVENT, on)
      window.removeEventListener('storage', on)
    }
  }, [])
  return list
}

export const useUnreadCount = () => useNotices().filter((n) => !n.read).length

/** At most once a day per key (storage alerts). */
export function onceADay(id: string) {
  const k = `nudge:alerted:${getStoredUser()?.id ?? 'anon'}:${id}`
  try {
    const last = Number(localStorage.getItem(k) ?? 0)
    if (Date.now() - last < 24 * 3600 * 1000) return false
    localStorage.setItem(k, String(Date.now()))
    return true
  } catch {
    return false
  }
}

/** Invite ids already turned into a notice. */
export function unseenInvites(ids: string[]) {
  const k = `nudge:seen-invites:${getStoredUser()?.id ?? 'anon'}`
  try {
    const raw = localStorage.getItem(k)
    const first = raw === null
    const seen = new Set<string>(raw ? (JSON.parse(raw) as string[]) : [])
    const fresh = ids.filter((id) => !seen.has(id))
    localStorage.setItem(k, JSON.stringify(ids))
    // The very first check only records what is there, so old invites don't flood the inbox.
    return first ? [] : fresh
  } catch {
    return []
  }
}
