import type { ReactNode } from 'react'

export function FeedbackNotice({
  children,
  tone,
}: {
  children: ReactNode
  tone: 'danger' | 'success'
}) {
  return (
    <div
      className={`notice ${tone}`}
      role={tone === 'danger' ? 'alert' : 'status'}
      aria-live={tone === 'danger' ? 'assertive' : 'polite'}
    >
      {children}
    </div>
  )
}
