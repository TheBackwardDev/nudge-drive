import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Lock, Mail } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { PasswordField, TextField } from '@/components/ui/Field'
import { landingFor, useAuth } from '@/context/AuthContext'
import { api, errorMessage } from '@/lib/api'
import { safeNext } from '@/lib/session'
import { AuthLayout, GoogleButton, OrDivider } from '@/pages/auth/AuthLayout'

export default function LoginPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const next = safeNext(params.get('next'))
  const { startSession } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!email.trim() || !password) return setError('Enter your email and password.')
    setBusy(true)
    setError('')
    try {
      const user = await startSession(await api.auth.login(email.trim(), password))
      navigate(landingFor(user, next), { replace: true })
    } catch (err) {
      setError(errorMessage(err, 'Sign-in failed'))
      setBusy(false)
    }
  }

  return (
    <AuthLayout
      title="Welcome back"
      subtitle="Sign in to reach all your drives in one place"
      footer={<>New here? <Link to={`/register${params.get('next') ? `?next=${encodeURIComponent(next)}` : ''}`} className="font-bold text-link hover:underline">Create an account</Link></>}
    >
      <form onSubmit={submit} className="grid grid-cols-1 gap-3.5" noValidate>
        <TextField label="Email" type="email" autoComplete="email" placeholder="Email" icon={Mail} value={email} onChange={(e) => setEmail(e.target.value)} required />
        <PasswordField label="Password" autoComplete="current-password" placeholder="Password" icon={Lock} value={password} onChange={(e) => setPassword(e.target.value)} required />
        {error && <p role="alert" className="rounded-[16px] bg-danger-soft px-4 py-3 text-[13.5px] text-danger">{error}</p>}
        <Button type="submit" size="lg" block loading={busy} className="mt-1">Sign in</Button>
      </form>
      <OrDivider />
      <GoogleButton onError={setError} next={params.get('next') ? next : undefined} />
    </AuthLayout>
  )
}
