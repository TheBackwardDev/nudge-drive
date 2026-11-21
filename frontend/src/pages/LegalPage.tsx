import { Link } from 'react-router-dom'
import { PublicLayout } from '@/components/layout/PublicLayout'
import { useDocumentTitle } from '@/lib/hooks'
import { Markdown } from '@/lib/markdown'
import privacy from '@/content/privacy-policy.md?raw'
import terms from '@/content/terms-of-service.md?raw'

const DOCS = {
  privacy: { title: 'Privacy policy', source: privacy },
  terms: { title: 'Terms of service', source: terms },
} as const

/** /privacy and /terms, rendered from Markdown kept in src/content. */
export default function LegalPage({ doc }: { doc: keyof typeof DOCS }) {
  const { title, source } = DOCS[doc]
  useDocumentTitle(title)
  return (
    <PublicLayout>
      <article className="mx-auto max-w-3xl px-5 py-10 sm:py-14">
        <div className="glass-strong rounded-[32px] p-6 shadow-soft sm:p-10">
          <Markdown source={source} />
        </div>
        <p className="mt-6 text-center text-[13px] text-muted">
          See also: {doc === 'privacy' ? <Link to="/terms" className="text-link hover:underline">Terms of service</Link> : <Link to="/privacy" className="text-link hover:underline">Privacy policy</Link>} · <Link to="/delete-account" className="text-link hover:underline">Delete your account</Link>
        </p>
      </article>
    </PublicLayout>
  )
}
