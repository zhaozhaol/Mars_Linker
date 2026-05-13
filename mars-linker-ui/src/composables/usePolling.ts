import { ref, onUnmounted, watch } from 'vue'
import type { Ref } from 'vue'

export interface UsePollingOptions {
  interval: Ref<number> | number
  maxFailures?: number
  callback: () => Promise<void>
}

export function usePolling(options: UsePollingOptions) {
  const { callback, maxFailures = 3 } = options
  const isPaused = ref(false)
  const failureCount = ref(0)
  let timerId: ReturnType<typeof setTimeout> | null = null

  const BACKOFF_STEPS = [5000, 10000, 30000, 60000]

  const getInterval = () => {
    const val = typeof options.interval === 'number' ? options.interval : options.interval.value
    return Math.max(1000, val)
  }

  const getBackoffDelay = () => {
    if (failureCount.value === 0) return getInterval()
    const idx = Math.min(failureCount.value - 1, BACKOFF_STEPS.length - 1)
    return BACKOFF_STEPS[idx]
  }

  const execute = async () => {
    try {
      await callback()
      failureCount.value = 0
      scheduleNext(getInterval())
    } catch {
      failureCount.value++
      if (failureCount.value >= maxFailures) {
        pause()
      } else {
        scheduleNext(getBackoffDelay())
      }
    }
  }

  const scheduleNext = (delay: number) => {
    if (timerId !== null) {
      clearTimeout(timerId)
    }
    timerId = setTimeout(execute, delay)
  }

  const start = () => {
    stop()
    isPaused.value = false
    failureCount.value = 0
    execute()
  }

  const stop = () => {
    if (timerId !== null) {
      clearTimeout(timerId)
      timerId = null
    }
  }

  const pause = () => {
    isPaused.value = true
    stop()
  }

  const resume = () => {
    start()
  }

  if (typeof options.interval !== 'number') {
    watch(options.interval, () => {
      if (!isPaused.value && timerId !== null) {
        scheduleNext(getInterval())
      }
    })
  }

  onUnmounted(() => {
    stop()
  })

  return { isPaused, failureCount, start, stop, pause, resume }
}
