import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { api } from '@/lib/api'
import { clearSession, getAccessToken, getStoredUser, storeSession, storeUser } from '@/lib/session'
import type { AuthResponse, User } from '@/lib/types'

type AuthState = {
  user: User | null
  signedIn: boolean
  /** Saves tokens from login / register / Google, then loads the full profile. */
  startSession: (res: AuthResponse) => Promise<User>
  refreshUser: () => Promise<User | null>
  markVerified: () => void
  signOut: () => Promise<void>
  /** Drops the local session without calling the server (e.g. after the account was deleted). */
  forgetSession: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => (getAccessToken() ? getStoredUser() : null))
  const [signedIn, setSignedIn] = useState(() => Boolean(getAccessToken()))
  /** Bumped whenever the session changes (sign-in, sign-out), so a profile fetched for an older session is dropped. */
  const generation = useRef(0)

  const refreshUser = useCallback(async () => {
    if (!getAccessToken()) return null
    const gen = generation.current
    try {
      const { user: u } = await api.auth.me()
      if (gen !== generation.current || !getAccessToken()) return null
      storeUser(u)
      setUser(u)
      return u
    } catch {
      return null
    }
  }, [])

  // Refresh the profile once per load (verification state, name changes).
  useEffect(() => {
    void refreshUser()
  }, [refreshUser])

  // apiFetch clears the session when the refresh token is gone.
  useEffect(() => {
    const onSignedOut = () => {
      generation.current += 1
      setUser(null)
      setSignedIn(false)
    }
    window.addEventListener('nd:signed-out', onSignedOut)
    return () => window.removeEventListener('nd:signed-out', onSignedOut)
  }, [])

  const startSession = useCallback(async (res: AuthResponse) => {
    generation.current += 1
    storeSession(res.accessToken, res.refreshToken, res.user)
    setUser(res.user)
    setSignedIn(true)
    return (await refreshUser()) ?? res.user
  }, [refreshUser])

  const markVerified = useCallback(() => {
    setUser((u) => {
      if (!u) return u
      const next = { ...u, emailVerified: true }
      storeUser(next)
      return next
    })
  }, [])

  const forgetSession = useCallback(() => clearSession(), [])

  const signOut = useCallback(async () => {
    await api.auth.logout().catch(() => undefined)
    clearSession()
  }, [])

  return (
    <AuthContext.Provider value={{ user, signedIn, startSession, refreshUser, markVerified, signOut, forgetSession }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}

const onboardedKey = (userId: string) => `nudge:onboarded:${userId}`

export function isOnboarded(userId: string) {
  try {
    return localStorage.getItem(onboardedKey(userId)) === '1'
  } catch {
    return true
  }
}

export function setOnboarded(userId: string) {
  try {
    localStorage.setItem(onboardedKey(userId), '1')
  } catch {
    /* ignore */
  }
}

/** Where to go right after signing in: verify email first, then the one-time welcome tour. */
export function landingFor(user: User, next?: string) {
  if (user.emailVerified === false) return '/verify-email'
  if (!isOnboarded(user.id)) return next && next !== '/home' ? next : '/welcome'
  return next ?? '/home'
}
