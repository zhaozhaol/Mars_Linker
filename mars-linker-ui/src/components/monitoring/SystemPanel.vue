<template>
  <div class="panel system-panel">
    <div class="card-header">
      <div class="card-icon sys-icon">Sys</div>
      <span class="card-title">系统指标</span>
    </div>
    <MetricSkeleton v-if="loading" />
    <ErrorPlaceholder v-else-if="error" :message="error!" @retry="$emit('retry')" />
    <div v-else class="metrics-grid">
      <div class="metric-item" :class="heapClass">
        <div class="metric-label">堆使用率</div>
        <div class="metric-value">{{ formatPercent(data?.heapUsedRatio) }}</div>
      </div>
      <div class="metric-item" :class="cpuClass">
        <div class="metric-label">CPU 使用率</div>
        <div class="metric-value">{{ formatPercent(data?.cpuUsageRatio) }}</div>
      </div>
      <div class="metric-item">
        <div class="metric-label">线程数</div>
        <div class="metric-value">{{ data?.threadCount ?? '--' }}</div>
      </div>
      <div class="metric-item">
        <div class="metric-label">GC 次数</div>
        <div class="metric-value">{{ data?.gcCount ?? '--' }}</div>
      </div>
      <div class="metric-item">
        <div class="metric-label">GC 耗时</div>
        <div class="metric-value gc-time">{{ formatMs(data?.gcTimeMs) }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { SystemMetrics } from '../../types/monitoring'
import MetricSkeleton from './MetricSkeleton.vue'
import ErrorPlaceholder from './ErrorPlaceholder.vue'

const props = defineProps<{
  data: SystemMetrics | null
  loading: boolean
  error: string | null
}>()

defineEmits<{ retry: [] }>()

const formatPercent = (ratio: number | undefined) => {
  if (ratio === undefined || ratio === null) return '--'
  return `${(ratio * 100).toFixed(1)}%`
}

const formatMs = (ms: number | undefined) => {
  if (ms === undefined || ms === null) return '--'
  return `${ms}ms`
}

const heapClass = computed(() => {
  const r = props.data?.heapUsedRatio
  if (r === undefined || r === null) return ''
  if (r > 0.85) return 'critical'
  if (r > 0.7) return 'warning'
  return ''
})

const cpuClass = computed(() => {
  const r = props.data?.cpuUsageRatio
  if (r === undefined || r === null) return ''
  if (r > 0.85) return 'critical'
  if (r > 0.7) return 'warning'
  return ''
})
</script>

<style scoped>
.panel {
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 12px;
  padding: 20px;
  transition: border-color 0.25s;
}
.panel:hover {
  border-color: rgba(255,255,255,0.1);
}
.card-header { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.card-icon { width: 32px; height: 32px; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 11px; color: #fff; }
.sys-icon { background: linear-gradient(135deg, #f59e0b, #ef4444); }
.card-title { font-size: 14px; font-weight: 600; color: rgba(255,255,255,0.85); }
.metrics-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.metric-item {
  text-align: center;
  padding: 14px 8px;
  background: rgba(255,255,255,0.03);
  border-radius: 10px;
  transition: background 0.2s;
}
.metric-item.warning {
  background: rgba(245, 158, 11, 0.06);
  border: 1px solid rgba(245, 158, 11, 0.15);
}
.metric-item.critical {
  background: rgba(239, 68, 68, 0.06);
  border: 1px solid rgba(239, 68, 68, 0.15);
}
.metric-label { font-size: 11px; color: rgba(255,255,255,0.4); font-weight: 500; margin-bottom: 6px; text-transform: uppercase; letter-spacing: 0.5px; }
.metric-value { font-size: 22px; font-weight: 700; color: #fff; font-family: 'SF Mono', 'Fira Code', monospace; }
.metric-item.warning .metric-value { color: #f59e0b; }
.metric-item.critical .metric-value { color: #ef4444; }
.metric-value.gc-time { font-size: 18px; }
</style>
