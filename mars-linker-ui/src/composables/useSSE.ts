import { ref, onUnmounted } from 'vue'
import type { PushMessage } from '../types/push'

export function useSSE(url: string) {
  const connected = ref(false)
  const error = ref<Event | null>(null)
  let eventSource: EventSource | null = null

  function connect(onMessage: (msg: PushMessage) => void) {
    disconnect()
    eventSource = new EventSource(url)
    eventSource.onopen = () => { connected.value = true; error.value = null }

    const handleEvent = (e: MessageEvent) => {
      try {
        onMessage(JSON.parse(e.data))
      } catch { /* ignore parse errors */ }
    }

    eventSource.addEventListener('metrics_update', handleEvent)
    eventSource.addEventListener('alert_event', handleEvent)
    eventSource.addEventListener('config_changed', handleEvent)
    eventSource.onmessage = handleEvent

    eventSource.onerror = (e) => { connected.value = false; error.value = e }
  }

  function disconnect() {
    if (eventSource) { eventSource.close(); eventSource = null }
    connected.value = false
  }

  onUnmounted(disconnect)
  return { connected, error, connect, disconnect }
}
