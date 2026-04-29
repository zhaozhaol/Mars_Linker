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
  let timerId: ReturnType<typeof setInterval> | null = null

  const getInterval = () => {
    const val = typeof options.interval === 'number' ? options.interval : options.interval.value
    return Math.max(1000, val)
  }

  const execute = async () => {
    try {
      await callback()
      failureCount.value = 0
    } catch {
      failureCount.value++
      if (failureCount.value >= maxFailures) {
        pause()
      }
    }
  }

  const start = () => {
    stop()
    isPaused.value = false
    failureCount.value = 0
    execute()
    timerId = setInterval(execute, getInterval())
  }

  const stop = () => {
    if (timerId !== null) {
      clearInterval(timerId)
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
        stop()
        timerId = setInterval(execute, getInterval())
      }
    })
  }

  onUnmounted(() => {
    stop()
  })

  return { isPaused, failureCount, start, stop, pause, resume }
}
