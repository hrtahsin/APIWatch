import { useCallback, useEffect, useState } from 'react'
import { getApiErrorMessage, getAuditLogsPage } from '../api/client'
import { Pagination } from '../components/Pagination'
import { EmptyState } from '../components/EmptyState'
import { FeedbackNotice } from '../components/FeedbackNotice'
import { LoadingState } from '../components/LoadingState'
import type { AuditAction, AuditLog } from '../types'
import { formatDate } from '../utils/format'

const pageSize = 15

const actionLabels: Record<AuditAction, string> = {
  SERVICE_CREATED: 'Service created',
  SERVICE_UPDATED: 'Service updated',
  SERVICE_DELETED: 'Service deleted',
  SERVICE_PAUSED: 'Service paused',
  SERVICE_RESUMED: 'Service resumed',
  INCIDENT_RESOLVED: 'Incident resolved',
  NOTIFICATION_SETTINGS_UPDATED: 'Notification settings updated',
  NOTIFICATION_DELIVERY_RETRIED: 'Notification delivery retried',
}

export function AuditLogsPage() {
  const [logs, setLogs] = useState<AuditLog[]>([])
  const [page, setPage] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    const response = await getAuditLogsPage(page, pageSize)
    setLogs(response.content)
    setTotalElements(response.totalElements)
    setTotalPages(response.totalPages)
  }, [page])

  useEffect(() => {
    const initialLoad = window.setTimeout(() => {
      void load()
        .then(() => setError(null))
        .catch((loadError) => {
          setError(getApiErrorMessage(loadError, 'Unable to load audit logs'))
        })
        .finally(() => setLoading(false))
    }, 0)
    return () => window.clearTimeout(initialLoad)
  }, [load])

  return (
    <section className="panel full-panel">
      <div className="panel-header toolbar-header">
        <div>
          <span>Security</span>
          <h2>Audit logs</h2>
        </div>
        <span className="table-count">{totalElements} events</span>
      </div>

      {error && <FeedbackNotice tone="danger">{error}</FeedbackNotice>}

      {loading ? (
        <LoadingState label="Loading audit logs" variant="table" />
      ) : logs.length === 0 ? (
        <EmptyState
          title="No audit activity yet"
          description="Administrative changes will appear here as they happen."
        />
      ) : (
        <div className="table-scroll mobile-card-table">
          <table className="data-table">
            <thead>
              <tr>
                <th>When</th>
                <th>Actor</th>
                <th>Action</th>
                <th>Target</th>
                <th>Details</th>
              </tr>
            </thead>
            <tbody>
              {logs.map((log) => (
                <tr key={log.id}>
                  <td className="muted-cell" data-label="When">{formatDate(log.createdAt)}</td>
                  <td className="metric-cell" data-label="Actor">{log.actorUsername}</td>
                  <td data-label="Action">
                    <span className="audit-action">{actionLabels[log.action]}</span>
                  </td>
                  <td data-label="Target">
                    <strong>{log.targetName ?? log.targetType}</strong>
                    {log.targetId && <small>#{log.targetId}</small>}
                  </td>
                  <td className="reason-cell" data-label="Details">{log.details ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Pagination
        page={page}
        totalPages={totalPages}
        totalElements={totalElements}
        onPageChange={setPage}
      />
    </section>
  )
}
