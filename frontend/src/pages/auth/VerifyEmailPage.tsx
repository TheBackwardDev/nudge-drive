import { useEffect, useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { MailCheck } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CodeField } from '@/components/ui/Field'
import { landingFor, useAuth } from '@/context/AuthContext'
import { useToast } from '@/context/ToastContext'
import { api, ApiError, errorMessage } from '@/lib/api'
import { useCountdown } from '@/lib/hooks'
import { AuthLayout } from '@/pages/auth/AuthLayout'

const COOLDOWN = 45

/** Six-digit code screen shown after sign-up until the email is confirmed (the server blocks everything else). */
export default function VerifyEmailPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const { user, signedIn, markVerified, refreshUser, signOut } = useAuth()
  const [code, setCode] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [resending, setResending] = useState(false)
  const [left, setLeft] = useCountdown()

  // The code was just emailed at sign-up; start the cooldown so "Resend" isn't spammed.
  useEffect(() => setLeft(COOLDOWN), [setLeft])

  // They may have tapped the link in the email instead; check now and then.
  useEffect(() => {
    let stopped = false
    let timer = 0
    const tick = async () => {
      const u = await refreshUser()
      if (stopped) return
      if (u?.emailVerified) return void navigate(landingFor(u), { replace: true })
      timer = window.setTimeout(tick, 8000)
    }
    timer = window.setTimeout(tick, 8000)
    return () => {
      stopped = true
      window.clearTimeout(timer)
    }
  }, [refreshUser, navigate])

  if (!signedIn) return <Navigate to="/login" replace />
  if (user?.emailVerified) return <Navigate to={landingFor(user)} replace />

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (code.length !== 6) return setError('Enter the 6-digit code from the email.')
    setBusy(true)
    setError('')
    try {
      await api.auth.verifyEmail(code)
      markVerified()
      toast.show('Email verified', { tone: 'success' })
      if (user) navigate(landingFor({ ...user, emailVerified: true }), { replace: true })
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  async function resend() {
    if (resending) return
    setResending(true)
    setError('')
    try {
      const r = await api.auth.resendVerification()
      if (r.emailVerified) {
        markVerified()
        if (user) navigate(landingFor({ ...user, emailVerified: true }), { replace: true })
        return
      }
      setLeft(COOLDOWN)
      toast.show('New code sent. Check your spam folder too.')
    } catch (err) {
      if (err instanceof ApiError && err.status === 429) setLeft(COOLDOWN)
      setError(errorMessage(err))
    } finally {
      setResending(false)
    }
  }

  return (
    <AuthLayout
      title="Check your inbox"
      subtitle={<>We sent a 6-digit code to <strong className="text-ink">{user?.email ?? 'your email'}</strong>.</>}
      footer={<button type="button" onClick={() => void signOut().then(() => navigate('/login'))} className="font-semibold text-link hover:underline">Use a different account</button>}
    >
      <form onSubmit={submit} className="grid grid-cols-1 gap-4">
        <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-[22px] bg-mist text-accent"><MailCheck size={30} aria-hidden /></div>
        <CodeField value={code} onChange={setCode} autoFocus />
        {error && <p role="alert" className="rounded-[16px] bg-danger-soft px-4 py-3 text-[13.5px] text-danger">{error}</p>}
        <Button type="submit" size="lg" block loading={busy} disabled={code.length !== 6}>Verify email</Button>
        <Button variant="ghost" block onClick={resend} loading={resending} disabled={left > 0}>
          {left > 0 ? `Resend code in ${left}s` : "Didn't get it? Resend code"}
        </Button>
        <p className="text-center text-[12.5px] text-muted">You can also tap the link in the email; this page notices on its own.</p>
      </form>
    </AuthLayout>
  )
}
