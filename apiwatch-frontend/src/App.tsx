import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import { AppLayout } from './components/AppLayout'
import { DashboardPage } from './pages/DashboardPage'
import { IncidentsPage } from './pages/IncidentsPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { ServiceDetailPage } from './pages/ServiceDetailPage'
import { ServiceFormPage } from './pages/ServiceFormPage'
import { ServicesPage } from './pages/ServicesPage'
import { SettingsPage } from './pages/SettingsPage'

const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    errorElement: <NotFoundPage />,
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'services', element: <ServicesPage /> },
      { path: 'services/new', element: <ServiceFormPage /> },
      { path: 'services/:id', element: <ServiceDetailPage /> },
      { path: 'services/:id/edit', element: <ServiceFormPage /> },
      { path: 'incidents', element: <IncidentsPage /> },
      { path: 'settings', element: <SettingsPage /> },
    ],
  },
])

export default function App() {
  return <RouterProvider router={router} />
}
