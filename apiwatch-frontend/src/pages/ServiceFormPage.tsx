import { Save } from 'lucide-react'
import { FormEvent, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { createService, getApiErrorMessage, getService, updateService } from '../api/client'
import { FeedbackNotice } from '../components/FeedbackNotice'
import { LoadingState } from '../components/LoadingState'
import { useToast } from '../hooks/useToast'
import type { ServiceInput } from '../types'

const defaultForm: ServiceInput = {
  name: '',
  url: '',
  ownerName: '',
  teamName: '',
  tags: [],
  method: 'GET',
  expectedStatusMin: 200,
  expectedStatusMax: 299,
  timeoutMs: 2000,
  slowThresholdMs: 2000,
  checkIntervalSeconds: 60,
  responseBodyContains: '',
  failureThreshold: 3,
  notifyOnIncidentOpen: true,
  notifyOnIncidentResolve: true,
  notificationEscalationMinutes: 0,
  active: true,
  customHeaders: null,
  authType: 'NONE',
  authHeaderName: 'X-API-Key',
  authValue: '',
  clearAuthSecret: false,
}

function parseCustomHeaders(value: string): Record<string, string> {
  const headers: Record<string, string> = {}
  value
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .forEach((line) => {
      const separator = line.indexOf(':')
      if (separator <= 0) {
        throw new Error(`Invalid custom header "${line}". Use Header-Name: value.`)
      }
      const name = line.slice(0, separator).trim()
      const headerValue = line.slice(separator + 1).trim()
      if (!headerValue) {
        throw new Error(`Custom header "${name}" requires a value.`)
      }
      headers[name] = headerValue
    })
  return headers
}

type FieldErrors = Partial<
  Record<'authValue' | 'customHeaders' | 'expectedStatus' | 'name' | 'url', string>
>

export function ServiceFormPage() {
  const { id } = useParams()
  const serviceId = id ? Number(id) : null
  const navigate = useNavigate()
  const notify = useToast()
  const [form, setForm] = useState<ServiceInput>(defaultForm)
  const [loading, setLoading] = useState(Boolean(serviceId))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [customHeaderText, setCustomHeaderText] = useState('')
  const [storedHeaderNames, setStoredHeaderNames] = useState<string[]>([])
  const [clearStoredHeaders, setClearStoredHeaders] = useState(false)

  useEffect(() => {
    if (!serviceId) return
    getService(serviceId)
      .then((service) => {
        setForm({
          name: service.name,
          url: service.url,
          ownerName: service.ownerName ?? '',
          teamName: service.teamName ?? '',
          tags: service.tags,
          method: service.method,
          expectedStatusMin: service.expectedStatusMin,
          expectedStatusMax: service.expectedStatusMax,
          timeoutMs: service.timeoutMs,
          slowThresholdMs: service.slowThresholdMs,
          checkIntervalSeconds: service.checkIntervalSeconds,
          responseBodyContains: service.responseBodyContains ?? '',
          failureThreshold: service.failureThreshold,
          notifyOnIncidentOpen: service.notifyOnIncidentOpen,
          notifyOnIncidentResolve: service.notifyOnIncidentResolve,
          notificationEscalationMinutes: service.notificationEscalationMinutes,
          active: service.active,
          customHeaders: null,
          authType: service.authType,
          authHeaderName: service.authHeaderName ?? 'X-API-Key',
          authValue: '',
          clearAuthSecret: false,
        })
        setStoredHeaderNames(service.customHeaderNames)
        setError(null)
      })
      .catch((loadError) => {
        setError(getApiErrorMessage(loadError, 'Unable to load service'))
      })
      .finally(() => setLoading(false))
  }, [serviceId])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const validationErrors: FieldErrors = {}
    if (!form.name.trim()) validationErrors.name = 'Enter a service name.'
    if (!form.url.trim()) {
      validationErrors.url = 'Enter a health check URL.'
    } else {
      try {
        const url = new URL(form.url)
        if (!['http:', 'https:'].includes(url.protocol)) {
          validationErrors.url = 'Use an HTTP or HTTPS URL.'
        }
      } catch {
        validationErrors.url = 'Enter a valid URL, including https://.'
      }
    }
    if (form.authType !== 'NONE' && !form.authValue && !serviceId) {
      validationErrors.authValue = 'Enter the authentication value.'
    }
    if (form.expectedStatusMin > form.expectedStatusMax) {
      validationErrors.expectedStatus = 'Minimum status cannot exceed the maximum.'
    }

    let customHeaders: Record<string, string> | null = null
    try {
      customHeaders = customHeaderText.trim()
        ? parseCustomHeaders(customHeaderText)
        : clearStoredHeaders
          ? {}
          : null
    } catch (headerError) {
      validationErrors.customHeaders =
        headerError instanceof Error ? headerError.message : 'Review the custom headers.'
    }

    if (Object.keys(validationErrors).length > 0) {
      setFieldErrors(validationErrors)
      setError('Review the highlighted fields and try again.')
      return
    }

    try {
      setSaving(true)
      const input = { ...form, customHeaders }
      const saved = serviceId ? await updateService(serviceId, input) : await createService(input)
      setFieldErrors({})
      notify({
        title: serviceId ? 'Service updated' : 'Service registered',
        message: `${saved.name} is ready for monitoring.`,
      })
      navigate(`/services/${saved.id}`)
    } catch (saveError) {
      setError(getApiErrorMessage(saveError, 'Unable to save service'))
    } finally {
      setSaving(false)
    }
  }

  function update<K extends keyof ServiceInput>(key: K, value: ServiceInput[K]) {
    setForm((current) => ({ ...current, [key]: value }))
  }

  function clearFieldError(key: keyof FieldErrors) {
    setFieldErrors((current) => ({ ...current, [key]: undefined }))
    setError(null)
  }

  function updateTags(value: string) {
    update(
      'tags',
      value
        .split(',')
        .map((tag) => tag.trim())
        .filter(Boolean),
    )
  }

  if (loading) return <LoadingState label="Loading service configuration" variant="panel" />

  return (
    <section className="panel form-panel">
      <div className="panel-header">
        <div>
          <span>Configuration</span>
          <h2>{serviceId ? 'Edit monitored service' : 'Register a monitored service'}</h2>
        </div>
      </div>
      {error && <FeedbackNotice tone="danger">{error}</FeedbackNotice>}
      <form className="service-form" onSubmit={handleSubmit} noValidate>
        <label>
          <span>Service name</span>
          <input
            value={form.name}
            onChange={(event) => {
              update('name', event.target.value)
              clearFieldError('name')
            }}
            placeholder="Payment Service"
            required
            aria-invalid={Boolean(fieldErrors.name)}
            aria-describedby={fieldErrors.name ? 'service-name-error' : undefined}
          />
          {fieldErrors.name && <small className="field-error" id="service-name-error">{fieldErrors.name}</small>}
        </label>

        <label>
          <span>Health check URL</span>
          <input
            value={form.url}
            onChange={(event) => {
              update('url', event.target.value)
              clearFieldError('url')
            }}
            placeholder="https://example.com/health"
            required
            type="url"
            aria-invalid={Boolean(fieldErrors.url)}
            aria-describedby={fieldErrors.url ? 'service-url-error' : undefined}
          />
          {fieldErrors.url && <small className="field-error" id="service-url-error">{fieldErrors.url}</small>}
        </label>

        <div className="form-section">
          <div>
            <span className="eyebrow">Ownership</span>
            <h3>Discovery metadata</h3>
            <p>Use these fields to search, sort, and route monitored services.</p>
          </div>
          <div className="form-grid metadata-grid">
            <label>
              <span>Owner</span>
              <input
                value={form.ownerName}
                onChange={(event) => update('ownerName', event.target.value)}
                placeholder="Finance Ops"
                maxLength={120}
              />
            </label>
            <label>
              <span>Team</span>
              <input
                value={form.teamName}
                onChange={(event) => update('teamName', event.target.value)}
                placeholder="Platform"
                maxLength={120}
              />
            </label>
            <label className="metadata-tags">
              <span>Tags</span>
              <input
                value={form.tags.join(', ')}
                onChange={(event) => updateTags(event.target.value)}
                placeholder="payments, critical"
              />
              <small>Comma-separated, up to 40 characters per tag.</small>
            </label>
          </div>
        </div>

        <div className="form-grid">
          <label>
            <span>HTTP method</span>
            <select
              value={form.method}
              onChange={(event) =>
                update('method', event.target.value as ServiceInput['method'])
              }
            >
              <option value="GET">GET</option>
              <option value="HEAD">HEAD</option>
            </select>
          </label>
          <label>
            <span>Expected status from</span>
            <input
              min={100}
              max={599}
              type="number"
              value={form.expectedStatusMin}
              onChange={(event) => {
                update('expectedStatusMin', Number(event.target.value))
                clearFieldError('expectedStatus')
              }}
              aria-invalid={Boolean(fieldErrors.expectedStatus)}
              aria-describedby={fieldErrors.expectedStatus ? 'expected-status-error' : undefined}
            />
          </label>
          <label>
            <span>Expected status through</span>
            <input
              min={100}
              max={599}
              type="number"
              value={form.expectedStatusMax}
              onChange={(event) => {
                update('expectedStatusMax', Number(event.target.value))
                clearFieldError('expectedStatus')
              }}
              aria-invalid={Boolean(fieldErrors.expectedStatus)}
              aria-describedby={fieldErrors.expectedStatus ? 'expected-status-error' : undefined}
            />
            {fieldErrors.expectedStatus && (
              <small className="field-error" id="expected-status-error">{fieldErrors.expectedStatus}</small>
            )}
          </label>
          <label>
            <span>Request timeout ms</span>
            <input
              min={100}
              max={120000}
              type="number"
              value={form.timeoutMs}
              onChange={(event) => update('timeoutMs', Number(event.target.value))}
            />
            <small>The check is marked DOWN if no response arrives within this limit.</small>
          </label>
          <label>
            <span>Slow threshold ms</span>
            <input
              min={1}
              max={120000}
              type="number"
              value={form.slowThresholdMs}
              onChange={(event) => update('slowThresholdMs', Number(event.target.value))}
            />
            <small>A successful response beyond this duration is marked SLOW.</small>
          </label>
          <label>
            <span>Check interval seconds</span>
            <input
              min={10}
              max={86400}
              type="number"
              value={form.checkIntervalSeconds}
              onChange={(event) => update('checkIntervalSeconds', Number(event.target.value))}
            />
          </label>
          <label>
            <span>Failure threshold</span>
            <input
              min={1}
              max={20}
              type="number"
              value={form.failureThreshold}
              onChange={(event) => update('failureThreshold', Number(event.target.value))}
            />
          </label>
        </div>

        <label>
          <span>Response body must contain</span>
          <input
            value={form.responseBodyContains}
            onChange={(event) => update('responseBodyContains', event.target.value)}
            placeholder={'Optional, for example: "status":"ok"'}
            maxLength={500}
          />
          <small>Leave blank to validate only the HTTP status range.</small>
          {form.method === 'HEAD' && (
            <small>HEAD checks cannot validate a response body.</small>
          )}
        </label>

        <label className="toggle-row">
          <input
            checked={form.active}
            onChange={(event) => update('active', event.target.checked)}
            type="checkbox"
          />
          <span>
            <strong>Enable scheduled monitoring</strong>
            <small>Inactive services stay registered but are skipped by the scheduler.</small>
          </span>
        </label>

        <div className="form-section">
          <div>
            <span className="eyebrow">Notifications</span>
            <h3>Incident routing rules</h3>
            <p>Control which incident lifecycle events should notify the configured provider.</p>
          </div>

          <div className="notification-rule-grid">
            <label className="toggle-row compact-toggle">
              <input
                checked={form.notifyOnIncidentOpen}
                onChange={(event) => update('notifyOnIncidentOpen', event.target.checked)}
                type="checkbox"
              />
              <span>
                <strong>Notify when incidents open</strong>
                <small>Queue a notification when this service crosses its failure threshold.</small>
              </span>
            </label>

            <label className="toggle-row compact-toggle">
              <input
                checked={form.notifyOnIncidentResolve}
                onChange={(event) => update('notifyOnIncidentResolve', event.target.checked)}
                type="checkbox"
              />
              <span>
                <strong>Notify when incidents resolve</strong>
                <small>Send a recovery notification when the service returns to UP.</small>
              </span>
            </label>

            <label>
              <span>Escalation delay minutes</span>
              <input
                min={0}
                max={10080}
                type="number"
                value={form.notificationEscalationMinutes}
                onChange={(event) =>
                  update('notificationEscalationMinutes', Number(event.target.value))
                }
              />
              <small>
                Delay open-incident notifications. If the incident resolves first, the queued
                alert is skipped.
              </small>
            </label>
          </div>
        </div>

        <div className="form-section">
          <div>
            <span className="eyebrow">Request authentication</span>
            <h3>Credentials and custom headers</h3>
            <p>Stored values are encrypted and are never returned by the API.</p>
          </div>

          <div className="form-grid credential-grid">
            <label>
              <span>Authentication type</span>
              <select
                value={form.authType}
                onChange={(event) =>
                  update('authType', event.target.value as ServiceInput['authType'])
                }
              >
                <option value="NONE">None</option>
                <option value="BEARER">Bearer token</option>
                <option value="API_KEY">API key header</option>
              </select>
            </label>

            {form.authType === 'API_KEY' && (
              <label>
                <span>API key header</span>
                <input
                  value={form.authHeaderName}
                  onChange={(event) => update('authHeaderName', event.target.value)}
                  placeholder="X-API-Key"
                />
              </label>
            )}

            {form.authType !== 'NONE' && (
              <label className="credential-secret">
                <span>{form.authType === 'BEARER' ? 'Bearer token' : 'API key value'}</span>
                <input
                  value={form.authValue}
                  onChange={(event) => {
                    update('authValue', event.target.value)
                    clearFieldError('authValue')
                  }}
                  placeholder={
                    serviceId ? 'Leave blank to keep the stored secret' : 'Enter secret value'
                  }
                  type="password"
                  aria-invalid={Boolean(fieldErrors.authValue)}
                  aria-describedby={fieldErrors.authValue ? 'auth-value-error' : undefined}
                />
                {fieldErrors.authValue && (
                  <small className="field-error" id="auth-value-error">{fieldErrors.authValue}</small>
                )}
              </label>
            )}
          </div>

          {serviceId && form.authType !== 'NONE' && (
            <label className="toggle-row compact-toggle">
              <input
                checked={form.clearAuthSecret}
                onChange={(event) => update('clearAuthSecret', event.target.checked)}
                type="checkbox"
              />
              <span>
                <strong>Remove stored authentication</strong>
                <small>This also changes authentication type to None after saving.</small>
              </span>
            </label>
          )}

          <label>
            <span>Custom headers</span>
            <textarea
              value={customHeaderText}
              onChange={(event) => {
                setCustomHeaderText(event.target.value)
                clearFieldError('customHeaders')
              }}
              placeholder={'X-Tenant-ID: customer-7\nAccept: application/json'}
              rows={4}
              aria-invalid={Boolean(fieldErrors.customHeaders)}
              aria-describedby={fieldErrors.customHeaders ? 'custom-headers-error' : undefined}
            />
            <small>Enter one header per line using `Header-Name: value`.</small>
            {fieldErrors.customHeaders && (
              <small className="field-error" id="custom-headers-error">{fieldErrors.customHeaders}</small>
            )}
          </label>

          {storedHeaderNames.length > 0 && (
            <div className="stored-secret-summary">
              <strong>Stored custom headers</strong>
              <div>
                {storedHeaderNames.map((name) => (
                  <span key={name}>{name}: ••••••••</span>
                ))}
              </div>
              <label>
                <input
                  checked={clearStoredHeaders}
                  onChange={(event) => setClearStoredHeaders(event.target.checked)}
                  type="checkbox"
                />
                Clear stored custom headers
              </label>
            </div>
          )}
        </div>

        <div className="form-actions">
          <Link className="secondary-button" to={serviceId ? `/services/${serviceId}` : '/services'}>
            Cancel
          </Link>
          <button className="primary-button" disabled={saving} type="submit">
            <Save size={17} />
            {saving ? 'Saving...' : 'Save service'}
          </button>
        </div>
      </form>
    </section>
  )
}
