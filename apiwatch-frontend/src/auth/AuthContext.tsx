import { useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import {
  clearApiCredentials,
  getCurrentUser,
  hasApiCredentials,
  setApiCredentials,
} from '../api/client'
import type { AuthUser } from '../types'
import { AuthContext } from './context'
import type { AuthContextValue } from './context'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!hasApiCredentials()) {
      setLoading(false)
      return
    }
    getCurrentUser()
      .then(setUser)
      .catch(() => clearApiCredentials())
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    function handleUnauthorized() {
      clearApiCredentials()
      setUser(null)
    }
    window.addEventListener('apiwatch:unauthorized', handleUnauthorized)
    return () => window.removeEventListener('apiwatch:unauthorized', handleUnauthorized)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      loading,
      canManage: user?.role === 'ADMIN',
      login: async (username, password) => {
        setApiCredentials(username, password)
        try {
          setUser(await getCurrentUser())
        } catch (error) {
          clearApiCredentials()
          setUser(null)
          throw error
        }
      },
      logout: () => {
        clearApiCredentials()
        setUser(null)
      },
    }),
    [loading, user],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
