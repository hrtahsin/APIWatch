import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AppLayout } from './components/AppLayout'
import { AuditLogsPage } from './pages/AuditLogsPage'
import { DashboardPage } from './pages/DashboardPage'
import { IncidentsPage } from './pages/IncidentsPage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { ServiceDetailPage } from './pages/ServiceDetailPage'
import { ServiceFormPage } from './pages/ServiceFormPage'
import { ServicesPage } from './pages/ServicesPage'
import { SettingsPage } from './pages/SettingsPage'

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: (
      <ProtectedRoute>
        <AppLayout />
      </ProtectedRoute>
    ),
    errorElement: <NotFoundPage />,
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'services', element: <ServicesPage /> },
      {
        path: 'services/new',
        element: (
          <ProtectedRoute admin>
            <ServiceFormPage />
          </ProtectedRoute>
        ),
      },
      { path: 'services/:id', element: <ServiceDetailPage /> },
      {
        path: 'services/:id/edit',
        element: (
          <ProtectedRoute admin>
            <ServiceFormPage />
          </ProtectedRoute>
        ),
      },
      { path: 'incidents', element: <IncidentsPage /> },
      {
        path: 'settings',
        element: (
          <ProtectedRoute admin>
            <SettingsPage />
          </ProtectedRoute>
        ),
      },
      {
        path: 'audit-logs',
        element: (
          <ProtectedRoute admin>
            <AuditLogsPage />
          </ProtectedRoute>
        ),
      },
    ],
  },
])

export default function App() {
  return (
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>
  )
}
