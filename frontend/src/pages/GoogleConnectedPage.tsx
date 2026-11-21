import { useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { CircleCheck, CircleX } from 'lucide-react'
import { ButtonLink } from '@/components/ui/Button'

/** Return page after connecting a Google Drive (/google-connected?status=…). Tells the opener and closes. */
export default function GoogleConnectedPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const status = params.get('status') ?? 'success'
  const ok = status === 'success'

  useEffect(() => {
    document.title = ok ? 'Drive connected · Nudge Drive' : 'Connection failed · Nudge Drive'
    window.opener?.postMessage({ type: 'GOOGLE_CONNECTED', status }, window.location.origin)
    const t = window.setTimeout(() => {
      if (window.opener) window.close()
      else navigate(`/drives?connected=${status}`, { replace: true })
    }, 900)
    return () => window.clearTimeout(t)
  }, [navigate, status, ok])

  return (
    <div className="flex min-h-dvh items-center justify-center p-6">
      <div className="auth-bg" aria-hidden />
      <div className="glass-strong w-full max-w-sm rounded-[32px] p-8 text-center shadow-lift" role="status">
        {ok ? <CircleCheck size={48} className="mx-auto text-success" aria-hidden /> : <CircleX size={48} className="mx-auto text-danger" aria-hidden />}
        <h1 className="mt-4 text-[22px] font-bold">{ok ? 'Google Drive connected' : 'Connection failed'}</h1>
        <p className="mt-2 text-[14px] text-muted">{ok ? 'This window closes on its own.' : 'Close this window and try again.'}</p>
        <ButtonLink to="/drives" variant="secondary" block className="mt-6">Go to Drives</ButtonLink>
      </div>
    </div>
  )
}
