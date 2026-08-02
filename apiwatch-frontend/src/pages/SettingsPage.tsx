import { BellRing, RotateCcw, Save, ShieldCheck } from 'lucide-react'
import { FormEvent, useEffect, useState } from 'react'
import {
  getApiErrorMessage,
  getNotificationDeliveries,
  getNotificationSettings,
  retryNotificationDelivery,
  updateNotificationSettings,
} from '../api/client'
import type {
  NotificationDelivery,
  NotificationProvider,
  NotificationSettings,
  NotificationSettingsInput,
} from '../types'
import { formatDate } from '../utils/format'
import { EmptyState } from '../components/EmptyState'
import { FeedbackNotice } from '../components/FeedbackNotice'
import { LoadingState } from '../components/LoadingState'
import { useToast } from '../hooks/useToast'

const defaultInput: NotificationSettingsInput = {
  enabled: false,
  provider: 'WEBHOOK',
  destination: '',
  clearDestination: false,
  cooldownSeconds: 300,
  escalationMinutes: 0,
}

const eventLabels = {
  INCIDENT_OPENED: 'Incident opened',
  INCIDENT_RESOLVED: 'Incident resolved',
}

const providerLabels: Record<NotificationProvider, string> = {
  WEBHOOK: 'Generic webhook',
  SLACK: 'Slack webhook',
  DISCORD: 'Discord webhook',
  EMAIL: 'Email',
  PAGERDUTY: 'PagerDuty',
  OPSGENIE: 'Opsgenie',
}

function destinationLabel(provider: NotificationProvider): string {
  switch (provider) {
    case 'EMAIL':
      return 'Email recipient'
    case 'PAGERDUTY':
      return 'PagerDuty routing key'
    case 'OPSGENIE':
      return 'Opsgenie API key'
    case 'SLACK':
      return 'Slack webhook URL'
    case 'DISCORD':
      return 'Discord webhook URL'
    default:
      return 'Webhook URL'
  }
}

function destinationPlaceholder(provider: NotificationProvider): string {
  switch (provider) {
    case 'EMAIL':
      return 'alerts@example.com'
    case 'PAGERDUTY':
      return 'PagerDuty Events API v2 routing key'
    case 'OPSGENIE':
      return 'Opsgenie API key'
    case 'SLACK':
      return 'https://hooks.slack.com/services/...'
    case 'DISCORD':
      return 'https://discord.com/api/webhooks/...'
    default:
      return 'https://hooks.example.com/apiwatch'
  }
}

function destinationHelp(provider: NotificationProvider): string {
  switch (provider) {
    case 'EMAIL':
      return 'Requires backend SMTP configuration through Spring mail environment variables.'
    case 'PAGERDUTY':
      return 'Stored encrypted. Used as the PagerDuty Events API v2 routing key.'
    case 'OPSGENIE':
      return 'Stored encrypted. Used as the Opsgenie API key for create and close alert calls.'
    default:
      return 'Stored encrypted. URL targets are still SSRF-validated before delivery.'
  }
}

export function SettingsPage() {
  const notify = useToast()
  const [settings, setSettings] = useState<NotificationSettings | null>(null)
  const [form, setForm] = useState(defaultInput)
  const [deliveries, setDeliveries] = useState<NotificationDelivery[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [retryingDeliveryId, setRetryingDeliveryId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([getNotificationSettings(), getNotificationDeliveries()])
      .then(([settingsResponse, deliveryResponse]) => {
        setSettings(settingsResponse)
        setForm({
          enabled: settingsResponse.enabled,
          provider: settingsResponse.provider,
          destination: '',
          clearDestination: false,
          cooldownSeconds: settingsResponse.cooldownSeconds,
          escalationMinutes: settingsResponse.escalationMinutes,
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
    const hasDestination =
      Boolean(form.destination.trim()) ||
      (settings?.destinationConfigured && !form.clearDestination)
    if (form.enabled && !hasDestination) {
      setError('Configure a notification destination before enabling notifications.')
      return
    }
    try {
      setSaving(true)
      const updated = await updateNotificationSettings({
        ...form,
        destination: form.destination.trim(),
      })
      setSettings(updated)
      setForm({
        enabled: updated.enabled,
        provider: updated.provider,
        destination: '',
        clearDestination: false,
        cooldownSeconds: updated.cooldownSeconds,
        escalationMinutes: updated.escalationMinutes,
      })
      setError(null)
      setDeliveries(await getNotificationDeliveries())
      notify({
        title: 'Settings saved',
        message: 'Notification delivery preferences are up to date.',
      })
    } catch (saveError) {
      setError(getApiErrorMessage(saveError, 'Unable to save notification settings'))
    } finally {
      setSaving(false)
    }
  }

  async function handleRetry(deliveryId: number) {
    try {
      setRetryingDeliveryId(deliveryId)
      const retried = await retryNotificationDelivery(deliveryId)
      setDeliveries((current) =>
        current.map((delivery) => (delivery.id === deliveryId ? retried : delivery)),
      )
      setError(null)
      notify({
        title: 'Delivery queued',
        message: 'The failed notification has been queued for another attempt.',
      })
    } catch (retryError) {
      setError(getApiErrorMessage(retryError, 'Unable to retry notification delivery'))
    } finally {
      setRetryingDeliveryId(null)
    }
  }

  if (loading) {
    return <LoadingState label="Loading notification settings" variant="panel" />
  }

  return (
    <div className="settings-grid">
      <section className="panel form-panel">
        <div className="panel-header">
          <div>
            <span>Notifications</span>
            <h2>Incident delivery</h2>
          </div>
          <BellRing size={22} />
        </div>
        {error && <FeedbackNotice tone="danger">{error}</FeedbackNotice>}
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
              <small>Queue async notifications when incidents open or resolve.</small>
            </span>
          </label>

          <label>
            <span>Provider</span>
            <select
              value={form.provider}
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  provider: event.target.value as NotificationProvider,
                  destination: '',
                }))
              }
            >
              {Object.entries(providerLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>

          <label>
            <span>{destinationLabel(form.provider)}</span>
            <input
              value={form.destination}
              onChange={(event) =>
                setForm((current) => ({ ...current, destination: event.target.value }))
              }
              placeholder={
                settings?.destinationConfigured
                  ? 'Leave blank to keep the stored destination'
                  : destinationPlaceholder(form.provider)
              }
              type={form.provider === 'EMAIL' ? 'email' : 'password'}
            />
            <small>
              {destinationHelp(form.provider)} API responses show only{' '}
              {settings?.destinationDisplay ?? 'a masked destination'}.
            </small>
          </label>

          {settings?.destinationConfigured && (
            <label className="toggle-row compact-toggle">
              <input
                checked={form.clearDestination}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    clearDestination: event.target.checked,
                    enabled: event.target.checked ? false : current.enabled,
                  }))
                }
                type="checkbox"
              />
              <span>
                <strong>Remove stored destination</strong>
                <small>This disables notifications after saving.</small>
              </span>
            </label>
          )}

          <div className="form-grid">
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

            <label>
              <span>Global escalation delay minutes</span>
              <input
                min={0}
                max={10080}
                type="number"
                value={form.escalationMinutes}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    escalationMinutes: Number(event.target.value),
                  }))
                }
              />
              <small>Applies to incident-open notifications unless a service uses a longer delay.</small>
            </label>
          </div>

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
            <h2>Recent notification attempts</h2>
          </div>
          <ShieldCheck size={22} />
        </div>
        {deliveries.length === 0 ? (
          <EmptyState
            title="No deliveries yet"
            description="Incident notification attempts will appear here once delivery is enabled."
          />
        ) : (
          <div className="table-scroll mobile-card-table">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Provider</th>
                  <th>Event</th>
                  <th>Status</th>
                  <th>HTTP</th>
                  <th>Attempts</th>
                  <th>Next attempt</th>
                  <th>Last attempt</th>
                  <th>Queued</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {deliveries.map((delivery) => (
                  <tr key={delivery.id}>
                    <td data-label="Provider">
                      {providerLabels[delivery.provider]}
                      {delivery.destinationDisplay && (
                        <small className="table-subtext">{delivery.destinationDisplay}</small>
                      )}
                    </td>
                    <td data-label="Event">{eventLabels[delivery.eventType]}</td>
                    <td data-label="Status">{delivery.status.replaceAll('_', ' ')}</td>
                    <td data-label="HTTP">{delivery.httpStatusCode ?? '—'}</td>
                    <td data-label="Attempts">{delivery.attemptCount}</td>
                    <td className="muted-cell" data-label="Next attempt">
                      {delivery.nextAttemptAt ? formatDate(delivery.nextAttemptAt) : '—'}
                    </td>
                    <td className="muted-cell" data-label="Last attempt">
                      {delivery.lastAttemptAt ? formatDate(delivery.lastAttemptAt) : '—'}
                    </td>
                    <td className="muted-cell" data-label="Queued">{formatDate(delivery.attemptedAt)}</td>
                    <td className="table-actions-cell" data-label="Action">
                      {delivery.status === 'FAILED' ? (
                        <button
                          className="secondary-button small-button"
                          disabled={retryingDeliveryId === delivery.id}
                          onClick={() => handleRetry(delivery.id)}
                          type="button"
                        >
                          <RotateCcw size={14} />
                          {retryingDeliveryId === delivery.id ? 'Retrying...' : 'Retry'}
                        </button>
                      ) : (
                        '—'
                      )}
                    </td>
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
