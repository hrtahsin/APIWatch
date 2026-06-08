import { AlertTriangle, Gauge, RadioTower, Server, Timer, TrendingUp } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import {
  getDashboardSummary,
  getHealthChecks,
  getIncidents,
  getServices,
} from '../api/client'
import { IncidentTable } from '../components/IncidentTable'
import { LatencyChart } from '../components/LatencyChart'
import { ServiceTable } from '../components/ServiceTable'
import { SummaryCard } from '../components/SummaryCard'
import type { DashboardSummary, HealthCheck, Incident, MonitoredService } from '../types'

export function DashboardPage() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null)
  const [services, setServices] = useState<MonitoredService[]>([])
  const [activeIncidents, setActiveIncidents] = useState<Incident[]>([])
  const [checks, setChecks] = useState<HealthCheck[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        setLoading(true)
        const [summaryResponse, servicesResponse, incidentsResponse] = await Promise.all([
          getDashboardSummary(),
          getServices(),
          getIncidents('ACTIVE'),
        ])
        const firstService = servicesResponse[0]
        const checksResponse = firstService ? await getHealthChecks(firstService.id, 20) : []
        if (!cancelled) {
          setSummary(summaryResponse)
          setServices(servicesResponse)
          setActiveIncidents(incidentsResponse)
          setChecks(checksResponse)
          setError(null)
        }
      } catch (loadError) {
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : 'Unable to load dashboard')
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => {
      cancelled = true
    }
  }, [])

  const cards = useMemo(
    () =>
      summary
        ? [
            {
              label: 'Total Services',
              value: summary.totalServices,
              detail: `${summary.unknownServices} unknown`,
              icon: Server,
              tone: 'default' as const,
            },
            {
              label: 'UP Services',
              value: summary.upServices,
              detail: `${summary.overallUptimePercentage.toFixed(2)}% uptime`,
              icon: RadioTower,
              tone: 'success' as const,
            },
            {
              label: 'SLOW Services',
              value: summary.slowServices,
              detail: 'Exceeding latency target',
              icon: Timer,
              tone: 'warning' as const,
            },
            {
              label: 'DOWN Services',
              value: summary.downServices,
              detail: `${summary.activeIncidents} active incidents`,
              icon: AlertTriangle,
              tone: 'danger' as const,
            },
            {
              label: 'Average Latency',
              value: `${Math.round(summary.averageResponseTimeMs)} ms`,
              detail: 'Latest checks',
              icon: Gauge,
              tone: 'default' as const,
            },
            {
              label: 'Uptime',
              value: `${summary.overallUptimePercentage.toFixed(2)}%`,
              detail: 'UP + SLOW vs DOWN',
              icon: TrendingUp,
              tone: 'success' as const,
            },
          ]
        : [],
    [summary],
  )

  if (loading) return <div className="panel loading-panel">Loading dashboard data...</div>

  return (
    <div className="dashboard-grid">
      {error && <div className="notice danger">Could not load live data: {error}</div>}

      <section className="summary-grid">
        {cards.map((card) => (
          <SummaryCard key={card.label} {...card} />
        ))}
      </section>

      <section className="panel wide-panel">
        <div className="panel-header">
          <div>
            <span>Registered APIs</span>
            <h2>Current service health</h2>
          </div>
        </div>
        <ServiceTable services={services} compact />
      </section>

      <section className="panel">
        <div className="panel-header">
          <div>
            <span>Latency</span>
            <h2>Recent response times</h2>
          </div>
        </div>
        <LatencyChart checks={checks} />
      </section>

      <section className="panel wide-panel">
        <div className="panel-header">
          <div>
            <span>Open issues</span>
            <h2>Active incidents</h2>
          </div>
        </div>
        <IncidentTable incidents={activeIncidents} />
      </section>
    </div>
  )
}
