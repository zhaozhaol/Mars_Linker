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

  function connect(onMessage: (msg: any) => void) {
    mode.value = 'sse'
    sse.connect((msg) => {
      connected.value = true
      onMessage(msg)
    })

    setTimeout(() => {
      if (!sse.connected.value) {
        mode.value = 'polling'
        connected.value = false
        sse.disconnect()
        startPolling(onMessage)
      }
    }, 3000)
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

  function disconnect() {
    sse.disconnect()
    if (pollingCleanup) { pollingCleanup(); pollingCleanup = null }
    connected.value = false
  }

  onUnmounted(disconnect)
  return { mode, connected, connect, disconnect }
}
