import { ref, onUnmounted } from 'vue'
import { useSSE } from './useSSE'
import { usePolling } from './usePolling'
import { getMonitoringOverview } from '../api/monitoring'

export type PushMode = 'sse' | 'polling'

export function usePushFallback(categories: string[] = ['all']) {
  const mode = ref<PushMode>('sse')
  const connected = ref(false)
  const categoriesParam = categories.join(',')
  const sseUrl = `/api/ui/monitoring/sse?categories=${categoriesParam}`

  const sse = useSSE(sseUrl)

  let pollingCleanup: (() => void) | null = null
  let sseRetryTimer: ReturnType<typeof setInterval> | null = null
  let currentOnMessage: ((msg: any) => void) | null = null

  function connect(onMessage: (msg: any) => void) {
    currentOnMessage = onMessage
    mode.value = 'sse'
    sse.connect((msg) => {
      connected.value = true
      onMessage(msg)
    })

    setTimeout(() => {
      if (!sse.connected.value) {
        fallbackToPolling(onMessage)
      }
    }, 3000)
  }

  function fallbackToPolling(onMessage: (msg: any) => void) {
    mode.value = 'polling'
    connected.value = false
    sse.disconnect()
    startPolling(onMessage)
    startSseRetry()
  }

  function startPolling(onMessage: (msg: any) => void) {
    const { start, stop } = usePolling({
      interval: 5000,
      callback: async () => {
        try {
          const overview = await getMonitoringOverview()
          connected.value = true
          onMessage({ type: 'metrics_update', category: 'all', timestamp: overview.timestamp, payload: overview })
        } catch {
          connected.value = false
        }
      }
    })
    start()
    pollingCleanup = stop
  }

  function startSseRetry() {
    if (sseRetryTimer !== null) return
    sseRetryTimer = setInterval(() => {
      if (mode.value !== 'polling') return
      try {
        const testSse = new EventSource(sseUrl)
        testSse.onopen = () => {
          testSse.close()
          if (pollingCleanup) { pollingCleanup(); pollingCleanup = null }
          if (sseRetryTimer !== null) { clearInterval(sseRetryTimer); sseRetryTimer = null }
          if (currentOnMessage) {
            mode.value = 'sse'
            sse.connect(currentOnMessage)
          }
        }
        testSse.onerror = () => { testSse.close() }
      } catch { /* ignore */ }
    }, 30000)
  }

  function disconnect() {
    sse.disconnect()
    if (pollingCleanup) { pollingCleanup(); pollingCleanup = null }
    if (sseRetryTimer !== null) { clearInterval(sseRetryTimer); sseRetryTimer = null }
    connected.value = false
  }

  onUnmounted(disconnect)
  return { mode, connected, connect, disconnect }
}
