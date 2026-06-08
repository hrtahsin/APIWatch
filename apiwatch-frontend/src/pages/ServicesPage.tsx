import { Plus, Search } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { getServices } from '../api/client'
import { ServiceTable } from '../components/ServiceTable'
import type { MonitoredService } from '../types'

export function ServicesPage() {
  const [services, setServices] = useState<MonitoredService[]>([])
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getServices()
      .then((response) => {
        setServices(response)
        setError(null)
      })
      .catch((loadError) => {
        setError(loadError instanceof Error ? loadError.message : 'Unable to load services')
      })
      .finally(() => setLoading(false))
  }, [])

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

  return (
    <section className="panel full-panel">
      <div className="panel-header toolbar-header">
        <div>
          <span>Inventory</span>
          <h2>All monitored services</h2>
        </div>
        <Link className="primary-button" to="/services/new">
          <Plus size={17} />
          Add service
        </Link>
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
        <span>{filteredServices.length} services</span>
      </div>
      {error && <div className="notice danger">Could not load services: {error}</div>}
      {loading ? <div className="loading-panel">Loading services...</div> : <ServiceTable services={filteredServices} />}
    </section>
  )
}
