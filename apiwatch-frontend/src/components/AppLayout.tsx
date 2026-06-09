import {
  Activity,
  Bell,
  Gauge,
  Menu,
  Plus,
  RefreshCw,
  Server,
  Settings,
  X,
} from 'lucide-react'
import { useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'

const navigation = [
  { to: '/', label: 'Overview', icon: Gauge },
  { to: '/services', label: 'Services', icon: Server },
  { to: '/incidents', label: 'Incidents', icon: Bell },
]

const titles: Record<string, { title: string; eyebrow: string }> = {
  '/': { title: 'System overview', eyebrow: 'Operations center' },
  '/services': { title: 'Monitored services', eyebrow: 'Service registry' },
  '/incidents': { title: 'Incident timeline', eyebrow: 'Reliability history' },
  '/settings': { title: 'Notification settings', eyebrow: 'Integrations' },
  '/services/new': { title: 'Add a service', eyebrow: 'Service registry' },
}

export function AppLayout() {
  const [mobileOpen, setMobileOpen] = useState(false)
  const location = useLocation()
  const title = titles[location.pathname] ?? {
    title: location.pathname.includes('/edit') ? 'Edit service' : 'Service details',
    eyebrow: 'Service intelligence',
  }

  return (
    <div className="app-shell min-h-screen">
      <aside className={`sidebar ${mobileOpen ? 'sidebar-open' : ''}`}>
        <div className="brand">
          <div className="brand-mark">
            <Activity size={21} strokeWidth={2.4} />
          </div>
          <div>
            <strong>APIWatch</strong>
            <span>Reliability console</span>
          </div>
          <button className="mobile-close" onClick={() => setMobileOpen(false)} aria-label="Close menu">
            <X size={20} />
          </button>
        </div>

        <nav className="sidebar-nav" aria-label="Primary navigation">
          <span className="nav-label">Workspace</span>
          {navigation.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/'}
              onClick={() => setMobileOpen(false)}
              className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
            >
              <Icon size={18} />
              <span>{label}</span>
            </NavLink>
          ))}

          <span className="nav-label nav-label-secondary">Manage</span>
          <NavLink
            to="/services/new"
            onClick={() => setMobileOpen(false)}
            className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
          >
            <Plus size={18} />
            <span>Add service</span>
          </NavLink>
          <NavLink
            to="/settings"
            onClick={() => setMobileOpen(false)}
            className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
          >
            <Settings size={18} />
            <span>Settings</span>
          </NavLink>
        </nav>

        <div className="sidebar-status">
          <span className="live-dot" />
          <div>
            <strong>Monitoring active</strong>
            <span>Per-service schedules</span>
          </div>
        </div>
      </aside>

      {mobileOpen && <button className="sidebar-backdrop" onClick={() => setMobileOpen(false)} />}

      <main className="main-content">
        <header className="topbar">
          <div className="page-heading">
            <button className="menu-button" onClick={() => setMobileOpen(true)} aria-label="Open menu">
              <Menu size={20} />
            </button>
            <div>
              <span>{title.eyebrow}</span>
              <h1>{title.title}</h1>
            </div>
          </div>
          <div className="topbar-actions">
            <span className="refresh-meta">Auto-refreshes every 60s</span>
            <button className="icon-button" onClick={() => window.location.reload()} aria-label="Refresh data">
              <RefreshCw size={17} />
            </button>
            <NavLink className="primary-button compact" to="/services/new">
              <Plus size={17} />
              Add service
            </NavLink>
          </div>
        </header>
        <div className="page-content">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
