import { useCallback, useEffect, useState } from 'react'
import { getApiErrorMessage, getIncidentsPage, resolveIncident } from '../api/client'
import { useAuth } from '../auth/useAuth'
import { IncidentTable } from '../components/IncidentTable'
import { Pagination } from '../components/Pagination'
import { useAutoRefresh } from '../hooks/useAutoRefresh'
import type { Incident, IncidentStatus } from '../types'

const filters: Array<'ALL' | IncidentStatus> = ['ALL', 'ACTIVE', 'RESOLVED']
const pageSize = 10

export function IncidentsPage() {
  const { canManage } = useAuth()
  const [incidents, setIncidents] = useState<Incident[]>([])
  const [filter, setFilter] = useState<'ALL' | IncidentStatus>('ALL')
  const [page, setPage] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    const response = await getIncidentsPage(
      filter === 'ALL' ? undefined : filter,
      undefined,
      page,
      pageSize,
    )
    setIncidents(response.content)
    setTotalElements(response.totalElements)
    setTotalPages(response.totalPages)
  }, [filter, page])

  const refresh = useCallback(async (showLoading = false) => {
    if (showLoading) setLoading(true)
    await load()
      .then(() => setError(null))
      .catch((loadError) => {
        setError(getApiErrorMessage(loadError, 'Unable to load incidents'))
      })
      .finally(() => {
        if (showLoading) setLoading(false)
      })
  }, [load])

  useEffect(() => {
    void refresh(true)
  }, [refresh])

  useAutoRefresh(() => refresh(false))

  async function handleResolve(id: number) {
    try {
      await resolveIncident(id)
      await load()
      setError(null)
    } catch (resolveError) {
      setError(getApiErrorMessage(resolveError, 'Unable to resolve incident'))
    }
  }

  function handleFilterChange(nextFilter: 'ALL' | IncidentStatus) {
    setFilter(nextFilter)
    setPage(0)
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
              onClick={() => handleFilterChange(item)}
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
        <IncidentTable
          incidents={incidents}
          onResolve={canManage ? handleResolve : undefined}
        />
      )}
      <Pagination
        page={page}
        totalPages={totalPages}
        totalElements={totalElements}
        onPageChange={setPage}
      />
    </section>
  )
}
