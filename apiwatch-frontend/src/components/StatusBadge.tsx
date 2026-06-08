import type { HealthStatus, IncidentStatus } from '../types'

export function StatusBadge({ status }: { status: HealthStatus | IncidentStatus }) {
  return (
    <span className={`status-badge status-${status.toLowerCase()}`}>
      <span className="status-dot" />
      {status}
    </span>
  )
}
