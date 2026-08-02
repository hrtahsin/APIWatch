import { ArrowUpRight, Pause, Pencil, Play } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { MonitoredService } from '../types'
import { formatRelative } from '../utils/format'
import { StatusBadge } from './StatusBadge'
import { EmptyState } from './EmptyState'

export function ServiceTable({
  services,
  compact = false,
  canManage = true,
  emptyDescription = 'Register an API endpoint to start collecting uptime and latency data.',
  emptyTitle = 'No services yet',
  onActiveChange,
  updatingServiceId,
}: {
  services: MonitoredService[]
  compact?: boolean
  canManage?: boolean
  emptyDescription?: string
  emptyTitle?: string
  onActiveChange?: (service: MonitoredService) => void
  updatingServiceId?: number | null
}) {
  if (services.length === 0) {
    return (
      <EmptyState
        title={emptyTitle}
        description={emptyDescription}
      />
    )
  }

  return (
    <div className="table-scroll mobile-card-table">
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
          {services.map((service) => {
            const ownership = [service.ownerName, service.teamName].filter(Boolean).join(' / ')

            return (
            <tr key={service.id}>
              <td data-label="Service">
                <div className="service-name-cell">
                  <span className="service-avatar">{service.name.slice(0, 2).toUpperCase()}</span>
                  <div>
                    <Link to={`/services/${service.id}`}>{service.name}</Link>
                    <span className="service-state">
                      {service.active ? 'Monitoring enabled' : 'Monitoring paused'}
                    </span>
                    {ownership && <span className="service-owner">{ownership}</span>}
                    {service.tags.length > 0 && (
                      <div className="tag-list">
                        {service.tags.map((tag) => (
                          <span key={tag}>{tag}</span>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              </td>
              <td data-label="Status">
                <StatusBadge status={service.currentStatus} />
              </td>
              {!compact && (
                <td data-label="Endpoint">
                  <span className="endpoint-text">{service.url}</span>
                </td>
              )}
              <td className="metric-cell" data-label="Latency">
                {service.lastResponseTimeMs === null ? '—' : `${service.lastResponseTimeMs} ms`}
              </td>
              <td className="muted-cell" data-label="Last checked">{formatRelative(service.lastCheckedAt)}</td>
              <td className="table-actions-cell" data-label="Actions">
                <div className="row-actions">
                  {!compact && onActiveChange && (
                    <button
                      aria-label={`${service.active ? 'Pause' : 'Resume'} ${service.name}`}
                      disabled={updatingServiceId === service.id}
                      onClick={() => onActiveChange(service)}
                      title={service.active ? 'Pause monitoring' : 'Resume monitoring'}
                      type="button"
                    >
                      {service.active ? <Pause size={16} /> : <Play size={16} />}
                    </button>
                  )}
                  {!compact && canManage && (
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
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
