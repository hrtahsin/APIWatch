import { Inbox } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'

export function EmptyState({
  action,
  description,
  icon: Icon = Inbox,
  title,
}: {
  action?: ReactNode
  description: string
  icon?: LucideIcon
  title: string
}) {
  return (
    <div className="empty-state">
      <span className="empty-state-icon" aria-hidden="true">
        <Icon size={22} />
      </span>
      <strong>{title}</strong>
      <p>{description}</p>
      {action}
    </div>
  )
}
