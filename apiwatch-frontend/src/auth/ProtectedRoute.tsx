import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './useAuth'

export function ProtectedRoute({
  children,
  admin = false,
}: {
  children: ReactNode
  admin?: boolean
}) {
  const { user, loading, canManage } = useAuth()
  const location = useLocation()
  if (loading) {
    return <div className="auth-loading">Checking access...</div>
  }
  if (!user) {
    return <Navigate replace state={{ from: location.pathname }} to="/login" />
  }
  if (admin && !canManage) {
    return <Navigate replace to="/" />
  }
  return children
}
