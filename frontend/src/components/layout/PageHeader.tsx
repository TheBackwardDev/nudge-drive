import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import clsx from 'clsx'
import { ArrowLeft } from 'lucide-react'
import { IconButton } from '@/components/ui/Button'
import { useDocumentTitle } from '@/lib/hooks'

type Props = {
  title: string
  subtitle?: ReactNode
  /** Where the back arrow goes (phones show it for sub-pages; wide screens use the sidebar). */
  back?: string | (() => void)
  backAlways?: boolean
  actions?: ReactNode
  className?: string
}

export function PageHeader({ title, subtitle, back, backAlways, actions, className }: Props) {
  const navigate = useNavigate()
  useDocumentTitle(title)
  return (
    <header className={clsx('flex flex-wrap items-center gap-3 pb-4 pt-2.5 lg:pt-4', className)}>
      {back && (
        <IconButton
          icon={ArrowLeft}
          label="Back"
          className={backAlways ? '' : 'lg:hidden'}
          onClick={() => (typeof back === 'function' ? back() : navigate(back))}
        />
      )}
      <div className="min-w-0 flex-1">
        <h1 className="truncate text-[28px] font-bold leading-tight tracking-[-0.03em] text-ink">{title}</h1>
        {subtitle && <p className="mt-0.5 text-[14px] text-muted">{subtitle}</p>}
      </div>
      {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
    </header>
  )
}
