export type HealthStatus = 'UP' | 'DOWN' | 'SLOW' | 'RATE_LIMITED' | 'UNKNOWN'
export type IncidentStatus = 'ACTIVE' | 'RESOLVED'
export type RequestAuthType = 'NONE' | 'BEARER' | 'API_KEY'
export type NotificationEventType = 'INCIDENT_OPENED' | 'INCIDENT_RESOLVED'
export type NotificationDeliveryStatus = 'SENT' | 'FAILED' | 'SKIPPED_COOLDOWN'
export type FailureType =
  | 'HTTP_STATUS'
  | 'RESPONSE_VALIDATION'
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
  expectedStatusMin: number
  expectedStatusMax: number
  timeoutMs: number
  checkIntervalSeconds: number
  responseBodyContains: string | null
  failureThreshold: number
  active: boolean
  currentStatus: HealthStatus
  lastCheckedAt: string | null
  lastResponseTimeMs: number | null
  lastHttpStatusCode: number | null
  lastFailureType: FailureType | null
  lastErrorMessage: string | null
  rateLimitedUntil: string | null
  customHeaderNames: string[]
  authType: RequestAuthType
  authHeaderName: string | null
  authConfigured: boolean
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
  expectedStatusMin: number
  expectedStatusMax: number
  timeoutMs: number
  checkIntervalSeconds: number
  responseBodyContains: string
  failureThreshold: number
  active: boolean
  customHeaders: Record<string, string> | null
  authType: RequestAuthType
  authHeaderName: string
  authValue: string
  clearAuthSecret: boolean
}

export interface NotificationSettings {
  enabled: boolean
  webhookConfigured: boolean
  webhookDisplay: string | null
  cooldownSeconds: number
  updatedAt: string | null
}

export interface NotificationSettingsInput {
  enabled: boolean
  webhookUrl: string
  clearWebhook: boolean
  cooldownSeconds: number
}

export interface NotificationDelivery {
  id: number
  incidentId: number
  serviceId: number
  eventType: NotificationEventType
  status: NotificationDeliveryStatus
  httpStatusCode: number | null
  errorMessage: string | null
  attemptedAt: string
}
