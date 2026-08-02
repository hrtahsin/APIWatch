import { fireEvent, render, screen } from '@testing-library/react'
import { useEffect } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { ConfirmDialog } from './ConfirmDialog'
import { FeedbackNotice } from './FeedbackNotice'
import { ToastProvider } from './ToastProvider'
import { useToast } from '../hooks/useToast'

function ToastTrigger() {
  const notify = useToast()
  useEffect(() => {
    notify({ title: 'Service updated', message: 'Monitoring is active.' })
  }, [notify])
  return null
}

describe('frontend feedback', () => {
  it('announces errors assertively', () => {
    render(<FeedbackNotice tone="danger">Unable to save.</FeedbackNotice>)

    expect(screen.getByRole('alert')).toHaveTextContent('Unable to save.')
    expect(screen.getByRole('alert')).toHaveAttribute('aria-live', 'assertive')
  })

  it('publishes dismissible success notifications', () => {
    render(
      <ToastProvider>
        <ToastTrigger />
      </ToastProvider>,
    )

    expect(screen.getByRole('status')).toHaveTextContent('Service updated')
    fireEvent.click(screen.getByRole('button', { name: 'Dismiss notification' }))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('focuses the safe action and supports Escape in confirmations', () => {
    const onCancel = vi.fn()
    render(
      <ConfirmDialog
        confirmLabel="Delete permanently"
        description="This cannot be undone."
        onCancel={onCancel}
        onConfirm={vi.fn()}
        title="Delete service?"
      />,
    )

    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus()
    fireEvent.keyDown(window, { key: 'Escape' })
    expect(onCancel).toHaveBeenCalledOnce()
  })
})
