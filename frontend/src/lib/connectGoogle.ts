import { useCallback, useEffect, useRef, useState } from 'react'
import { api, errorMessage } from '@/lib/api'
import { emitChange } from '@/lib/hooks'

/**
 * Where one connect attempt is. Only `awaitingPopup` listens for the pop-up's answer, so other pop-ups that post
 * the same message (another page's connect button, the Google Photos permission) don't reach an idle hook.
 */
type ConnectState = { kind: 'idle' } | { kind: 'awaitingPopup'; popup: Window } | { kind: 'redirecting' }

/**
 * Connect another Google Drive in a pop-up. Google sends the pop-up back to /google-connected,
 * which posts GOOGLE_CONNECTED to this window. Without pop-ups the whole tab goes to Google and back.
 */
export function useConnectGoogle(onResult: (ok: boolean, error?: string) => void) {
  const [state, setState] = useState<ConnectState>({ kind: 'idle' })
  const cb = useRef(onResult)
  cb.current = onResult
  /** Ends a connect that is still starting when the page is left (or restored from the back/forward cache). */
  const attempt = useRef(0)

  const popup = state.kind === 'awaitingPopup' ? state.popup : null
  useEffect(() => {
    if (!popup) return
    const onMessage = (e: MessageEvent) => {
      if (e.origin !== window.location.origin || e.data?.type !== 'GOOGLE_CONNECTED') return
      setState({ kind: 'idle' })
      const ok = e.data.status === 'success'
      if (ok) emitChange('accounts', 'storage', 'files')
      cb.current(ok, ok ? undefined : 'Google Drive was not connected.')
    }
    // Stop the spinner if the pop-up is closed without finishing (after a moment, in case its message is still on the way).
    let grace = 0
    const timer = window.setInterval(() => {
      if (!popup.closed) return
      window.clearInterval(timer)
      grace = window.setTimeout(() => setState({ kind: 'idle' }), 500)
    }, 800)
    window.addEventListener('message', onMessage)
    return () => {
      window.removeEventListener('message', onMessage)
      window.clearInterval(timer)
      window.clearTimeout(grace)
    }
  }, [popup])

  // Back from Google after a full-page redirect, the page may come back from the bfcache as it was left.
  useEffect(() => {
    const onShow = (e: PageTransitionEvent) => {
      if (!e.persisted) return
      attempt.current += 1
      setState({ kind: 'idle' })
    }
    window.addEventListener('pageshow', onShow)
    return () => {
      window.removeEventListener('pageshow', onShow)
      attempt.current += 1
    }
  }, [])

  const busy = state.kind !== 'idle'
  const connect = useCallback(async () => {
    if (busy) return
    const id = ++attempt.current
    const win = window.open('', 'nudge-google-connect', 'width=520,height=720')
    setState(win ? { kind: 'awaitingPopup', popup: win } : { kind: 'redirecting' })
    try {
      const { url } = await api.accounts.googleConnectUrl()
      if (id !== attempt.current) return void win?.close()
      if (win) {
        if (!win.closed) win.location.href = url
      } else window.location.href = url
    } catch (e) {
      if (id !== attempt.current) return
      win?.close()
      setState({ kind: 'idle' })
      cb.current(false, errorMessage(e, "Couldn't start the Google connection"))
    }
  }, [busy])

  return { connect, busy }
}
