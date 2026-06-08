import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <section className="panel not-found-panel">
      <span>404</span>
      <h2>Page not found</h2>
      <p>The dashboard route you requested does not exist.</p>
      <Link className="primary-button" to="/">
        Back to overview
      </Link>
    </section>
  )
}
