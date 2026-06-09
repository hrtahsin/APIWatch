import { Plus, Search } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { getApiErrorMessage, getServicesPage, setServiceActive } from '../api/client'
import { useAuth } from '../auth/useAuth'
import { Pagination } from '../components/Pagination'
import { ServiceTable } from '../components/ServiceTable'
import type { MonitoredService } from '../types'

const pageSize = 10

export function ServicesPage() {
  const { canManage } = useAuth()
  const [services, setServices] = useState<MonitoredService[]>([])
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [updatingServiceId, setUpdatingServiceId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    getServicesPage(page, pageSize)
      .then((response) => {
        setServices(response.content)
        setTotalElements(response.totalElements)
        setTotalPages(response.totalPages)
        setError(null)
      })
      .catch((loadError) => {
        setError(getApiErrorMessage(loadError, 'Unable to load services'))
      })
      .finally(() => setLoading(false))
  }, [page])

  const filteredServices = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    if (!normalized) return services
    return services.filter(
      (service) =>
        service.name.toLowerCase().includes(normalized) ||
        service.url.toLowerCase().includes(normalized) ||
        service.currentStatus.toLowerCase().includes(normalized),
    )
  }, [query, services])

  async function handleActiveChange(service: MonitoredService) {
    try {
      setUpdatingServiceId(service.id)
      const updated = await setServiceActive(service.id, !service.active)
      setServices((current) =>
        current.map((item) => (item.id === updated.id ? updated : item)),
      )
      setError(null)
    } catch (updateError) {
      setError(getApiErrorMessage(updateError, 'Unable to update monitoring state'))
    } finally {
      setUpdatingServiceId(null)
    }
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
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search by name, URL, or status"
          />
        </label>
        <span>
          {query ? `${filteredServices.length} matching this page` : `${totalElements} services`}
        </span>
      </div>
      {error && <div className="notice danger">{error}</div>}
      {loading ? (
        <div className="loading-panel">Loading services...</div>
      ) : (
        <ServiceTable
          services={filteredServices}
          canManage={canManage}
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
