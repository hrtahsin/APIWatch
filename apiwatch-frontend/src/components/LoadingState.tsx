export function LoadingState({
  label,
  variant = 'panel',
}: {
  label: string
  variant?: 'cards' | 'panel' | 'table'
}) {
  const itemCount = variant === 'cards' ? 6 : variant === 'table' ? 5 : 3

  return (
    <div className={`loading-state loading-state-${variant}`} role="status" aria-live="polite">
      <span className="sr-only">{label}</span>
      <div className="skeleton-stack" aria-hidden="true">
        {Array.from({ length: itemCount }, (_, index) => (
          <span className="skeleton-line" key={index} />
        ))}
      </div>
    </div>
  )
}
