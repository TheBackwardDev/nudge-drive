import type { User } from '@/lib/types'

// Keys are unchanged from the previous web app so existing sessions survive the redesign.
const ACCESS_TOKEN_KEY = 'nudge.accessToken'
const REFRESH_TOKEN_KEY = 'nudge.refreshToken'
const USER_KEY = 'nudge.user'

function read(key: string) {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

function write(key: string, value: string | null) {
  try {
    if (value === null) localStorage.removeItem(key)
    else localStorage.setItem(key, value)
  } catch {
    /* storage unavailable (private mode); the session just won't persist */
  }
}

export const getAccessToken = () => read(ACCESS_TOKEN_KEY)
export const getRefreshToken = () => read(REFRESH_TOKEN_KEY)
export const setAccessToken = (token: string) => write(ACCESS_TOKEN_KEY, token)

export function getStoredUser(): User | null {
  const raw = read(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as User
  } catch {
    return null
  }
}

export function storeUser(user: User) {
  write(USER_KEY, JSON.stringify(user))
}

export function storeSession(accessToken: string, refreshToken: string, user: User) {
  write(ACCESS_TOKEN_KEY, accessToken)
  write(REFRESH_TOKEN_KEY, refreshToken)
  storeUser(user)
}

export function clearSession() {
  write(ACCESS_TOKEN_KEY, null)
  write(REFRESH_TOKEN_KEY, null)
  write(USER_KEY, null)
  window.dispatchEvent(new Event('nd:signed-out'))
}

/** A same-origin path to return to after signing in (never an absolute or protocol-relative URL). */
export function safeNext(next: string | null | undefined, fallback = '/home') {
  return next && next.startsWith('/') && !next.startsWith('//') ? next : fallback
}
