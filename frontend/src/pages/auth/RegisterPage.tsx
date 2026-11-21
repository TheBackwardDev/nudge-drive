import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Lock, Mail, UserRound } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { PasswordField, TextField } from '@/components/ui/Field'
import { landingFor, useAuth } from '@/context/AuthContext'
import { api, errorMessage } from '@/lib/api'
import { safeNext } from '@/lib/session'
import { AuthLayout, GoogleButton, OrDivider } from '@/pages/auth/AuthLayout'

const recaptchaSiteKey = import.meta.env.VITE_RECAPTCHA_SITE_KEY?.trim()

declare global {
  interface Window {
    grecaptcha?: {
      render: (el: HTMLElement, opts: { sitekey: string; theme?: string; callback: (token: string) => void; 'expired-callback': () => void }) => number
      reset: (id?: number) => void
    }
  }
}

type Errors = Partial<Record<'name' | 'email' | 'password', string>>

function validate(name: string, email: string, password: string): Errors {
  const e: Errors = {}
  if (name.trim().length < 2) e.name = 'Your name needs at least 2 letters.'
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) e.email = "That email doesn't look right."
  if (password.length < 8) e.password = 'Use at least 8 characters for the password.'
  return e
}

export default function RegisterPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const next = safeNext(params.get('next'))
  const { startSession } = useAuth()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [touched, setTouched] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [captcha, setCaptcha] = useState('')
  const captchaEl = useRef<HTMLDivElement>(null)
  const captchaId = useRef<number | null>(null)
  const errors = touched ? validate(name, email, password) : {}

  // Optional reCAPTCHA (only when the build has a site key; the server checks it if configured).
  useEffect(() => {
    if (!recaptchaSiteKey) return
    const render = () => {
      if (!captchaEl.current || !window.grecaptcha || captchaId.current !== null) return
      captchaId.current = window.grecaptcha.render(captchaEl.current, { sitekey: recaptchaSiteKey, callback: setCaptcha, 'expired-callback': () => setCaptcha('') })
    }
    const existing = document.getElementById('recaptcha-script')
    if (existing) return render()
    const s = document.createElement('script')
    s.id = 'recaptcha-script'
    s.src = 'https://www.google.com/recaptcha/api.js?render=explicit'
    s.async = true
    s.onload = render
    document.body.appendChild(s)
  }, [])

  async function submit(e: FormEvent) {
    e.preventDefault()
    setTouched(true)
    if (Object.keys(validate(name, email, password)).length) return
    if (recaptchaSiteKey && !captcha) return setError('Please complete the captcha.')
    setBusy(true)
    setError('')
    try {
      const user = await startSession(await api.auth.register(name.trim(), email.trim(), password, captcha || undefined))
      navigate(landingFor(user, params.get('next') ? next : undefined), { replace: true })
    } catch (err) {
      setError(errorMessage(err, 'Could not create the account'))
      if (captchaId.current !== null) window.grecaptcha?.reset(captchaId.current)
      setCaptcha('')
      setBusy(false)
    }
  }

  return (
    <AuthLayout
      title="Create your account"
      subtitle="One pool for every drive"
      footer={<>Already have an account? <Link to={`/login${params.get('next') ? `?next=${encodeURIComponent(next)}` : ''}`} className="font-bold text-link hover:underline">Sign in</Link></>}
    >
      <form onSubmit={submit} className="grid grid-cols-1 gap-3.5" noValidate>
        <TextField label="Your name" autoComplete="name" placeholder="Your name" icon={UserRound} value={name} onChange={(e) => setName(e.target.value)} error={errors.name} />
        <TextField label="Email" type="email" autoComplete="email" placeholder="Email" icon={Mail} value={email} onChange={(e) => setEmail(e.target.value)} error={errors.email} />
        <PasswordField label="Password" autoComplete="new-password" placeholder="Password (8+ characters)" icon={Lock} value={password} onChange={(e) => setPassword(e.target.value)} error={errors.password} />
        {recaptchaSiteKey && <div ref={captchaEl} className="min-h-[78px]" />}
        {error && <p role="alert" className="rounded-[16px] bg-danger-soft px-4 py-3 text-[13.5px] text-danger">{error}</p>}
        <Button type="submit" size="lg" block loading={busy} className="mt-1">Create account</Button>
        <p className="text-center text-[12.5px] text-muted">
          By creating an account you agree to the <Link to="/terms" className="font-semibold text-link hover:underline">Terms</Link> and <Link to="/privacy" className="font-semibold text-link hover:underline">Privacy Policy</Link>.
        </p>
      </form>
      <OrDivider />
      <GoogleButton label="Continue with Google" onError={setError} />
    </AuthLayout>
  )
}
