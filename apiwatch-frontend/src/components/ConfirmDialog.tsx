import { AlertTriangle } from 'lucide-react'
import { useEffect, useId, useRef } from 'react'

export function ConfirmDialog({
  busy = false,
  confirmLabel,
  description,
  onCancel,
  onConfirm,
  title,
}: {
  busy?: boolean
  confirmLabel: string
  description: string
  onCancel: () => void
  onConfirm: () => void
  title: string
}) {
  const titleId = useId()
  const descriptionId = useId()
  const cancelRef = useRef<HTMLButtonElement>(null)
  const dialogRef = useRef<HTMLElement>(null)

  useEffect(() => {
    const previouslyFocused = document.activeElement as HTMLElement | null
    cancelRef.current?.focus()
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !busy) onCancel()
      if (event.key === 'Tab') {
        const controls = Array.from(
          dialogRef.current?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)') ?? [],
        )
        const first = controls[0]
        const last = controls.at(-1)
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault()
          last?.focus()
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault()
          first?.focus()
        }
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => {
      window.removeEventListener('keydown', handleKeyDown)
      previouslyFocused?.focus()
    }
  }, [busy, onCancel])

  return (
    <div className="dialog-backdrop" onMouseDown={() => !busy && onCancel()}>
      <section
        className="confirmation-dialog"
        role="alertdialog"
        aria-describedby={descriptionId}
        aria-labelledby={titleId}
        aria-modal="true"
        onMouseDown={(event) => event.stopPropagation()}
        ref={dialogRef}
      >
        <span className="confirmation-dialog-icon" aria-hidden="true">
          <AlertTriangle size={22} />
        </span>
        <div>
          <h2 id={titleId}>{title}</h2>
          <p id={descriptionId}>{description}</p>
        </div>
        <div className="confirmation-dialog-actions">
          <button
            className="secondary-button"
            disabled={busy}
            onClick={onCancel}
            ref={cancelRef}
            type="button"
          >
            Cancel
          </button>
          <button className="danger-button solid" disabled={busy} onClick={onConfirm} type="button">
            {busy ? 'Deleting...' : confirmLabel}
          </button>
        </div>
      </section>
    </div>
  )
}
