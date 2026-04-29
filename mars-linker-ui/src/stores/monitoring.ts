import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getMonitoringOverview } from '../api/monitoring'
import type { MonitoringOverview, BrokerMetrics } from '../types/monitoring'

export interface TrendPoint {
  time: number
  value: number
}

const MAX_TREND_POINTS = 60

export const useMonitoringStore = defineStore('monitoring', () => {
  const data = ref<MonitoringOverview | null>(null)
  const loading = ref(false)
  const failureCount = ref(0)
  const isPaused = ref(false)
  const stale = ref(false)
  const lastFetchTime = ref<number>(0)

  const connectionsTrend = ref<TrendPoint[]>([])
  const publishInTrend = ref<TrendPoint[]>([])
  const publishOutTrend = ref<TrendPoint[]>([])
  const rejectedTrend = ref<TrendPoint[]>([])

  const brokerStatus = computed(() => data.value?.broker ?? null)
  const metrics = computed(() => data.value?.metrics ?? null)
  const runtimeConfig = computed(() => data.value?.runtimeConfig ?? null)
  const collectionStats = computed(() => data.value?.collection ?? null)
  const shouldAlert = computed(() => {
    const m = data.value?.metrics
    if (!m) return false
    return m.connectRejectedTotal > 0 || m.aclSubscribeDenyTotal > 0 || m.aclPublishDenyTotal > 0
  })

  const pushTrend = (arr: typeof connectionsTrend, time: number, value: number) => {
    arr.value.push({ time, value })
    if (arr.value.length > MAX_TREND_POINTS) {
      arr.value = arr.value.slice(-MAX_TREND_POINTS)
    }
  }

  const fetchOverview = async () => {
    loading.value = true
    try {
      const result = await getMonitoringOverview()
      data.value = result
      lastFetchTime.value = result.timestamp
      failureCount.value = 0
      stale.value = false
      isPaused.value = false

      const now = result.timestamp
      const m: BrokerMetrics = result.metrics
      pushTrend(connectionsTrend, now, m.connectionsActive)
      pushTrend(publishInTrend, now, m.publishInTotal)
      pushTrend(publishOutTrend, now, m.publishOutTotal)
      pushTrend(rejectedTrend, now, m.connectRejectedTotal + m.aclSubscribeDenyTotal + m.aclPublishDenyTotal)
    } catch {
      failureCount.value++
      stale.value = true
      if (failureCount.value >= 3) {
        isPaused.value = true
      }
    } finally {
      loading.value = false
    }
  }

  return {
    data, loading, failureCount, isPaused, stale, lastFetchTime,
    brokerStatus, metrics, runtimeConfig, collectionStats, shouldAlert,
    connectionsTrend, publishInTrend, publishOutTrend, rejectedTrend,
    fetchOverview
  }
})
