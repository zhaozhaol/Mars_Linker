<template>
  <div class="panel message-panel">
    <div class="card-header">
      <div class="card-icon msg-icon">M</div>
      <span class="card-title">消息指标</span>
    </div>
    <MetricSkeleton v-if="loading" />
    <ErrorPlaceholder v-else-if="error" :message="error!" @retry="$emit('retry')" />
    <div v-else class="metrics-grid">
      <div class="metric-item">
        <div class="metric-label">发布入总数</div>
        <div class="metric-value">{{ data?.publishInTotal ?? '--' }}</div>
      </div>
      <div class="metric-item">
        <div class="metric-label">发布出总数</div>
        <div class="metric-value">{{ data?.publishOutTotal ?? '--' }}</div>
      </div>
      <div class="metric-item">
        <div class="metric-label">发布入速率</div>
        <div class="metric-value rate">{{ formatRate(data?.publishInRate) }}</div>
      </div>
      <div class="metric-item">
        <div class="metric-label">发布出速率</div>
        <div class="metric-value rate">{{ formatRate(data?.publishOutRate) }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { MessageMetrics } from '../../types/monitoring'
import MetricSkeleton from './MetricSkeleton.vue'
import ErrorPlaceholder from './ErrorPlaceholder.vue'

defineProps<{
  data: MessageMetrics | null
  loading: boolean
  error: string | null
}>()

defineEmits<{ retry: [] }>()

const formatRate = (rate: number | undefined) => {
  if (rate === undefined || rate === null) return '--'
  return `${rate.toFixed(1)}/s`
}
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
.card-icon { width: 32px; height: 32px; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 14px; color: #fff; }
.msg-icon { background: linear-gradient(135deg, #10b981, #06b6d4); }
.card-title { font-size: 14px; font-weight: 600; color: rgba(255,255,255,0.85); }
.metrics-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
.metric-item {
  text-align: center;
  padding: 14px 8px;
  background: rgba(255,255,255,0.03);
  border-radius: 10px;
}
.metric-label { font-size: 11px; color: rgba(255,255,255,0.4); font-weight: 500; margin-bottom: 6px; text-transform: uppercase; letter-spacing: 0.5px; }
.metric-value { font-size: 22px; font-weight: 700; color: #fff; font-family: 'SF Mono', 'Fira Code', monospace; }
.metric-value.rate { font-size: 18px; color: #10b981; }
</style>
