import { Activity, LockKeyhole, LogIn } from 'lucide-react'
import { FormEvent, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { getApiErrorMessage } from '../api/client'
import { useAuth } from '../auth/useAuth'

export function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (user) {
    return <Navigate replace to="/" />
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    try {
      setSubmitting(true)
      await login(username.trim(), password)
      const destination =
        (location.state as { from?: string } | null)?.from ?? '/'
      navigate(destination, { replace: true })
    } catch (loginError) {
      const message = getApiErrorMessage(loginError, 'Invalid username or password')
      setError(
        message === 'Authentication is required'
          ? 'Invalid username or password.'
          : message,
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="login-shell">
      <section className="login-panel">
        <div className="login-brand">
          <span><Activity size={24} /></span>
          <div>
            <strong>APIWatch</strong>
            <small>Reliability console</small>
          </div>
        </div>
        <div className="login-heading">
          <LockKeyhole size={28} />
          <h1>Sign in</h1>
          <p>Use an administrator or read-only viewer account.</p>
        </div>
        {error && <div className="notice danger">{error}</div>}
        <form className="login-form" onSubmit={handleSubmit}>
          <label>
            <span>Username</span>
            <input
              autoComplete="username"
              autoFocus
              onChange={(event) => setUsername(event.target.value)}
              required
              value={username}
            />
          </label>
          <label>
            <span>Password</span>
            <input
              autoComplete="current-password"
              onChange={(event) => setPassword(event.target.value)}
              required
              type="password"
              value={password}
            />
          </label>
          <button className="primary-button" disabled={submitting} type="submit">
            <LogIn size={17} />
            {submitting ? 'Signing in...' : 'Sign in'}
          </button>
        </form>
      </section>
    </main>
  )
}
