import { AlertTriangle, Clock3, Play, ShieldAlert, Trash2 } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  deleteService,
  getApiErrorMessage,
  getHealthChecksPage,
  getIncidents,
  getService,
  getServiceMetrics,
  runCheck,
} from '../api/client'
import { useAuth } from '../auth/useAuth'
import { IncidentTable } from '../components/IncidentTable'
import { LatencyChart } from '../components/LatencyChart'
import { Pagination } from '../components/Pagination'
import { StatusBadge } from '../components/StatusBadge'
import { useAutoRefresh } from '../hooks/useAutoRefresh'
import type {
  FailureType,
  HealthCheck,
  Incident,
  MonitoredService,
  ServiceMetrics,
} from '../types'
import { formatDate } from '../utils/format'

const failureLabels: Record<FailureType, string> = {
  HTTP_STATUS: 'Unexpected HTTP status',
  RESPONSE_VALIDATION: 'Response body validation failed',
  SECURITY_BLOCKED: 'Target blocked by security policy',
  TIMEOUT: 'Request timed out',
  DNS_FAILURE: 'DNS lookup failed',
  CONNECTION_FAILURE: 'Connection failed',
  RATE_LIMITED: 'Rate limit reached',
  NETWORK_ERROR: 'Network request failed',
}

const checkPageSize = 10

export function ServiceDetailPage() {
  const { canManage } = useAuth()
  const serviceId = Number(useParams().id)
  const navigate = useNavigate()
  const [service, setService] = useState<MonitoredService | null>(null)
  const [checks, setChecks] = useState<HealthCheck[]>([])
  const [checkPage, setCheckPage] = useState(0)
  const [checkTotalElements, setCheckTotalElements] = useState(0)
  const [checkTotalPages, setCheckTotalPages] = useState(0)
  const [incidents, setIncidents] = useState<Incident[]>([])
  const [metrics, setMetrics] = useState<ServiceMetrics | null>(null)
  const [loading, setLoading] = useState(true)
  const [running, setRunning] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async (showLoading = false, targetCheckPage = checkPage) => {
    if (showLoading) setLoading(true)
    const [serviceResponse, checksResponse, incidentsResponse, metricsResponse] =
      await Promise.all([
        getService(serviceId),
        getHealthChecksPage(serviceId, targetCheckPage, checkPageSize),
        getIncidents(undefined, serviceId),
        getServiceMetrics(serviceId),
      ])
    setService(serviceResponse)
    setChecks(checksResponse.content)
    setCheckTotalElements(checksResponse.totalElements)
    setCheckTotalPages(checksResponse.totalPages)
    setIncidents(incidentsResponse)
    setMetrics(metricsResponse)
    if (showLoading) setLoading(false)
  }, [checkPage, serviceId])

  useEffect(() => {
    load(true)
      .then(() => setError(null))
      .catch((loadError) => {
        setError(getApiErrorMessage(loadError, 'Unable to load service'))
        setLoading(false)
      })
  }, [load])

  useAutoRefresh(() =>
    load(false).catch((loadError) => {
      setError(getApiErrorMessage(loadError, 'Unable to load service'))
    }),
  )

  async function handleRunCheck() {
    try {
      setRunning(true)
      await runCheck(serviceId)
      setCheckPage(0)
      await load(false, 0)
      setError(null)
    } catch (runError) {
      setError(getApiErrorMessage(runError, 'Health check failed'))
    } finally {
      setRunning(false)
    }
  }

  async function handleDelete() {
    try {
      setDeleting(true)
      await deleteService(serviceId)
      navigate('/services', { replace: true })
    } catch (deleteError) {
      setError(getApiErrorMessage(deleteError, 'Unable to delete service'))
      setConfirmingDelete(false)
      setDeleting(false)
    }
  }

  if (loading) return <div className="panel loading-panel">Loading service details...</div>
  if (!service) return <div className="notice danger">Service not found.</div>

  const rateLimitedUntil = service.rateLimitedUntil
    ? new Date(service.rateLimitedUntil)
    : null
  const checksPaused = rateLimitedUntil !== null && rateLimitedUntil.getTime() > Date.now()

  return (
    <div className="detail-grid">
      {error && <div className="notice danger">{error}</div>}

      <section className="panel service-hero">
        <div>
          <span className="eyebrow">Service health</span>
          <h2>{service.name}</h2>
          <p>{service.url}</p>
          <div className="hero-meta">
            <StatusBadge status={service.currentStatus} />
            {service.ownerName && <span>Owner {service.ownerName}</span>}
            {service.teamName && <span>Team {service.teamName}</span>}
            <span>
              Expected {service.expectedStatusMin === service.expectedStatusMax
                ? service.expectedStatusMin
                : `${service.expectedStatusMin}-${service.expectedStatusMax}`}
            </span>
            <span>{service.timeoutMs} ms timeout</span>
            <span>Every {service.checkIntervalSeconds}s</span>
            <span>Threshold {service.failureThreshold}</span>
            <span>
              Notifications{' '}
              {[
                service.notifyOnIncidentOpen ? 'open' : null,
                service.notifyOnIncidentResolve ? 'resolve' : null,
              ]
                .filter(Boolean)
                .join(' + ') || 'muted'}
            </span>
            {service.notificationEscalationMinutes > 0 && (
              <span>Escalate after {service.notificationEscalationMinutes}m</span>
            )}
            {service.responseBodyContains && <span>Body validation enabled</span>}
            {service.tags.map((tag) => (
              <span key={tag}>#{tag}</span>
            ))}
          </div>
        </div>
        {canManage && (
          <div className="hero-actions">
            <button
              className="danger-button"
              onClick={() => setConfirmingDelete(true)}
              type="button"
            >
              <Trash2 size={17} />
              Delete service
            </button>
            <Link className="secondary-button" to={`/services/${service.id}/edit`}>
              Edit configuration
            </Link>
            <button
              className="primary-button"
              onClick={handleRunCheck}
              disabled={running || checksPaused}
              type="button"
            >
              {checksPaused ? <Clock3 size={17} /> : <Play size={17} />}
              {running ? 'Running...' : checksPaused ? 'Rate limit pause' : 'Run check'}
            </button>
          </div>
        )}
      </section>

      {(service.lastFailureType || checksPaused) && (
        <section className="diagnostic-panel">
          <AlertTriangle size={22} />
          <div>
            <span className="eyebrow">Latest check diagnosis</span>
            <h3>
              {service.lastFailureType
                ? failureLabels[service.lastFailureType]
                : 'Checks temporarily paused'}
            </h3>
            <p>
              {service.lastErrorMessage ??
                `Automatic and manual checks resume after ${formatDate(service.rateLimitedUntil)}.`}
            </p>
            <div className="diagnostic-meta">
              <span>HTTP {service.lastHttpStatusCode ?? 'not received'}</span>
              {checksPaused && <span>Retry after {formatDate(service.rateLimitedUntil)}</span>}
            </div>
          </div>
        </section>
      )}

      {canManage && confirmingDelete && (
        <section className="delete-confirmation">
          <div>
            <strong>Delete {service.name}?</strong>
            <span>
              This permanently removes the service, its health-check history, and its incidents.
            </span>
          </div>
          <div className="delete-confirmation-actions">
            <button
              className="secondary-button"
              disabled={deleting}
              onClick={() => setConfirmingDelete(false)}
              type="button"
            >
              Cancel
            </button>
            <button className="danger-button solid" disabled={deleting} onClick={handleDelete} type="button">
              <Trash2 size={17} />
              {deleting ? 'Deleting...' : 'Delete permanently'}
            </button>
          </div>
        </section>
      )}

      <section className="metrics-strip">
        <div className="mini-metric">
          <span>Uptime</span>
          <strong>{metrics?.uptimePercentage.toFixed(2) ?? '0.00'}%</strong>
        </div>
        <div className="mini-metric">
          <span>Average latency</span>
          <strong>{Math.round(metrics?.averageResponseTimeMs ?? 0)} ms</strong>
        </div>
        <div className="mini-metric">
          <span>P95 latency</span>
          <strong>{Math.round(metrics?.p95ResponseTimeMs ?? 0)} ms</strong>
        </div>
        <div className="mini-metric">
          <span>Failed checks</span>
          <strong>{metrics?.failedChecks ?? 0}</strong>
        </div>
      </section>

      <section className="panel">
        <div className="panel-header">
          <div>
            <span>Trend</span>
            <h2>Latency history</h2>
          </div>
        </div>
        <LatencyChart checks={checks} />
      </section>

      <section className="panel">
        <div className="panel-header">
          <div>
            <span>Audit trail</span>
            <h2>Recent health checks</h2>
          </div>
        </div>
        <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr>
                <th>Status</th>
                <th>HTTP</th>
                <th>Latency</th>
                <th>Failure reason</th>
                <th>Checked</th>
              </tr>
            </thead>
            <tbody>
              {checks.map((check) => (
                <tr key={check.id}>
                  <td>
                    <StatusBadge status={check.status} />
                  </td>
                  <td className="metric-cell">{check.httpStatusCode ?? '—'}</td>
                  <td className="metric-cell">{check.responseTimeMs ?? '—'} ms</td>
                  <td className="reason-cell">
                    {check.failureType ? failureLabels[check.failureType] : '—'}
                    {check.errorMessage && <small>{check.errorMessage}</small>}
                  </td>
                  <td className="muted-cell">{formatDate(check.checkedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Pagination
          page={checkPage}
          totalPages={checkTotalPages}
          totalElements={checkTotalElements}
          onPageChange={setCheckPage}
        />
      </section>

      <section className="panel full-span">
        <div className="panel-header">
          <div>
            <span>Incidents</span>
            <h2>Service incident history</h2>
          </div>
          {service.activeIncident && (
            <div className="alert-pill">
              <ShieldAlert size={15} />
              Active incident
            </div>
          )}
        </div>
        <IncidentTable incidents={incidents} />
      </section>
    </div>
  )
}
