import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import clsx from 'clsx'
import { CircleCheck, KeyRound, Lock, Mail, ShieldAlert, UserX } from 'lucide-react'
import { Button, ButtonLink } from '@/components/ui/Button'
import { Checkbox, CodeField, PasswordField } from '@/components/ui/Field'
import { Notice } from '@/components/ui/Surface'
import { PublicLayout } from '@/components/layout/PublicLayout'
import { useAuth } from '@/context/AuthContext'
import { api, ApiError, errorMessage } from '@/lib/api'
import { useCountdown, useDocumentTitle } from '@/lib/hooks'
import { Markdown } from '@/lib/markdown'
import explainer from '@/content/account-deletion.md?raw'

// The page has its own heading; drop the document's title line.
const body = explainer.replace(/^#\s+[^\n]*\n+/, '')

/** Public account-deletion page (Google Play requirement): explains what goes, and deletes when signed in. */
export default function DeleteAccountPage() {
  useDocumentTitle('Delete your account')
  const { signedIn, user } = useAuth()
  const [deleted, setDeleted] = useState<string | null>(null)

  return (
    <PublicLayout>
      <div className="mx-auto grid max-w-3xl grid-cols-1 gap-6 px-5 py-10 sm:py-14">
        <div>
          <span className="flex h-14 w-14 items-center justify-center rounded-[18px] bg-danger-soft text-danger"><UserX size={26} aria-hidden /></span>
          <h1 className="mt-5 text-[32px] font-bold leading-tight tracking-[-0.03em] text-ink sm:text-[38px]">Delete your Nudge Drive account</h1>
          <p className="mt-2 text-[16px] text-muted">Everything Nudge Drive stores about you is removed. <strong className="text-ink">Your files are not deleted</strong>: they stay in your own Google Drive or S3 storage.</p>
        </div>

        {deleted ? (
          <div className="glass-strong rounded-[28px] p-6 text-center shadow-soft sm:p-8" role="status">
            <CircleCheck size={48} className="mx-auto text-success" aria-hidden />
            <h2 className="mt-4 text-[24px] font-bold">Your account was deleted</h2>
            <p className="mx-auto mt-2 max-w-md text-[14.5px] text-muted">
              {deleted} and everything Nudge Drive stored about it are gone, and its access to your drives was revoked. Your files are still in your own Google Drive or storage. A confirmation email is on its way.
            </p>
            <ButtonLink to="/" variant="secondary" className="mt-6">Back to the home page</ButtonLink>
          </div>
        ) : signedIn && user ? (
          <DeleteForm email={user.email} onDeleted={() => { setDeleted(user.email); window.scrollTo({ top: 0 }) }} />
        ) : (
          <div className="glass-strong rounded-[28px] p-6 shadow-soft sm:p-8">
            <h2 className="text-[20px] font-semibold">Sign in to continue</h2>
            <p className="mt-1.5 text-[14.5px] text-muted">To protect your account, sign in with the account you want to delete. You'll come straight back here to confirm.</p>
            <div className="mt-5 flex flex-wrap gap-2.5">
              <ButtonLink to="/login?next=%2Fdelete-account" icon={Lock}>Sign in to delete</ButtonLink>
              <a href="mailto:support@nudge.qd.je?subject=Delete%20my%20account" className="press inline-flex h-11 items-center gap-2 rounded-[22px] border border-line bg-surface px-5 text-[14px] font-bold text-ink hover:bg-field-solid"><Mail size={18} aria-hidden />Can't sign in? Email us</a>
            </div>
          </div>
        )}

        <article className="glass rounded-[28px] p-6 sm:p-8">
          <Markdown source={body} />
        </article>
        <p className="text-center text-[13px] text-muted">
          <Link to="/privacy" className="text-link hover:underline">Privacy policy</Link> · <Link to="/terms" className="text-link hover:underline">Terms of service</Link>
        </p>
      </div>
    </PublicLayout>
  )
}

function DeleteForm({ email, onDeleted }: { email: string; onDeleted: () => void }) {
  const { forgetSession } = useAuth()
  const [method, setMethod] = useState<'password' | 'code'>('password')
  const [password, setPassword] = useState('')
  const [code, setCode] = useState('')
  const [codeSent, setCodeSent] = useState(false)
  const [sending, setSending] = useState(false)
  const [left, setLeft] = useCountdown()
  const [understood, setUnderstood] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function sendCode() {
    setSending(true)
    setError('')
    try {
      await api.auth.sendDeleteCode()
      setCodeSent(true)
      setLeft(45)
    } catch (e) {
      if (e instanceof ApiError && e.status === 429) setLeft(45)
      setError(errorMessage(e))
    } finally {
      setSending(false)
    }
  }

  const ready = understood && (method === 'password' ? password.length > 0 : code.length === 6)

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!ready) return
    setBusy(true)
    setError('')
    try {
      await api.auth.deleteAccount(method === 'password' ? { password } : { code })
      forgetSession()
      onDeleted()
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="glass-strong grid grid-cols-1 gap-5 rounded-[28px] p-6 shadow-soft sm:p-8">
      <div>
        <h2 className="text-[20px] font-semibold">Confirm it's you</h2>
        <p className="mt-1 text-[14px] text-muted">Signed in as <strong className="text-ink">{email}</strong>.</p>
      </div>
      <div role="tablist" aria-label="How to confirm" className="inline-flex w-fit rounded-[20px] bg-mist p-1">
        {([['password', 'Password', KeyRound], ['code', 'Email me a code', Mail]] as const).map(([value, label, Icon]) => (
          <button
            key={value}
            type="button"
            role="tab"
            aria-selected={method === value}
            onClick={() => { setMethod(value); setError('') }}
            className={clsx('press inline-flex h-9 items-center gap-1.5 rounded-[16px] px-3.5 text-[13px] font-semibold', method === value ? 'bg-surface text-ink shadow-soft' : 'text-muted hover:text-ink')}
          >
            <Icon size={15} aria-hidden />{label}
          </button>
        ))}
      </div>

      {method === 'password' ? (
        <PasswordField label="Password" showLabel autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} hint="Signed up with Google? Use “Email me a code” instead." />
      ) : (
        <div className="grid grid-cols-1 gap-3">
          <p className="text-[14px] text-muted">We'll email a 6-digit code to {email}. It expires in 15 minutes.</p>
          {codeSent && <CodeField value={code} onChange={setCode} label="Code from the email" autoFocus />}
          <Button variant={codeSent ? 'ghost' : 'soft'} onClick={sendCode} loading={sending} disabled={left > 0} className="justify-self-start">
            {left > 0 ? `Send a new code in ${left}s` : codeSent ? 'Send a new code' : 'Email me a code'}
          </Button>
        </div>
      )}

      <Notice tone="warn" icon={ShieldAlert} title="This can't be undone">
        Your account, drive connections, file list, share links, invites, API keys and activity are permanently removed. Files stay in your own storage.
      </Notice>
      <Checkbox checked={understood} onChange={setUnderstood} label="I understand. Delete my account." />
      {error && <p role="alert" className="rounded-[16px] bg-danger-soft px-4 py-3 text-[13.5px] text-danger">{error}</p>}
      <div className="flex flex-wrap gap-2.5">
        <Button type="submit" variant="danger" icon={UserX} loading={busy} disabled={!ready} className="!bg-danger !text-white dark:!text-black">Delete my account</Button>
        <ButtonLink to="/home" variant="secondary">Cancel</ButtonLink>
      </div>
    </form>
  )
}
