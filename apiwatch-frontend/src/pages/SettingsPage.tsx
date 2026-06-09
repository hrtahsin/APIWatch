import { BellRing, Save, ShieldCheck } from 'lucide-react'
import { FormEvent, useEffect, useState } from 'react'
import {
  getApiErrorMessage,
  getNotificationDeliveries,
  getNotificationSettings,
  updateNotificationSettings,
} from '../api/client'
import type {
  NotificationDelivery,
  NotificationSettings,
  NotificationSettingsInput,
} from '../types'
import { formatDate } from '../utils/format'

const defaultInput: NotificationSettingsInput = {
  enabled: false,
  webhookUrl: '',
  clearWebhook: false,
  cooldownSeconds: 300,
}

const eventLabels = {
  INCIDENT_OPENED: 'Incident opened',
  INCIDENT_RESOLVED: 'Incident resolved',
}

export function SettingsPage() {
  const [settings, setSettings] = useState<NotificationSettings | null>(null)
  const [form, setForm] = useState(defaultInput)
  const [deliveries, setDeliveries] = useState<NotificationDelivery[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([getNotificationSettings(), getNotificationDeliveries()])
      .then(([settingsResponse, deliveryResponse]) => {
        setSettings(settingsResponse)
        setForm({
          enabled: settingsResponse.enabled,
          webhookUrl: '',
          clearWebhook: false,
          cooldownSeconds: settingsResponse.cooldownSeconds,
        })
        setDeliveries(deliveryResponse)
      })
      .catch((loadError) => {
        setError(getApiErrorMessage(loadError, 'Unable to load notification settings'))
      })
      .finally(() => setLoading(false))
  }, [])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const hasWebhook = Boolean(form.webhookUrl.trim()) ||
      (settings?.webhookConfigured && !form.clearWebhook)
    if (form.enabled && !hasWebhook) {
      setError('Configure a webhook URL before enabling notifications.')
      return
    }
    try {
      setSaving(true)
      setSaved(false)
      const updated = await updateNotificationSettings({
        ...form,
        webhookUrl: form.webhookUrl.trim(),
      })
      setSettings(updated)
      setForm({
        enabled: updated.enabled,
        webhookUrl: '',
        clearWebhook: false,
        cooldownSeconds: updated.cooldownSeconds,
      })
      setSaved(true)
      setError(null)
    } catch (saveError) {
      setError(getApiErrorMessage(saveError, 'Unable to save notification settings'))
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return <div className="panel loading-panel">Loading notification settings...</div>
  }

  return (
    <div className="settings-grid">
      <section className="panel form-panel">
        <div className="panel-header">
          <div>
            <span>Notifications</span>
            <h2>Incident webhooks</h2>
          </div>
          <BellRing size={22} />
        </div>
        {error && <div className="notice danger">{error}</div>}
        {saved && <div className="notice success">Notification settings saved.</div>}
        <form className="service-form" onSubmit={handleSubmit}>
          <label className="toggle-row">
            <input
              checked={form.enabled}
              onChange={(event) =>
                setForm((current) => ({ ...current, enabled: event.target.checked }))
              }
              type="checkbox"
            />
            <span>
              <strong>Send incident lifecycle notifications</strong>
              <small>Deliver a webhook when an incident opens or resolves.</small>
            </span>
          </label>

          <label>
            <span>Webhook URL</span>
            <input
              value={form.webhookUrl}
              onChange={(event) =>
                setForm((current) => ({ ...current, webhookUrl: event.target.value }))
              }
              placeholder={
                settings?.webhookConfigured
                  ? 'Leave blank to keep the stored webhook'
                  : 'https://hooks.example.com/apiwatch'
              }
              type="password"
            />
            <small>
              Stored encrypted. API responses expose only {settings?.webhookDisplay ?? 'a masked host'}.
            </small>
          </label>

          {settings?.webhookConfigured && (
            <label className="toggle-row compact-toggle">
              <input
                checked={form.clearWebhook}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    clearWebhook: event.target.checked,
                    enabled: event.target.checked ? false : current.enabled,
                  }))
                }
                type="checkbox"
              />
              <span>
                <strong>Remove stored webhook</strong>
                <small>This disables notifications after saving.</small>
              </span>
            </label>
          )}

          <label>
            <span>Cooldown seconds</span>
            <input
              min={0}
              max={86400}
              type="number"
              value={form.cooldownSeconds}
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  cooldownSeconds: Number(event.target.value),
                }))
              }
            />
            <small>Suppress repeated notifications for the same service and event type.</small>
          </label>

          <button className="primary-button settings-save" disabled={saving} type="submit">
            <Save size={17} />
            {saving ? 'Saving...' : 'Save notification settings'}
          </button>
        </form>
      </section>

      <section className="panel">
        <div className="panel-header">
          <div>
            <span>Delivery audit</span>
            <h2>Recent webhook attempts</h2>
          </div>
          <ShieldCheck size={22} />
        </div>
        {deliveries.length === 0 ? (
          <div className="empty-state">No webhook deliveries have been attempted yet.</div>
        ) : (
          <div className="table-scroll">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Event</th>
                  <th>Status</th>
                  <th>HTTP</th>
                  <th>Attempted</th>
                </tr>
              </thead>
              <tbody>
                {deliveries.map((delivery) => (
                  <tr key={delivery.id}>
                    <td>{eventLabels[delivery.eventType]}</td>
                    <td>{delivery.status.replaceAll('_', ' ')}</td>
                    <td>{delivery.httpStatusCode ?? '—'}</td>
                    <td className="muted-cell">{formatDate(delivery.attemptedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}
