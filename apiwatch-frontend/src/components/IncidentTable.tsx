import { CheckCircle2 } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { Incident } from '../types'
import { formatDate, formatDuration } from '../utils/format'
import { StatusBadge } from './StatusBadge'
import { EmptyState } from './EmptyState'

export function IncidentTable({
  incidents,
  onResolve,
}: {
  incidents: Incident[]
  onResolve?: (id: number) => void
}) {
  if (incidents.length === 0) {
    return (
      <EmptyState
        title="No incidents found"
        description="There are no incidents matching the selected view."
      />
    )
  }

  return (
    <div className="table-scroll mobile-card-table">
      <table className="data-table incident-table">
        <thead>
          <tr>
            <th>Service</th>
            <th>Status</th>
            <th>Reason</th>
            <th>Started</th>
            <th>Duration</th>
            {onResolve && <th aria-label="Actions" />}
          </tr>
        </thead>
        <tbody>
          {incidents.map((incident) => (
            <tr key={incident.id}>
              <td data-label="Service">
                <Link className="table-link" to={`/services/${incident.serviceId}`}>
                  {incident.serviceName}
                </Link>
              </td>
              <td data-label="Status">
                <StatusBadge status={incident.status} />
              </td>
              <td className="reason-cell" data-label="Reason">{incident.reason}</td>
              <td className="muted-cell" data-label="Started">{formatDate(incident.startedAt)}</td>
              <td className="metric-cell" data-label="Duration">{formatDuration(incident.durationSeconds)}</td>
              {onResolve && (
                <td className="table-actions-cell" data-label="Actions">
                  {incident.status === 'ACTIVE' && (
                    <button
                      className="text-button"
                      onClick={() => onResolve(incident.id)}
                      type="button"
                    >
                      <CheckCircle2 size={15} />
                      Resolve
                    </button>
                  )}
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
