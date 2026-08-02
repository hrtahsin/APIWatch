import { Plus, Search } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getApiErrorMessage, getServicesPage, setServiceActive } from '../api/client'
import { useAuth } from '../auth/useAuth'
import { Pagination } from '../components/Pagination'
import { ServiceTable } from '../components/ServiceTable'
import { FeedbackNotice } from '../components/FeedbackNotice'
import { LoadingState } from '../components/LoadingState'
import { useToast } from '../hooks/useToast'
import { useAutoRefresh } from '../hooks/useAutoRefresh'
import type { MonitoredService } from '../types'
import type { ServiceListSort } from '../api/client'

const pageSize = 10
type ActiveFilter = 'ALL' | 'ACTIVE' | 'PAUSED'

export function ServicesPage() {
  const { canManage } = useAuth()
  const notify = useToast()
  const [services, setServices] = useState<MonitoredService[]>([])
  const [query, setQuery] = useState('')
  const [activeFilter, setActiveFilter] = useState<ActiveFilter>('ALL')
  const [sortBy, setSortBy] = useState<ServiceListSort>('name')
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc')
  const [page, setPage] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [updatingServiceId, setUpdatingServiceId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    const active = activeFilter === 'ALL' ? undefined : activeFilter === 'ACTIVE'
    await getServicesPage(page, pageSize, {
      query,
      active,
      sort: sortBy,
      direction: sortDirection,
    })
      .then((response) => {
        setServices(response.content)
        setTotalElements(response.totalElements)
        setTotalPages(response.totalPages)
        setError(null)
      })
      .catch((loadError) => {
        setError(getApiErrorMessage(loadError, 'Unable to load services'))
      })
  }, [activeFilter, page, query, sortBy, sortDirection])

  useEffect(() => {
    void load().finally(() => setLoading(false))
  }, [load])

  useAutoRefresh(load)

  async function handleActiveChange(service: MonitoredService) {
    try {
      setUpdatingServiceId(service.id)
      const updated = await setServiceActive(service.id, !service.active)
      setServices((current) =>
        current.map((item) => (item.id === updated.id ? updated : item)),
      )
      setError(null)
      notify({
        title: updated.active ? 'Monitoring resumed' : 'Monitoring paused',
        message: `${updated.name} was updated successfully.`,
      })
      void load()
    } catch (updateError) {
      setError(getApiErrorMessage(updateError, 'Unable to update monitoring state'))
    } finally {
      setUpdatingServiceId(null)
    }
  }

  function handleQueryChange(value: string) {
    setQuery(value)
    setPage(0)
  }

  function handleActiveFilterChange(value: ActiveFilter) {
    setActiveFilter(value)
    setPage(0)
  }

  function handleSortChange(value: ServiceListSort) {
    setSortBy(value)
    setPage(0)
  }

  function handleDirectionChange(value: 'asc' | 'desc') {
    setSortDirection(value)
    setPage(0)
  }

  return (
    <section className="panel full-panel">
      <div className="panel-header toolbar-header">
        <div>
          <span>Inventory</span>
          <h2>All monitored services</h2>
        </div>
        {canManage && (
          <Link className="primary-button" to="/services/new">
            <Plus size={17} />
            Add service
          </Link>
        )}
      </div>
      <div className="table-toolbar">
        <label className="search-box">
          <Search size={17} />
          <input
            value={query}
            onChange={(event) => handleQueryChange(event.target.value)}
            placeholder="Search name, URL, owner, team, or tag"
          />
        </label>
        <div className="toolbar-controls">
          <select
            aria-label="Monitoring state filter"
            value={activeFilter}
            onChange={(event) => handleActiveFilterChange(event.target.value as ActiveFilter)}
          >
            <option value="ALL">All states</option>
            <option value="ACTIVE">Active</option>
            <option value="PAUSED">Paused</option>
          </select>
          <select
            aria-label="Sort services by"
            value={sortBy}
            onChange={(event) => handleSortChange(event.target.value as ServiceListSort)}
          >
            <option value="name">Name</option>
            <option value="owner">Owner</option>
            <option value="team">Team</option>
            <option value="created">Created</option>
            <option value="updated">Updated</option>
          </select>
          <select
            aria-label="Sort direction"
            value={sortDirection}
            onChange={(event) => handleDirectionChange(event.target.value as 'asc' | 'desc')}
          >
            <option value="asc">Ascending</option>
            <option value="desc">Descending</option>
          </select>
          <span>{totalElements} services</span>
        </div>
      </div>
      {error && <FeedbackNotice tone="danger">{error}</FeedbackNotice>}
      {loading ? (
        <LoadingState label="Loading services" variant="table" />
      ) : (
        <ServiceTable
          services={services}
          canManage={canManage}
          emptyTitle={query || activeFilter !== 'ALL' ? 'No matching services' : 'No services yet'}
          emptyDescription={
            query || activeFilter !== 'ALL'
              ? 'Adjust the search or monitoring-state filter to broaden this view.'
              : 'Register an API endpoint to start collecting uptime and latency data.'
          }
          onActiveChange={canManage ? handleActiveChange : undefined}
          updatingServiceId={updatingServiceId}
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
