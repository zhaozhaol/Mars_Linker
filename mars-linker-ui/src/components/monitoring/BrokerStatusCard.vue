<template>
  <div class="glass-card broker-status-card">
    <div class="card-header">
      <div class="card-icon broker-icon">B</div>
      <span class="card-title">Broker 状态</span>
    </div>
    <div class="status-grid">
      <div class="status-item">
        <span class="status-label">Netty</span>
        <span class="status-value" :class="status?.nettyEnabled ? 'on' : 'off'">{{ formatBoolean(status?.nettyEnabled ?? false) }}</span>
      </div>
      <div class="status-item">
        <span class="status-label">TCP 端口</span>
        <span class="status-value mono">{{ status?.tcpPort ?? '--' }}</span>
      </div>
      <div class="status-item">
        <span class="status-label">存储</span>
        <span class="status-value" :class="status?.storageEnabled ? 'on' : 'off'">{{ formatBoolean(status?.storageEnabled ?? false) }}</span>
      </div>
      <div class="status-item">
        <span class="status-label">存储模式</span>
        <span class="status-value mono">{{ status?.storageMode ?? '--' }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { BrokerStatus } from '../../types/monitoring'
import { formatBoolean } from '../../utils/formatter'

defineProps<{ status: BrokerStatus | null }>()
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
.broker-icon { background: linear-gradient(135deg, #6366f1, #8b5cf6); }
.card-title { font-size: 15px; font-weight: 600; color: #1a1a2e; }
.status-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.status-item { display: flex; flex-direction: column; gap: 4px; padding: 10px 12px; background: #f8f8fc; border-radius: 8px; }
.status-label { font-size: 12px; color: #8c8c9a; font-weight: 500; }
.status-value { font-size: 15px; font-weight: 600; color: #1a1a2e; }
.status-value.mono { font-family: 'SF Mono', 'Fira Code', monospace; }
.status-value.on { color: #10b981; }
.status-value.off { color: #94a3b8; }
</style>
