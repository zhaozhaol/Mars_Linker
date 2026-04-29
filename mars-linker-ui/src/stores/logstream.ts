import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { LogEntry } from '../types/logstream'

const MAX_LOGS = 500

export const useLogStreamStore = defineStore('logstream', () => {
  const logs = ref<LogEntry[]>([])
  const connected = ref(false)
  const paused = ref(false)
  const levelFilter = ref<string>('ALL')

  const filteredLogs = computed(() => {
    if (levelFilter.value === 'ALL') return logs.value
    return logs.value.filter(l => l.level === levelFilter.value)
  })

  const stats = computed(() => {
    let error = 0, warn = 0, info = 0, other = 0
    for (const l of logs.value) {
      if (l.level === 'ERROR') error++
      else if (l.level === 'WARN') warn++
      else if (l.level === 'INFO') info++
      else other++
    }
    return { error, warn, info, other, total: logs.value.length }
  })

  const pushLog = (entry: LogEntry) => {
    if (paused.value) return
    logs.value.push(entry)
    if (logs.value.length > MAX_LOGS) {
      logs.value = logs.value.slice(-MAX_LOGS)
    }
  }

  const clearLogs = () => {
    logs.value = []
  }

  return {
    logs, connected, paused, levelFilter,
    filteredLogs, stats,
    pushLog, clearLogs
  }
})
