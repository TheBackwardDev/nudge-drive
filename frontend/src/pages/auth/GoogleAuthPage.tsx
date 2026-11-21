import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { CircleAlert } from 'lucide-react'
import { LogoTile } from '@/components/brand/Brand'
import { ButtonLink } from '@/components/ui/Button'
import { Spinner } from '@/components/ui/Spinner'
import { landingFor, useAuth } from '@/context/AuthContext'
import { api, errorMessage } from '@/lib/api'
import { safeNext } from '@/lib/session'

/** Return page for Google sign-in: the backend redirects to /google-auth?token=… (or ?status=error). */
export default function GoogleAuthPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const { startSession } = useAuth()
  const [error, setError] = useState('')
  const started = useRef(false)
  const token = params.get('token')

  useEffect(() => {
    if (started.current) return
    started.current = true
    if (params.get('status') === 'error' || !token) {
      setError('Google sign-in did not finish. Please try again.')
      return
    }
    api.auth.googleExchange(token)
      .then((res) => startSession(res))
      .then((user) => {
        const stored = sessionStorage.getItem('nd:next')
        sessionStorage.removeItem('nd:next')
        navigate(landingFor(user, stored ? safeNext(stored) : undefined), { replace: true })
      })
      .catch((e) => setError(errorMessage(e, 'Google sign-in failed. Please try again.')))
  }, [token, params, startSession, navigate])

  return (
    <div className="flex min-h-dvh items-center justify-center p-6">
      <div className="auth-bg" aria-hidden />
      <div className="glass-strong w-full max-w-sm rounded-[32px] p-8 text-center shadow-lift">
        <div className="mx-auto w-fit"><LogoTile size={64} /></div>
        {error ? (
          <>
            <CircleAlert size={28} className="mx-auto mt-6 text-danger" aria-hidden />
            <h1 className="mt-3 text-[22px] font-bold">Sign-in failed</h1>
            <p role="alert" className="mt-2 text-[14px] text-muted">{error}</p>
            <ButtonLink to="/login" block className="mt-6">Back to sign in</ButtonLink>
          </>
        ) : (
          <>
            <h1 className="mt-6 text-[22px] font-bold">Signing you in…</h1>
            <p className="mt-2 text-[14px] text-muted">Finishing Google sign-in.</p>
            <Spinner size={28} className="mx-auto mt-6 text-accent" label="Signing in" />
            <Link to="/login" className="mt-6 block text-[13px] text-link hover:underline">Cancel</Link>
          </>
        )}
      </div>
    </div>
  )
}
