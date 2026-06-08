import { CheckCircle2 } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { Incident } from '../types'
import { formatDate, formatDuration } from '../utils/format'
import { StatusBadge } from './StatusBadge'

export function IncidentTable({
  incidents,
  onResolve,
}: {
  incidents: Incident[]
  onResolve?: (id: number) => void
}) {
  if (incidents.length === 0) {
    return <div className="empty-state">No incidents match this view.</div>
  }

  return (
    <div className="table-scroll">
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
              <td>
                <Link className="table-link" to={`/services/${incident.serviceId}`}>
                  {incident.serviceName}
                </Link>
              </td>
              <td>
                <StatusBadge status={incident.status} />
              </td>
              <td className="reason-cell">{incident.reason}</td>
              <td className="muted-cell">{formatDate(incident.startedAt)}</td>
              <td className="metric-cell">{formatDuration(incident.durationSeconds)}</td>
              {onResolve && (
                <td>
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
