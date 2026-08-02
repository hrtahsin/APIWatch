import { CheckCircle2, CircleAlert, X } from 'lucide-react'
import {
  useCallback,
  useMemo,
  useRef,
  useState,
} from 'react'
import type { ReactNode } from 'react'
import { ToastContext } from './toast-context'
import type { ToastInput, ToastTone } from './toast-context'

type Toast = ToastInput & { id: number; tone: ToastTone }

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const nextId = useRef(0)

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id))
  }, [])

  const notify = useCallback((input: ToastInput) => {
    nextId.current += 1
    const toast: Toast = { ...input, id: nextId.current, tone: input.tone ?? 'success' }
    setToasts((current) => [...current, toast])
    window.setTimeout(() => dismiss(toast.id), 4500)
  }, [dismiss])

  const contextValue = useMemo(() => notify, [notify])

  return (
    <ToastContext.Provider value={contextValue}>
      {children}
      <div className="toast-region" aria-label="Notifications" role="region">
        {toasts.map((toast) => {
          const Icon = toast.tone === 'success' ? CheckCircle2 : CircleAlert
          return (
            <div
              className={`toast toast-${toast.tone}`}
              key={toast.id}
              role={toast.tone === 'danger' ? 'alert' : 'status'}
              aria-live={toast.tone === 'danger' ? 'assertive' : 'polite'}
            >
              <Icon size={20} aria-hidden="true" />
              <div>
                <strong>{toast.title}</strong>
                <span>{toast.message}</span>
              </div>
              <button onClick={() => dismiss(toast.id)} aria-label="Dismiss notification" type="button">
                <X size={16} />
              </button>
            </div>
          )
        })}
      </div>
    </ToastContext.Provider>
  )
}
