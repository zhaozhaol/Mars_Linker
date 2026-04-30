import { defineStore } from 'pinia'
import { ref } from 'vue'

export const usePushStore = defineStore('push', () => {
  const connected = ref(false)
  const mode = ref<'sse' | 'polling'>('sse')
  const reconnectCount = ref(0)

  function setConnected(val: boolean) { connected.value = val }
  function setMode(val: typeof mode.value) { mode.value = val }
  function incrementReconnect() { reconnectCount.value++ }
  function resetReconnect() { reconnectCount.value = 0 }

  return { connected, mode, reconnectCount, setConnected, setMode, incrementReconnect, resetReconnect }
})
