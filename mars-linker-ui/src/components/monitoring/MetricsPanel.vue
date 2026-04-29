<template>
  <div class="glass-card metrics-panel">
    <div class="card-header">
      <div class="card-icon metrics-icon">M</div>
      <span class="card-title">核心指标</span>
    </div>
    <div class="metrics-grid">
      <div v-for="item in metricItems" :key="item.key" class="metric-item" :class="{ alert: item.alert }">
        <div class="metric-label">{{ item.label }}</div>
        <div class="metric-value">
          {{ formatMetricValue(item.value) }}
        </div>
        <div v-if="item.alert" class="alert-badge">ALERT</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { BrokerMetrics } from '../../types/monitoring'
import { formatMetricValue } from '../../utils/formatter'

const props = defineProps<{ metrics: BrokerMetrics | null }>()

const metricItems = computed(() => {
  const m = props.metrics
  return [
    { key: 'connectionsActive', label: '活跃连接', value: m?.connectionsActive, alert: false },
    { key: 'connectAcceptedTotal', label: '接受总数', value: m?.connectAcceptedTotal, alert: false },
    { key: 'connectRejectedTotal', label: '拒绝总数', value: m?.connectRejectedTotal, alert: (m?.connectRejectedTotal ?? 0) > 0 },
    { key: 'publishInTotal', label: '发布入', value: m?.publishInTotal, alert: false },
    { key: 'publishOutTotal', label: '发布出', value: m?.publishOutTotal, alert: false },
    { key: 'qos2InPending', label: 'QoS2 待完成', value: m?.qos2InPending, alert: false },
    { key: 'qos2InCompletedTotal', label: 'QoS2 完成', value: m?.qos2InCompletedTotal, alert: false },
    { key: 'aclSubscribeDenyTotal', label: 'ACL 订阅拒绝', value: m?.aclSubscribeDenyTotal, alert: (m?.aclSubscribeDenyTotal ?? 0) > 0 },
    { key: 'aclPublishDenyTotal', label: 'ACL 发布拒绝', value: m?.aclPublishDenyTotal, alert: (m?.aclPublishDenyTotal ?? 0) > 0 }
  ]
})
</script>

<style scoped>
.glass-card {
  background: rgba(255,255,255,0.85);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255,255,255,0.6);
  border-radius: 14px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.04);
  transition: box-shadow 0.25s, transform 0.25s;
}
.glass-card:hover {
  box-shadow: 0 6px 24px rgba(0,0,0,0.08);
  transform: translateY(-2px);
}
.card-header { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.card-icon { width: 32px; height: 32px; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 14px; color: #fff; }
.metrics-icon { background: linear-gradient(135deg, #0ea5e9, #06b6d4); }
.card-title { font-size: 15px; font-weight: 600; color: #1a1a2e; }
.metrics-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.metric-item {
  text-align: center;
  padding: 14px 8px;
  background: #f8f8fc;
  border-radius: 10px;
  position: relative;
  transition: background 0.2s;
}
.metric-item.alert {
  background: rgba(239, 68, 68, 0.06);
  border: 1px solid rgba(239, 68, 68, 0.15);
}
.metric-label { font-size: 11px; color: #8c8c9a; font-weight: 500; margin-bottom: 6px; text-transform: uppercase; letter-spacing: 0.5px; }
.metric-value { font-size: 22px; font-weight: 700; color: #1a1a2e; font-family: 'SF Mono', 'Fira Code', monospace; }
.metric-item.alert .metric-value { color: #ef4444; }
.alert-badge {
  position: absolute;
  top: 6px;
  right: 6px;
  font-size: 9px;
  font-weight: 700;
  color: #ef4444;
  background: rgba(239, 68, 68, 0.1);
  padding: 1px 5px;
  border-radius: 4px;
  letter-spacing: 0.5px;
}
</style>
