import { useEffect } from 'react'

const refreshEventName = 'apiwatch:refresh'

export function requestAppRefresh() {
  window.dispatchEvent(new Event(refreshEventName))
}

export function useAutoRefresh(callback: () => void | Promise<void>, intervalMs = 60_000) {
  useEffect(() => {
    const run = () => {
      void Promise.resolve(callback()).catch(() => undefined)
    }

    window.addEventListener(refreshEventName, run)
    const timer = window.setInterval(run, intervalMs)

    return () => {
      window.removeEventListener(refreshEventName, run)
      window.clearInterval(timer)
    }
  }, [callback, intervalMs])
}
