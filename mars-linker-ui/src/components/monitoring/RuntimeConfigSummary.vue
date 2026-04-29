<template>
  <div class="glass-card">
    <div class="card-header">
      <div class="card-icon rt-icon">R</div>
      <span class="card-title">运行时配置</span>
    </div>
    <div class="info-rows">
      <div class="info-row">
        <span class="info-label">启用状态</span>
        <span class="info-value" :class="config?.enabled ? 'on' : 'off'">{{ formatBoolean(config?.enabled ?? false) }}</span>
      </div>
      <div class="info-row">
        <span class="info-label">采集模式</span>
        <span class="info-value mono">{{ config?.collectMode ?? '--' }}</span>
      </div>
      <div class="info-row">
        <span class="info-label">刷新间隔</span>
        <span class="info-value mono">{{ config?.monitorRefreshMs ? config.monitorRefreshMs + ' ms' : '--' }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { RuntimeConfig } from '../../types/monitoring'
import { formatBoolean } from '../../utils/formatter'

defineProps<{ config: RuntimeConfig | null }>()
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
.glass-card:hover { box-shadow: 0 6px 24px rgba(0,0,0,0.08); transform: translateY(-2px); }
.card-header { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.card-icon { width: 32px; height: 32px; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 14px; color: #fff; }
.rt-icon { background: linear-gradient(135deg, #f59e0b, #f97316); }
.card-title { font-size: 15px; font-weight: 600; color: #1a1a2e; }
.info-rows { display: flex; flex-direction: column; gap: 10px; }
.info-row { display: flex; justify-content: space-between; align-items: center; padding: 10px 14px; background: #f8f8fc; border-radius: 8px; }
.info-label { font-size: 13px; color: #8c8c9a; font-weight: 500; }
.info-value { font-size: 14px; font-weight: 600; color: #1a1a2e; }
.info-value.mono { font-family: 'SF Mono', 'Fira Code', monospace; }
.info-value.on { color: #10b981; }
.info-value.off { color: #94a3b8; }
</style>
