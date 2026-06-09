export type HealthStatus = 'UP' | 'DOWN' | 'SLOW' | 'RATE_LIMITED' | 'UNKNOWN'
export type IncidentStatus = 'ACTIVE' | 'RESOLVED'
export type FailureType =
  | 'HTTP_STATUS'
  | 'TIMEOUT'
  | 'DNS_FAILURE'
  | 'CONNECTION_FAILURE'
  | 'RATE_LIMITED'
  | 'NETWORK_ERROR'

export interface MonitoredService {
  id: number
  name: string
  url: string
  method: 'GET'
  expectedStatusCode: number
  timeoutMs: number
  failureThreshold: number
  active: boolean
  currentStatus: HealthStatus
  lastCheckedAt: string | null
  lastResponseTimeMs: number | null
  lastHttpStatusCode: number | null
  lastFailureType: FailureType | null
  lastErrorMessage: string | null
  rateLimitedUntil: string | null
  activeIncident: boolean
  createdAt: string
  updatedAt: string
}

export interface HealthCheck {
  id: number
  serviceId: number
  status: HealthStatus
  httpStatusCode: number | null
  responseTimeMs: number | null
  failureType: FailureType | null
  errorMessage: string | null
  retryAfterSeconds: number | null
  rateLimitRemaining: number | null
  rateLimitResetAt: string | null
  checkedAt: string
}

export interface Incident {
  id: number
  serviceId: number
  serviceName: string
  status: IncidentStatus
  reason: string
  startedAt: string
  resolvedAt: string | null
  durationSeconds: number | null
  createdAt: string
  updatedAt: string
}

export interface DashboardSummary {
  totalServices: number
  upServices: number
  slowServices: number
  downServices: number
  rateLimitedServices: number
  unknownServices: number
  activeIncidents: number
  averageResponseTimeMs: number
  overallUptimePercentage: number
}

export interface ServiceMetrics {
  serviceId: number
  serviceName: string
  windowHours: number
  uptimePercentage: number
  averageResponseTimeMs: number
  p95ResponseTimeMs: number
  totalChecks: number
  failedChecks: number
  slowChecks: number
  latestStatus: HealthStatus
}

export interface ServiceInput {
  name: string
  url: string
  method: 'GET'
  expectedStatusCode: number
  timeoutMs: number
  failureThreshold: number
  active: boolean
}
