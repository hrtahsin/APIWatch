import { Save } from 'lucide-react'
import { FormEvent, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { createService, getApiErrorMessage, getService, updateService } from '../api/client'
import type { ServiceInput } from '../types'

const defaultForm: ServiceInput = {
  name: '',
  url: '',
  method: 'GET',
  expectedStatusCode: 200,
  timeoutMs: 2000,
  failureThreshold: 3,
  active: true,
}

export function ServiceFormPage() {
  const { id } = useParams()
  const serviceId = id ? Number(id) : null
  const navigate = useNavigate()
  const [form, setForm] = useState<ServiceInput>(defaultForm)
  const [loading, setLoading] = useState(Boolean(serviceId))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!serviceId) return
    getService(serviceId)
      .then((service) => {
        setForm({
          name: service.name,
          url: service.url,
          method: service.method,
          expectedStatusCode: service.expectedStatusCode,
          timeoutMs: service.timeoutMs,
          failureThreshold: service.failureThreshold,
          active: service.active,
        })
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
      const saved = serviceId ? await updateService(serviceId, form) : await createService(form)
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
            <span>Expected status</span>
            <input
              min={100}
              max={599}
              type="number"
              value={form.expectedStatusCode}
              onChange={(event) => update('expectedStatusCode', Number(event.target.value))}
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
