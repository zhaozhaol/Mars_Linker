<template>
  <div class="panel connection-panel">
    <div class="card-header">
      <div class="card-icon conn-icon">C</div>
      <span class="card-title">连接指标</span>
    </div>
    <MetricSkeleton v-if="loading" />
    <ErrorPlaceholder v-else-if="error" :message="error!" @retry="$emit('retry')" />
    <div v-else class="metrics-grid">
      <div class="metric-item">
        <div class="metric-label">活跃连接</div>
        <div class="metric-value">{{ data?.connectionsActive ?? '--' }}</div>
      </div>
      <div class="metric-item">
        <div class="metric-label">峰值连接</div>
        <div class="metric-value">{{ data?.connectionsPeakTotal ?? '--' }}</div>
      </div>
      <div class="metric-item">
        <div class="metric-label">接受总数</div>
        <div class="metric-value">{{ data?.connectAcceptedTotal ?? '--' }}</div>
      </div>
      <div class="metric-item" :class="{ alert: (data?.connectRejectedTotal ?? 0) > 0 }">
        <div class="metric-label">拒绝总数</div>
        <div class="metric-value">{{ data?.connectRejectedTotal ?? '--' }}</div>
        <div v-if="(data?.connectRejectedTotal ?? 0) > 0" class="alert-badge">ALERT</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ConnectionMetrics } from '../../types/monitoring'
import MetricSkeleton from './MetricSkeleton.vue'
import ErrorPlaceholder from './ErrorPlaceholder.vue'

defineProps<{
  data: ConnectionMetrics | null
  loading: boolean
  error: string | null
}>()

defineEmits<{ retry: [] }>()
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
.conn-icon { background: linear-gradient(135deg, #4d6dff, #0ea5e9); }
.card-title { font-size: 14px; font-weight: 600; color: rgba(255,255,255,0.85); }
.metrics-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
.metric-item {
  text-align: center;
  padding: 14px 8px;
  background: rgba(255,255,255,0.03);
  border-radius: 10px;
  position: relative;
  transition: background 0.2s;
}
.metric-item.alert {
  background: rgba(239, 68, 68, 0.06);
  border: 1px solid rgba(239, 68, 68, 0.15);
}
.metric-label { font-size: 11px; color: rgba(255,255,255,0.4); font-weight: 500; margin-bottom: 6px; text-transform: uppercase; letter-spacing: 0.5px; }
.metric-value { font-size: 22px; font-weight: 700; color: #fff; font-family: 'SF Mono', 'Fira Code', monospace; }
.metric-item.alert .metric-value { color: #ef4444; }
.alert-badge {
  position: absolute; top: 6px; right: 6px;
  font-size: 9px; font-weight: 700; color: #ef4444;
  background: rgba(239, 68, 68, 0.1);
  padding: 1px 5px; border-radius: 4px; letter-spacing: 0.5px;
}
</style>
