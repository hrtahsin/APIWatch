import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ProtectedRoute } from './ProtectedRoute'
import { AuthContext } from './context'

describe('ProtectedRoute', () => {
  it('redirects unauthenticated users to login', () => {
    render(
      <AuthContext.Provider
        value={{
          user: null,
          loading: false,
          canManage: false,
          login: vi.fn(),
          logout: vi.fn(),
        }}
      >
        <MemoryRouter initialEntries={['/services']}>
          <Routes>
            <Route path="/login" element={<div>Login screen</div>} />
            <Route
              path="/services"
              element={
                <ProtectedRoute>
                  <div>Protected services</div>
                </ProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>,
    )

    expect(screen.getByText('Login screen')).toBeInTheDocument()
    expect(screen.queryByText('Protected services')).not.toBeInTheDocument()
  })

  it('redirects viewers away from administrator routes', () => {
    render(
      <AuthContext.Provider
        value={{
          user: { username: 'viewer', role: 'VIEWER' },
          loading: false,
          canManage: false,
          login: vi.fn(),
          logout: vi.fn(),
        }}
      >
        <MemoryRouter initialEntries={['/settings']}>
          <Routes>
            <Route path="/" element={<div>Dashboard</div>} />
            <Route
              path="/settings"
              element={
                <ProtectedRoute admin>
                  <div>Settings</div>
                </ProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>,
    )

    expect(screen.getByText('Dashboard')).toBeInTheDocument()
    expect(screen.queryByText('Settings')).not.toBeInTheDocument()
  })
})
