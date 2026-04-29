import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getSystemHealth } from '../api/system'
import type { SystemHealth } from '../types/system'

export const useSystemStore = defineStore('system', () => {
  const data = ref<SystemHealth | null>(null)
  const loading = ref(false)
  const failureCount = ref(0)

  const heap = computed(() => data.value?.heap ?? null)
  const gc = computed(() => data.value?.gc ?? null)
  const threads = computed(() => data.value?.threads ?? null)
  const cpu = computed(() => data.value?.cpu ?? null)
  const disk = computed(() => data.value?.disk ?? null)

  const heapWarning = computed(() => {
    const h = data.value?.heap
    if (!h) return false
    return h.usagePercent > 80
  })

  const cpuWarning = computed(() => {
    const c = data.value?.cpu
    if (!c) return false
    return c.processCpuPercent > 80 || c.systemCpuPercent > 80
  })

  const fetchHealth = async () => {
    loading.value = true
    try {
      data.value = await getSystemHealth()
      failureCount.value = 0
    } catch {
      failureCount.value++
    } finally {
      loading.value = false
    }
  }

  return {
    data, loading, failureCount,
    heap, gc, threads, cpu, disk,
    heapWarning, cpuWarning,
    fetchHealth
  }
})
