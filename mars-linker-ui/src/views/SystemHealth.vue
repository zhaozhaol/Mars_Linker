<template>
  <div class="health-page">
    <div v-if="store.loading && !store.data" class="skeleton">
      <div v-for="i in 4" :key="i" class="skeleton-card"></div>
    </div>
    <template v-else-if="store.data">
      <div class="health-grid">
        <div class="health-card" :class="{ warn: store.heapWarning }">
          <div class="card-header">
            <span class="card-icon heap">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="4" y="4" width="16" height="16" rx="2"/><rect x="9" y="9" width="6" height="6"/><line x1="9" y1="1" x2="9" y2="4"/><line x1="15" y1="1" x2="15" y2="4"/><line x1="9" y1="20" x2="9" y2="23"/><line x1="15" y1="20" x2="15" y2="23"/><line x1="20" y1="9" x2="23" y2="9"/><line x1="20" y1="14" x2="23" y2="14"/><line x1="1" y1="9" x2="4" y2="9"/><line x1="1" y1="14" x2="4" y2="14"/></svg>
            </span>
            <span class="card-title">JVM 堆内存</span>
          </div>
          <div class="ring-row">
            <div class="ring-wrap">
              <svg class="ring" viewBox="0 0 120 120">
                <circle cx="60" cy="60" r="52" fill="none" stroke="rgba(255,255,255,0.06)" stroke-width="10"/>
                <circle cx="60" cy="60" r="52" fill="none" :stroke="heapColor" stroke-width="10" stroke-linecap="round"
                  :stroke-dasharray="ringDash(store.heap!.usagePercent)" stroke-dashoffset="0" transform="rotate(-90 60 60)" class="ring-fill"/>
              </svg>
              <div class="ring-label">
                <span class="ring-value">{{ store.heap!.usagePercent.toFixed(1) }}</span>
                <span class="ring-unit">%</span>
              </div>
            </div>
            <div class="ring-info">
              <div class="info-row"><span class="info-label">已用</span><span class="info-val mono">{{ formatBytes(store.heap!.used) }}</span></div>
              <div class="info-row"><span class="info-label">最大</span><span class="info-val mono">{{ formatBytes(store.heap!.max) }}</span></div>
              <div class="info-row"><span class="info-label">已提交</span><span class="info-val mono">{{ formatBytes(store.heap!.committed) }}</span></div>
            </div>
          </div>
        </div>

        <div class="health-card">
          <div class="card-header">
            <span class="card-icon cpu">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="4" y="4" width="16" height="16" rx="2"/><rect x="9" y="9" width="6" height="6"/><line x1="9" y1="1" x2="9" y2="4"/><line x1="15" y1="1" x2="15" y2="4"/><line x1="9" y1="20" x2="9" y2="23"/><line x1="15" y1="20" x2="15" y2="23"/><line x1="20" y1="9" x2="23" y2="9"/><line x1="20" y1="14" x2="23" y2="14"/><line x1="1" y1="9" x2="4" y2="9"/><line x1="1" y1="14" x2="4" y2="14"/></svg>
            </span>
            <span class="card-title">CPU 使用率</span>
          </div>
          <div class="ring-row">
            <div class="ring-wrap">
              <svg class="ring" viewBox="0 0 120 120">
                <circle cx="60" cy="60" r="52" fill="none" stroke="rgba(255,255,255,0.06)" stroke-width="10"/>
                <circle cx="60" cy="60" r="52" fill="none" :stroke="cpuColor" stroke-width="10" stroke-linecap="round"
                  :stroke-dasharray="ringDash(store.cpu!.processCpuPercent)" stroke-dashoffset="0" transform="rotate(-90 60 60)" class="ring-fill"/>
              </svg>
              <div class="ring-label">
                <span class="ring-value">{{ store.cpu!.processCpuPercent.toFixed(1) }}</span>
                <span class="ring-unit">%</span>
              </div>
            </div>
            <div class="ring-info">
              <div class="info-row"><span class="info-label">系统 CPU</span><span class="info-val mono">{{ store.cpu!.systemCpuPercent.toFixed(1) }}%</span></div>
              <div class="info-row"><span class="info-label">处理器</span><span class="info-val mono">{{ store.cpu!.availableProcessors }} 核</span></div>
              <div class="info-row"><span class="info-label">系统负载</span><span class="info-val mono">{{ store.cpu!.systemLoadAverage.toFixed(2) }}</span></div>
            </div>
          </div>
        </div>

        <div class="health-card">
          <div class="card-header">
            <span class="card-icon gc">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
            </span>
            <span class="card-title">GC 统计</span>
          </div>
          <div class="stat-grid">
            <div class="stat-block">
              <span class="stat-label">Young GC</span>
              <span class="stat-val mono">{{ store.gc!.youngGcCount }}</span>
              <span class="stat-sub">{{ store.gc!.youngGcTimeMs }} ms</span>
            </div>
            <div class="stat-block">
              <span class="stat-label">Full GC</span>
              <span class="stat-val mono">{{ store.gc!.fullGcCount }}</span>
              <span class="stat-sub">{{ store.gc!.fullGcTimeMs }} ms</span>
            </div>
          </div>
        </div>

        <div class="health-card">
          <div class="card-header">
            <span class="card-icon thread">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/></svg>
            </span>
            <span class="card-title">线程</span>
          </div>
          <div class="stat-grid">
            <div class="stat-block">
              <span class="stat-label">活跃</span>
              <span class="stat-val mono">{{ store.threads!.active }}</span>
            </div>
            <div class="stat-block">
              <span class="stat-label">峰值</span>
              <span class="stat-val mono">{{ store.threads!.peak }}</span>
            </div>
            <div class="stat-block">
              <span class="stat-label">守护</span>
              <span class="stat-val mono">{{ store.threads!.daemon }}</span>
            </div>
            <div class="stat-block">
              <span class="stat-label">累计启动</span>
              <span class="stat-val mono">{{ store.threads!.totalStarted }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="disk-section">
        <div class="health-card">
          <div class="card-header">
            <span class="card-icon disk">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/></svg>
            </span>
            <span class="card-title">磁盘</span>
          </div>
          <div class="disk-bar-wrap">
            <div class="disk-bar-bg">
              <div class="disk-bar-fill" :style="{ width: store.disk!.usagePercent + '%' }"></div>
            </div>
            <div class="disk-legend">
              <span class="legend-item"><span class="legend-dot used"></span>已用 {{ formatBytes(store.disk!.used) }}</span>
              <span class="legend-item"><span class="legend-dot free"></span>可用 {{ formatBytes(store.disk!.usable) }}</span>
              <span class="legend-item"><span class="legend-dot total"></span>总计 {{ formatBytes(store.disk!.total) }}</span>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, computed } from 'vue'
import { useSystemStore } from '../stores/system'
import { usePolling } from '../composables/usePolling'

const store = useSystemStore()

const RING_CIRCUMFERENCE = 2 * Math.PI * 52

const ringDash = (percent: number) => {
  const clamped = Math.min(100, Math.max(0, percent))
  const filled = (clamped / 100) * RING_CIRCUMFERENCE
  return `${filled} ${RING_CIRCUMFERENCE - filled}`
}

const heapColor = computed(() => {
  const p = store.heap?.usagePercent ?? 0
  if (p > 85) return '#ef4444'
  if (p > 70) return '#f59e0b'
  return '#4d6dff'
})

const cpuColor = computed(() => {
  const p = store.cpu?.processCpuPercent ?? 0
  if (p > 85) return '#ef4444'
  if (p > 70) return '#f59e0b'
  return '#10b981'
})

const formatBytes = (bytes: number): string => {
  if (bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  const val = bytes / Math.pow(1024, i)
  return val.toFixed(i === 0 ? 0 : 1) + ' ' + units[i]
}

const { start: startPolling, stop: stopPolling } = usePolling({
  interval: 3000,
  callback: store.fetchHealth
})

onMounted(startPolling)
onUnmounted(stopPolling)
</script>

<style scoped>
.health-page { animation: fadeIn 0.3s ease; }

.health-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }

.health-card {
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 14px;
  padding: 20px 24px;
  transition: border-color 0.2s;
}
.health-card.warn { border-color: rgba(239,68,68,0.3); }

.card-header { display: flex; align-items: center; gap: 10px; margin-bottom: 20px; }
.card-icon { display: flex; align-items: center; }
.card-icon.heap, .card-icon.cpu { color: #4d6dff; }
.card-icon.gc { color: #f59e0b; }
.card-icon.thread { color: #7c5cfc; }
.card-icon.disk { color: #10b981; }
.card-title { font-size: 14px; font-weight: 600; color: rgba(255,255,255,0.85); }

.ring-row { display: flex; align-items: center; gap: 24px; }
.ring-wrap { position: relative; width: 120px; height: 120px; flex-shrink: 0; }
.ring { width: 120px; height: 120px; }
.ring-fill { transition: stroke-dasharray 0.6s ease; }
.ring-label {
  position: absolute; inset: 0;
  display: flex; align-items: center; justify-content: center; gap: 2px;
}
.ring-value { font-size: 24px; font-weight: 700; color: #fff; }
.ring-unit { font-size: 12px; color: rgba(255,255,255,0.4); margin-top: 4px; }
.ring-info { display: flex; flex-direction: column; gap: 8px; }
.info-row { display: flex; justify-content: space-between; gap: 16px; }
.info-label { font-size: 12px; color: rgba(255,255,255,0.4); }
.info-val { font-size: 13px; font-weight: 500; color: rgba(255,255,255,0.85); }
.mono { font-family: 'SF Mono','Fira Code',monospace; }

.stat-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.stat-block { display: flex; flex-direction: column; gap: 4px; }
.stat-label { font-size: 11px; color: rgba(255,255,255,0.35); font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; }
.stat-val { font-size: 20px; font-weight: 700; color: #fff; }
.stat-sub { font-size: 12px; color: rgba(255,255,255,0.4); }

.disk-section {}
.disk-bar-wrap { display: flex; flex-direction: column; gap: 12px; }
.disk-bar-bg { height: 10px; background: rgba(255,255,255,0.06); border-radius: 5px; overflow: hidden; }
.disk-bar-fill { height: 100%; background: linear-gradient(90deg, #4d6dff, #7c5cfc); border-radius: 5px; transition: width 0.6s ease; }
.disk-legend { display: flex; gap: 20px; }
.legend-item { display: flex; align-items: center; gap: 6px; font-size: 12px; color: rgba(255,255,255,0.5); }
.legend-dot { width: 8px; height: 8px; border-radius: 50%; }
.legend-dot.used { background: #4d6dff; }
.legend-dot.free { background: #10b981; }
.legend-dot.total { background: rgba(255,255,255,0.15); }

.skeleton { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.skeleton-card { height: 200px; background: rgba(255,255,255,0.04); border-radius: 14px; animation: shimmer 1.5s infinite; }

@keyframes fadeIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }
@keyframes shimmer { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
</style>
