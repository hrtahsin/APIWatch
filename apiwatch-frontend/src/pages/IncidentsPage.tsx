import { useEffect, useMemo, useState } from 'react'
import { getIncidents, resolveIncident } from '../api/client'
import { IncidentTable } from '../components/IncidentTable'
import type { Incident, IncidentStatus } from '../types'

const filters: Array<'ALL' | IncidentStatus> = ['ALL', 'ACTIVE', 'RESOLVED']

export function IncidentsPage() {
  const [incidents, setIncidents] = useState<Incident[]>([])
  const [filter, setFilter] = useState<'ALL' | IncidentStatus>('ALL')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  async function load() {
    const response = await getIncidents()
    setIncidents(response)
  }

  useEffect(() => {
    load()
      .then(() => setError(null))
      .catch((loadError) => {
        setError(loadError instanceof Error ? loadError.message : 'Unable to load incidents')
      })
      .finally(() => setLoading(false))
  }, [])

  const visibleIncidents = useMemo(
    () => (filter === 'ALL' ? incidents : incidents.filter((incident) => incident.status === filter)),
    [filter, incidents],
  )

  async function handleResolve(id: number) {
    try {
      await resolveIncident(id)
      await load()
      setError(null)
    } catch (resolveError) {
      setError(resolveError instanceof Error ? resolveError.message : 'Unable to resolve incident')
    }
  }

  return (
    <section className="panel full-panel">
      <div className="panel-header toolbar-header">
        <div>
          <span>Reliability</span>
          <h2>Incident history</h2>
        </div>
        <div className="segmented-control">
          {filters.map((item) => (
            <button
              key={item}
              className={filter === item ? 'active' : ''}
              onClick={() => setFilter(item)}
              type="button"
            >
              {item}
            </button>
          ))}
        </div>
      </div>
      {error && <div className="notice danger">Could not load incidents: {error}</div>}
      {loading ? (
        <div className="loading-panel">Loading incidents...</div>
      ) : (
        <IncidentTable incidents={visibleIncidents} onResolve={handleResolve} />
      )}
    </section>
  )
}
