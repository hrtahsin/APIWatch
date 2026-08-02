import { BellRing, Plus, ScanSearch } from 'lucide-react'
import { Link } from 'react-router-dom'

export function OnboardingCard({ canManage }: { canManage: boolean }) {
  return (
    <section className="panel onboarding-panel">
      <div className="onboarding-copy">
        <span className="eyebrow">Get started</span>
        <h2>Build your reliability workspace</h2>
        <p>
          Register an endpoint, validate its first health check, and connect incident notifications.
        </p>
        {canManage ? (
          <Link className="primary-button" to="/services/new">
            <Plus size={17} />
            Add your first service
          </Link>
        ) : (
          <p className="onboarding-viewer-note">Ask an administrator to register the first service.</p>
        )}
      </div>
      <ol className="onboarding-steps">
        <li><Plus size={18} /><span><strong>Register</strong>Add an API health endpoint.</span></li>
        <li><ScanSearch size={18} /><span><strong>Validate</strong>Review latency and failures.</span></li>
        <li><BellRing size={18} /><span><strong>Notify</strong>Connect your incident channel.</span></li>
      </ol>
    </section>
  )
}
