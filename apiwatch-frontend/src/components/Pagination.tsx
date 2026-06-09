import { ChevronLeft, ChevronRight } from 'lucide-react'

interface PaginationProps {
  page: number
  totalPages: number
  totalElements: number
  onPageChange: (page: number) => void
}

export function Pagination({
  page,
  totalPages,
  totalElements,
  onPageChange,
}: PaginationProps) {
  if (totalPages <= 1) return null

  return (
    <nav className="pagination" aria-label="Pagination">
      <span>
        Page {page + 1} of {totalPages} · {totalElements} total
      </span>
      <div>
        <button
          className="secondary-button"
          disabled={page === 0}
          onClick={() => onPageChange(page - 1)}
          type="button"
        >
          <ChevronLeft size={16} />
          Previous
        </button>
        <button
          className="secondary-button"
          disabled={page + 1 >= totalPages}
          onClick={() => onPageChange(page + 1)}
          type="button"
        >
          Next
          <ChevronRight size={16} />
        </button>
      </div>
    </nav>
  )
}
