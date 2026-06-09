import axios from 'axios'
import type {
  DashboardSummary,
  HealthCheck,
  Incident,
  IncidentStatus,
  MonitoredService,
  NotificationDelivery,
  NotificationSettings,
  NotificationSettingsInput,
  ServiceInput,
  ServiceMetrics,
} from '../types'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api',
  timeout: 15_000,
})

export function getApiErrorMessage(error: unknown, fallback: string): string {
  if (!axios.isAxiosError(error)) {
    return error instanceof Error ? error.message : fallback
  }

  if (!error.response) {
    return `Cannot reach the APIWatch backend at ${http.defaults.baseURL}. Start the backend and refresh the page.`
  }

  const responseMessage = (error.response.data as { message?: unknown } | undefined)?.message
  return typeof responseMessage === 'string'
    ? responseMessage
    : `${fallback} (HTTP ${error.response.status})`
}

const demoMode = import.meta.env.VITE_DEMO_MODE === 'true'
const now = Date.now()

let demoNotificationSettings: NotificationSettings = {
  enabled: false,
  webhookConfigured: false,
  webhookDisplay: null,
  cooldownSeconds: 300,
  updatedAt: null,
}

const demoNotificationDeliveries: NotificationDelivery[] = []

let demoServices: MonitoredService[] = [
  {
    id: 1,
    name: 'Payments API',
    url: 'https://api.example.com/payments/health',
    method: 'GET',
    expectedStatusCode: 200,
    expectedStatusMin: 200,
    expectedStatusMax: 299,
    timeoutMs: 2000,
    checkIntervalSeconds: 60,
    responseBodyContains: null,
    failureThreshold: 3,
    active: true,
    currentStatus: 'UP',
    lastCheckedAt: new Date(now - 32_000).toISOString(),
    lastResponseTimeMs: 142,
    lastHttpStatusCode: 200,
    lastFailureType: null,
    lastErrorMessage: null,
    rateLimitedUntil: null,
    customHeaderNames: [],
    authType: 'NONE',
    authHeaderName: null,
    authConfigured: false,
    activeIncident: false,
    createdAt: new Date(now - 8_000_000).toISOString(),
    updatedAt: new Date(now - 8_000_000).toISOString(),
  },
  {
    id: 2,
    name: 'Identity Service',
    url: 'https://api.example.com/auth/health',
    method: 'GET',
    expectedStatusCode: 200,
    expectedStatusMin: 200,
    expectedStatusMax: 299,
    timeoutMs: 1500,
    checkIntervalSeconds: 30,
    responseBodyContains: null,
    failureThreshold: 3,
    active: true,
    currentStatus: 'SLOW',
    lastCheckedAt: new Date(now - 41_000).toISOString(),
    lastResponseTimeMs: 1840,
    lastHttpStatusCode: 200,
    lastFailureType: null,
    lastErrorMessage: null,
    rateLimitedUntil: null,
    customHeaderNames: [],
    authType: 'NONE',
    authHeaderName: null,
    authConfigured: false,
    activeIncident: false,
    createdAt: new Date(now - 7_000_000).toISOString(),
    updatedAt: new Date(now - 7_000_000).toISOString(),
  },
  {
    id: 3,
    name: 'Orders API',
    url: 'https://api.example.com/orders/health',
    method: 'GET',
    expectedStatusCode: 200,
    expectedStatusMin: 200,
    expectedStatusMax: 299,
    timeoutMs: 2000,
    checkIntervalSeconds: 60,
    responseBodyContains: '"status":"ok"',
    failureThreshold: 3,
    active: true,
    currentStatus: 'DOWN',
    lastCheckedAt: new Date(now - 26_000).toISOString(),
    lastResponseTimeMs: 3002,
    lastHttpStatusCode: 503,
    lastFailureType: 'HTTP_STATUS',
    lastErrorMessage: 'Expected HTTP 200 but received 503',
    rateLimitedUntil: null,
    customHeaderNames: [],
    authType: 'NONE',
    authHeaderName: null,
    authConfigured: false,
    activeIncident: true,
    createdAt: new Date(now - 6_000_000).toISOString(),
    updatedAt: new Date(now - 6_000_000).toISOString(),
  },
  {
    id: 4,
    name: 'Inventory API',
    url: 'https://api.example.com/inventory/health',
    method: 'GET',
    expectedStatusCode: 200,
    expectedStatusMin: 200,
    expectedStatusMax: 299,
    timeoutMs: 2000,
    checkIntervalSeconds: 120,
    responseBodyContains: null,
    failureThreshold: 4,
    active: true,
    currentStatus: 'UP',
    lastCheckedAt: new Date(now - 54_000).toISOString(),
    lastResponseTimeMs: 96,
    lastHttpStatusCode: 200,
    lastFailureType: null,
    lastErrorMessage: null,
    rateLimitedUntil: null,
    customHeaderNames: [],
    authType: 'NONE',
    authHeaderName: null,
    authConfigured: false,
    activeIncident: false,
    createdAt: new Date(now - 5_000_000).toISOString(),
    updatedAt: new Date(now - 5_000_000).toISOString(),
  },
]

let demoIncidents: Incident[] = [
  {
    id: 1,
    serviceId: 3,
    serviceName: 'Orders API',
    status: 'ACTIVE',
    reason: '3 consecutive health checks failed',
    startedAt: new Date(now - 18 * 60_000).toISOString(),
    resolvedAt: null,
    durationSeconds: null,
    createdAt: new Date(now - 18 * 60_000).toISOString(),
    updatedAt: new Date(now - 18 * 60_000).toISOString(),
  },
  {
    id: 2,
    serviceId: 1,
    serviceName: 'Payments API',
    status: 'RESOLVED',
    reason: '3 consecutive health checks failed',
    startedAt: new Date(now - 28 * 60 * 60_000).toISOString(),
    resolvedAt: new Date(now - 27.5 * 60 * 60_000).toISOString(),
    durationSeconds: 1800,
    createdAt: new Date(now - 28 * 60 * 60_000).toISOString(),
    updatedAt: new Date(now - 27.5 * 60 * 60_000).toISOString(),
  },
]

const latencySeries = [112, 126, 118, 151, 143, 180, 132, 121, 158, 142]

function demoChecks(serviceId: number): HealthCheck[] {
  const service = demoServices.find((item) => item.id === serviceId)
  if (!service || service.currentStatus === 'UNKNOWN') return []
  return latencySeries.map((latency, index) => {
    const isRecent = index >= latencySeries.length - 3
    const status =
      isRecent && service?.currentStatus === 'DOWN'
        ? 'DOWN'
        : isRecent && service?.currentStatus === 'SLOW'
          ? 'SLOW'
          : 'UP'
    return {
      id: serviceId * 100 + index,
      serviceId,
      status,
      httpStatusCode: status === 'DOWN' ? 503 : 200,
      responseTimeMs:
        status === 'DOWN' ? 3002 + index * 8 : status === 'SLOW' ? 1840 + index * 5 : latency,
      failureType: status === 'DOWN' ? 'HTTP_STATUS' : null,
      errorMessage: status === 'DOWN' ? 'Unexpected HTTP status 503' : null,
      retryAfterSeconds: null,
      rateLimitRemaining: null,
      rateLimitResetAt: null,
      checkedAt: new Date(now - (latencySeries.length - index) * 6 * 60_000).toISOString(),
    }
  })
}

export async function getServices(): Promise<MonitoredService[]> {
  if (demoMode) return demoServices
  return (await http.get<MonitoredService[]>('/services')).data
}

export async function getNotificationSettings(): Promise<NotificationSettings> {
  if (demoMode) return demoNotificationSettings
  return (await http.get<NotificationSettings>('/notification-settings')).data
}

export async function updateNotificationSettings(
  input: NotificationSettingsInput,
): Promise<NotificationSettings> {
  if (demoMode) {
    const webhookConfigured = input.clearWebhook
      ? false
      : Boolean(input.webhookUrl) || demoNotificationSettings.webhookConfigured
    demoNotificationSettings = {
      enabled: input.enabled && webhookConfigured,
      webhookConfigured,
      webhookDisplay: webhookConfigured ? 'https://hooks.example.com/****' : null,
      cooldownSeconds: input.cooldownSeconds,
      updatedAt: new Date().toISOString(),
    }
    return demoNotificationSettings
  }
  return (await http.put<NotificationSettings>('/notification-settings', input)).data
}

export async function getNotificationDeliveries(): Promise<NotificationDelivery[]> {
  if (demoMode) return demoNotificationDeliveries
  return (
    await http.get<NotificationDelivery[]>('/notification-settings/deliveries')
  ).data
}

export async function getService(id: number): Promise<MonitoredService> {
  if (demoMode) {
    const service = demoServices.find((item) => item.id === id)
    if (!service) throw new Error('Service not found')
    return service
  }
  return (await http.get<MonitoredService>(`/services/${id}`)).data
}

export async function createService(input: ServiceInput): Promise<MonitoredService> {
  if (demoMode) {
    const timestamp = new Date().toISOString()
    const { customHeaders, authValue, clearAuthSecret, ...publicInput } = input
    const service: MonitoredService = {
      ...publicInput,
      expectedStatusCode: input.expectedStatusMin,
      responseBodyContains: input.responseBodyContains.trim() || null,
      id: Math.max(0, ...demoServices.map((item) => item.id)) + 1,
      currentStatus: 'UNKNOWN',
      lastCheckedAt: null,
      lastResponseTimeMs: null,
      lastHttpStatusCode: null,
      lastFailureType: null,
      lastErrorMessage: null,
      rateLimitedUntil: null,
      customHeaderNames: Object.keys(customHeaders ?? {}),
      authHeaderName:
        input.authType === 'BEARER'
          ? 'Authorization'
          : input.authType === 'API_KEY'
            ? input.authHeaderName
            : null,
      authConfigured: !clearAuthSecret && input.authType !== 'NONE' && Boolean(authValue),
      activeIncident: false,
      createdAt: timestamp,
      updatedAt: timestamp,
    }
    demoServices = [...demoServices, service]
    return service
  }
  return (await http.post<MonitoredService>('/services', input)).data
}

export async function updateService(
  id: number,
  input: ServiceInput,
): Promise<MonitoredService> {
  if (demoMode) {
    const existing = await getService(id)
    const { customHeaders, authValue, clearAuthSecret, ...publicInput } = input
    const updated = {
      ...existing,
      ...publicInput,
      expectedStatusCode: input.expectedStatusMin,
      responseBodyContains: input.responseBodyContains.trim() || null,
      customHeaderNames:
        customHeaders === null ? existing.customHeaderNames : Object.keys(customHeaders),
      authHeaderName:
        clearAuthSecret || input.authType === 'NONE'
          ? null
          : input.authType === 'BEARER'
            ? 'Authorization'
            : input.authHeaderName,
      authConfigured:
        clearAuthSecret || input.authType === 'NONE'
          ? false
          : Boolean(authValue) || existing.authConfigured,
      updatedAt: new Date().toISOString(),
    }
    demoServices = demoServices.map((item) => (item.id === id ? updated : item))
    return updated
  }
  return (await http.put<MonitoredService>(`/services/${id}`, input)).data
}

export async function deleteService(id: number): Promise<void> {
  if (demoMode) {
    demoServices = demoServices.filter((item) => item.id !== id)
    return
  }
  await http.delete(`/services/${id}`)
}

export async function setServiceActive(
  id: number,
  active: boolean,
): Promise<MonitoredService> {
  if (demoMode) {
    const existing = await getService(id)
    const updated = { ...existing, active, updatedAt: new Date().toISOString() }
    demoServices = demoServices.map((service) => (service.id === id ? updated : service))
    return updated
  }
  return (await http.patch<MonitoredService>(`/services/${id}/active`, { active })).data
}

export async function runCheck(id: number): Promise<HealthCheck> {
  if (demoMode) {
    const checkedAt = new Date().toISOString()
    demoServices = demoServices.map((service) =>
      service.id === id
        ? {
            ...service,
            currentStatus: 'UP',
            lastCheckedAt: checkedAt,
            lastResponseTimeMs: 134,
            lastHttpStatusCode: 200,
            lastFailureType: null,
            lastErrorMessage: null,
            rateLimitedUntil: null,
            activeIncident: false,
          }
        : service,
    )
    demoIncidents = demoIncidents.map((incident) =>
      incident.serviceId === id && incident.status === 'ACTIVE'
        ? {
            ...incident,
            status: 'RESOLVED',
            resolvedAt: checkedAt,
            durationSeconds: Math.round(
              (new Date(checkedAt).getTime() - new Date(incident.startedAt).getTime()) / 1000,
            ),
            updatedAt: checkedAt,
          }
        : incident,
    )
    return {
      id: Date.now(),
      serviceId: id,
      status: 'UP',
      httpStatusCode: 200,
      responseTimeMs: 134,
      failureType: null,
      errorMessage: null,
      retryAfterSeconds: null,
      rateLimitRemaining: null,
      rateLimitResetAt: null,
      checkedAt,
    }
  }
  return (await http.post<HealthCheck>(`/services/${id}/check`)).data
}

export async function getHealthChecks(id: number, limit = 50): Promise<HealthCheck[]> {
  if (demoMode) return demoChecks(id).slice(-limit).reverse()
  return (
    await http.get<HealthCheck[]>(`/services/${id}/health-checks`, { params: { limit } })
  ).data
}

export async function getIncidents(
  status?: IncidentStatus,
  serviceId?: number,
): Promise<Incident[]> {
  if (demoMode) {
    return demoIncidents.filter(
      (incident) =>
        (!status || incident.status === status) &&
        (!serviceId || incident.serviceId === serviceId),
    )
  }
  return (await http.get<Incident[]>('/incidents', { params: { status, serviceId } })).data
}

export async function resolveIncident(id: number): Promise<Incident> {
  if (demoMode) {
    const resolvedAt = new Date().toISOString()
    const incident = demoIncidents.find((item) => item.id === id)
    if (!incident) throw new Error('Incident not found')
    const resolved = {
      ...incident,
      status: 'RESOLVED' as const,
      resolvedAt,
      durationSeconds: Math.round(
        (new Date(resolvedAt).getTime() - new Date(incident.startedAt).getTime()) / 1000,
      ),
      updatedAt: resolvedAt,
    }
    demoIncidents = demoIncidents.map((item) => (item.id === id ? resolved : item))
    return resolved
  }
  return (await http.patch<Incident>(`/incidents/${id}/resolve`)).data
}

export async function getDashboardSummary(): Promise<DashboardSummary> {
  if (demoMode) {
    const known = demoServices.filter(
      (service) =>
        service.currentStatus !== 'UNKNOWN' && service.currentStatus !== 'RATE_LIMITED',
    )
    const available = known.filter(
      (service) => service.currentStatus === 'UP' || service.currentStatus === 'SLOW',
    )
    const responseTimes = demoServices
      .map((service) => service.lastResponseTimeMs)
      .filter((value): value is number => value !== null)
    return {
      totalServices: demoServices.length,
      upServices: demoServices.filter((service) => service.currentStatus === 'UP').length,
      slowServices: demoServices.filter((service) => service.currentStatus === 'SLOW').length,
      downServices: demoServices.filter((service) => service.currentStatus === 'DOWN').length,
      rateLimitedServices: demoServices.filter(
        (service) => service.currentStatus === 'RATE_LIMITED',
      ).length,
      unknownServices: demoServices.filter((service) => service.currentStatus === 'UNKNOWN').length,
      activeIncidents: demoIncidents.filter((incident) => incident.status === 'ACTIVE').length,
      averageResponseTimeMs:
        responseTimes.reduce((sum, value) => sum + value, 0) / responseTimes.length,
      overallUptimePercentage: (available.length / known.length) * 100,
    }
  }
  return (await http.get<DashboardSummary>('/dashboard/summary')).data
}

export async function getServiceMetrics(id: number): Promise<ServiceMetrics> {
  if (demoMode) {
    const service = await getService(id)
    if (service.currentStatus === 'UNKNOWN') {
      return {
        serviceId: id,
        serviceName: service.name,
        windowHours: 24,
        uptimePercentage: 0,
        averageResponseTimeMs: 0,
        p95ResponseTimeMs: 0,
        totalChecks: 0,
        failedChecks: 0,
        slowChecks: 0,
        latestStatus: 'UNKNOWN',
      }
    }
    return {
      serviceId: id,
      serviceName: service.name,
      windowHours: 24,
      uptimePercentage: service.currentStatus === 'DOWN' ? 97.92 : 99.93,
      averageResponseTimeMs: service.lastResponseTimeMs ?? 0,
      p95ResponseTimeMs: (service.lastResponseTimeMs ?? 0) * 1.7,
      totalChecks: 1440,
      failedChecks: service.currentStatus === 'DOWN' ? 30 : 1,
      slowChecks: service.currentStatus === 'SLOW' ? 18 : 3,
      latestStatus: service.currentStatus,
    }
  }
  return (await http.get<ServiceMetrics>(`/services/${id}/metrics`)).data
}
