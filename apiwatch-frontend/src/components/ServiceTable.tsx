import { ArrowUpRight, Pencil } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { MonitoredService } from '../types'
import { formatRelative } from '../utils/format'
import { StatusBadge } from './StatusBadge'

export function ServiceTable({
  services,
  compact = false,
}: {
  services: MonitoredService[]
  compact?: boolean
}) {
  if (services.length === 0) {
    return <div className="empty-state">No services have been registered yet.</div>
  }

  return (
    <div className="table-scroll">
      <table className="data-table">
        <thead>
          <tr>
            <th>Service</th>
            <th>Status</th>
            {!compact && <th>Endpoint</th>}
            <th>Latency</th>
            <th>Last checked</th>
            <th aria-label="Actions" />
          </tr>
        </thead>
        <tbody>
          {services.map((service) => (
            <tr key={service.id}>
              <td>
                <div className="service-name-cell">
                  <span className="service-avatar">{service.name.slice(0, 2).toUpperCase()}</span>
                  <div>
                    <Link to={`/services/${service.id}`}>{service.name}</Link>
                    <span>{service.active ? 'Monitoring enabled' : 'Monitoring paused'}</span>
                  </div>
                </div>
              </td>
              <td>
                <StatusBadge status={service.currentStatus} />
              </td>
              {!compact && (
                <td>
                  <span className="endpoint-text">{service.url}</span>
                </td>
              )}
              <td className="metric-cell">
                {service.lastResponseTimeMs === null ? '—' : `${service.lastResponseTimeMs} ms`}
              </td>
              <td className="muted-cell">{formatRelative(service.lastCheckedAt)}</td>
              <td>
                <div className="row-actions">
                  {!compact && (
                    <Link to={`/services/${service.id}/edit`} aria-label={`Edit ${service.name}`}>
                      <Pencil size={16} />
                    </Link>
                  )}
                  <Link to={`/services/${service.id}`} aria-label={`View ${service.name}`}>
                    <ArrowUpRight size={17} />
                  </Link>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
