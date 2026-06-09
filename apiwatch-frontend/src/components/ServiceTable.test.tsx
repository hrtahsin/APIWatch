import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import type { MonitoredService } from '../types'
import { ServiceTable } from './ServiceTable'

const service: MonitoredService = {
  id: 1,
  name: 'Payments API',
  url: 'https://api.example.com/health',
  method: 'GET',
  expectedStatusCode: 200,
  expectedStatusMin: 200,
  expectedStatusMax: 299,
  timeoutMs: 2000,
  checkIntervalSeconds: 60,
  responseBodyContains: null,
  failureThreshold: 3,
  active: true,
  currentStatus: 'UP',
  lastCheckedAt: '2026-06-09T00:00:00Z',
  lastResponseTimeMs: 120,
  lastHttpStatusCode: 200,
  lastFailureType: null,
  lastErrorMessage: null,
  rateLimitedUntil: null,
  customHeaderNames: [],
  authType: 'NONE',
  authHeaderName: null,
  authConfigured: false,
  activeIncident: false,
  createdAt: '2026-06-08T00:00:00Z',
  updatedAt: '2026-06-08T00:00:00Z',
}

describe('ServiceTable', () => {
  it('hides mutation controls from read-only viewers', () => {
    render(
      <MemoryRouter>
        <ServiceTable services={[service]} canManage={false} />
      </MemoryRouter>,
    )

    expect(screen.queryByRole('button', { name: 'Pause Payments API' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Edit Payments API' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'View Payments API' })).toBeInTheDocument()
  })

  it('invokes pause control for administrators', async () => {
    const onActiveChange = vi.fn()
    render(
      <MemoryRouter>
        <ServiceTable
          services={[service]}
          canManage
          onActiveChange={onActiveChange}
        />
      </MemoryRouter>,
    )

    screen.getByRole('button', { name: 'Pause Payments API' }).click()

    expect(onActiveChange).toHaveBeenCalledWith(service)
  })
})
