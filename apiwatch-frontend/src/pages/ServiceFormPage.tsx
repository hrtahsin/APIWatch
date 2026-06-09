import { Save } from 'lucide-react'
import { FormEvent, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { createService, getApiErrorMessage, getService, updateService } from '../api/client'
import type { ServiceInput } from '../types'

const defaultForm: ServiceInput = {
  name: '',
  url: '',
  method: 'GET',
  expectedStatusMin: 200,
  expectedStatusMax: 299,
  timeoutMs: 2000,
  checkIntervalSeconds: 60,
  responseBodyContains: '',
  failureThreshold: 3,
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

export function ServiceFormPage() {
  const { id } = useParams()
  const serviceId = id ? Number(id) : null
  const navigate = useNavigate()
  const [form, setForm] = useState<ServiceInput>(defaultForm)
  const [loading, setLoading] = useState(Boolean(serviceId))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
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
          method: service.method,
          expectedStatusMin: service.expectedStatusMin,
          expectedStatusMax: service.expectedStatusMax,
          timeoutMs: service.timeoutMs,
          checkIntervalSeconds: service.checkIntervalSeconds,
          responseBodyContains: service.responseBodyContains ?? '',
          failureThreshold: service.failureThreshold,
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
    if (!form.name.trim() || !form.url.trim()) {
      setError('Service name and URL are required.')
      return
    }
    try {
      setSaving(true)
      const customHeaders = customHeaderText.trim()
        ? parseCustomHeaders(customHeaderText)
        : clearStoredHeaders
          ? {}
          : null
      if (form.authType !== 'NONE' && !form.authValue && !serviceId) {
        throw new Error('Authentication value is required.')
      }
      if (form.expectedStatusMin > form.expectedStatusMax) {
        throw new Error('Expected status minimum cannot exceed the maximum.')
      }
      const input = { ...form, customHeaders }
      const saved = serviceId ? await updateService(serviceId, input) : await createService(input)
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

  if (loading) return <div className="panel loading-panel">Loading service configuration...</div>

  return (
    <section className="panel form-panel">
      <div className="panel-header">
        <div>
          <span>Configuration</span>
          <h2>{serviceId ? 'Edit monitored service' : 'Register a monitored service'}</h2>
        </div>
      </div>
      {error && <div className="notice danger">{error}</div>}
      <form className="service-form" onSubmit={handleSubmit}>
        <label>
          <span>Service name</span>
          <input
            value={form.name}
            onChange={(event) => update('name', event.target.value)}
            placeholder="Payment Service"
            required
          />
        </label>

        <label>
          <span>Health check URL</span>
          <input
            value={form.url}
            onChange={(event) => update('url', event.target.value)}
            placeholder="https://example.com/health"
            required
            type="url"
          />
        </label>

        <div className="form-grid">
          <label>
            <span>HTTP method</span>
            <select value={form.method} onChange={(event) => update('method', event.target.value as 'GET')}>
              <option value="GET">GET</option>
            </select>
          </label>
          <label>
            <span>Expected status from</span>
            <input
              min={100}
              max={599}
              type="number"
              value={form.expectedStatusMin}
              onChange={(event) => update('expectedStatusMin', Number(event.target.value))}
            />
          </label>
          <label>
            <span>Expected status through</span>
            <input
              min={100}
              max={599}
              type="number"
              value={form.expectedStatusMax}
              onChange={(event) => update('expectedStatusMax', Number(event.target.value))}
            />
          </label>
          <label>
            <span>Timeout ms</span>
            <input
              min={100}
              max={120000}
              type="number"
              value={form.timeoutMs}
              onChange={(event) => update('timeoutMs', Number(event.target.value))}
            />
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
                  onChange={(event) => update('authValue', event.target.value)}
                  placeholder={
                    serviceId ? 'Leave blank to keep the stored secret' : 'Enter secret value'
                  }
                  type="password"
                />
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
              onChange={(event) => setCustomHeaderText(event.target.value)}
              placeholder={'X-Tenant-ID: customer-7\nAccept: application/json'}
              rows={4}
            />
            <small>Enter one header per line using `Header-Name: value`.</small>
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
