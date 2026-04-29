<template>
  <div class="config-page">
    <div v-if="configStore.brokerLoading" class="skeleton">
      <div v-for="i in 4" :key="i" class="skeleton-row"></div>
    </div>
    <div v-else-if="error" class="error-state">
      <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#ef4444" stroke-width="1.5"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
      <p class="error-text">配置数据加载失败</p>
      <button class="ml-btn ml-btn-primary" @click="loadConfig">重试</button>
    </div>
    <template v-else>
      <div class="config-section">
        <h3 class="section-title">网络配置</h3>
        <div class="config-list">
          <div v-for="item in networkItems" :key="item.label" class="config-row">
            <div class="config-row-left">
              <span class="config-icon" v-html="item.icon"></span>
              <span class="config-name">{{ item.label }}</span>
            </div>
            <span class="config-val" :class="item.class">
              <span v-if="item.isBool" class="dot" :class="{ on: item.boolVal }"></span>
              {{ item.display }}
            </span>
          </div>
        </div>
      </div>
      <div class="config-section">
        <h3 class="section-title">离线消息配置</h3>
        <div class="config-list">
          <div class="config-row">
            <div class="config-row-left">
              <span class="config-icon"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg></span>
              <span class="config-name">消息上限</span>
            </div>
            <span class="config-val mono">{{ configStore.brokerConfig?.sessionOfflineMaxMessages ?? '--' }}</span>
          </div>
          <div class="config-row">
            <div class="config-row-left">
              <span class="config-icon"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg></span>
              <span class="config-name">保留时长 (TTL)</span>
            </div>
            <span class="config-val">{{ configStore.brokerConfig?.sessionOfflineTtlMs ? formatTtlMs(configStore.brokerConfig.sessionOfflineTtlMs) : '--' }}</span>
          </div>
        </div>
      </div>
      <div class="config-section">
        <h3 class="section-title">Retain 消息配置</h3>
        <div class="config-list">
          <div class="config-row">
            <div class="config-row-left">
              <span class="config-icon"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/></svg></span>
              <span class="config-name">消息上限</span>
            </div>
            <span class="config-val mono">{{ configStore.brokerConfig?.retainMaxMessages ?? '--' }}</span>
          </div>
          <div class="config-row">
            <div class="config-row-left">
              <span class="config-icon"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg></span>
              <span class="config-name">保留时长 (TTL)</span>
            </div>
            <span class="config-val">{{ configStore.brokerConfig?.retainTtlMs ? formatTtlMs(configStore.brokerConfig.retainTtlMs) : '--' }}</span>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useConfigStore } from '../stores/config'
import { formatBoolean, formatTtlMs } from '../utils/formatter'

const configStore = useConfigStore()
const error = ref(false)

const networkItems = computed(() => {
  const c = configStore.brokerConfig
  return [
    { label: 'Netty 启用', display: formatBoolean(c?.nettyEnabled ?? false), isBool: true, boolVal: c?.nettyEnabled ?? false, class: c?.nettyEnabled ? 'val-on' : 'val-off', icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="2" width="20" height="20" rx="5"/><path d="M8 12h8"/></svg>' },
    { label: 'TCP 端口', display: String(c?.tcpPort ?? '--'), isBool: false, boolVal: false, class: 'mono', icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/></svg>' },
    { label: '存储开关', display: formatBoolean(c?.storageEnabled ?? false), isBool: true, boolVal: c?.storageEnabled ?? false, class: c?.storageEnabled ? 'val-on' : 'val-off', icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 5c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/></svg>' },
    { label: '存储模式', display: c?.storageMode ?? '--', isBool: false, boolVal: false, class: 'mono', icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>' }
  ]
})

const loadConfig = async () => {
  error.value = false
  try { await configStore.fetchBrokerConfig() } catch { error.value = true }
}

onMounted(loadConfig)
</script>

<style scoped>
.config-page { animation: fadeIn 0.3s ease; }
.config-section { margin-bottom: 24px; }
.section-title { font-size: 13px; font-weight: 600; color: rgba(255,255,255,0.4); text-transform: uppercase; letter-spacing: 0.5px; margin: 0 0 12px; }
.config-list { display: flex; flex-direction: column; gap: 4px; }
.config-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 16px; background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.04); border-radius: 10px;
  transition: background 0.15s, border-color 0.15s;
}
.config-row:hover { background: rgba(255,255,255,0.06); border-color: rgba(255,255,255,0.08); }
.config-row-left { display: flex; align-items: center; gap: 10px; }
.config-icon { color: rgba(255,255,255,0.3); display: flex; align-items: center; }
.config-name { font-size: 13px; color: rgba(255,255,255,0.65); font-weight: 500; }
.config-val { font-size: 14px; font-weight: 600; color: #fff; display: flex; align-items: center; gap: 6px; }
.config-val.mono { font-family: 'SF Mono','Fira Code',monospace; }
.val-on { color: #10b981; }
.val-off { color: #64748b; }
.dot { width: 6px; height: 6px; border-radius: 50%; background: #64748b; }
.dot.on { background: #10b981; box-shadow: 0 0 6px rgba(16,185,129,0.4); }

.skeleton {}
.skeleton-row { height: 44px; background: rgba(255,255,255,0.04); border-radius: 10px; margin-bottom: 8px; animation: shimmer 1.5s infinite; }
.error-state { text-align: center; padding: 40px 0; }
.error-text { color: rgba(255,255,255,0.5); font-size: 14px; margin: 12px 0; }

@keyframes fadeIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }
@keyframes shimmer { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
</style>
