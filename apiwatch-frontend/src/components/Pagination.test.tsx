import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Pagination } from './Pagination'

describe('Pagination', () => {
  it('moves between pages and disables unavailable directions', () => {
    const onPageChange = vi.fn()

    const { rerender } = render(
      <Pagination
        page={0}
        totalPages={3}
        totalElements={25}
        onPageChange={onPageChange}
      />,
    )

    expect(screen.getByText('Page 1 of 3 · 25 total')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()

    fireEvent.click(screen.getByRole('button', { name: 'Next' }))
    expect(onPageChange).toHaveBeenCalledWith(1)

    rerender(
      <Pagination
        page={2}
        totalPages={3}
        totalElements={25}
        onPageChange={onPageChange}
      />,
    )
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
  })
})
