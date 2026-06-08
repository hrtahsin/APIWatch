import type { LucideIcon } from 'lucide-react'

interface SummaryCardProps {
  label: string
  value: string | number
  detail: string
  icon: LucideIcon
  tone?: 'default' | 'success' | 'warning' | 'danger'
}

export function SummaryCard({
  label,
  value,
  detail,
  icon: Icon,
  tone = 'default',
}: SummaryCardProps) {
  return (
    <article className={`summary-card tone-${tone}`}>
      <div className="summary-card-top">
        <span>{label}</span>
        <div className="summary-icon">
          <Icon size={18} />
        </div>
      </div>
      <strong>{value}</strong>
      <span className="summary-detail">{detail}</span>
    </article>
  )
}
