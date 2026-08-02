import { createContext } from 'react'

export type ToastTone = 'danger' | 'success'
export type ToastInput = { message: string; title: string; tone?: ToastTone }

export const ToastContext = createContext<((toast: ToastInput) => void) | null>(null)
