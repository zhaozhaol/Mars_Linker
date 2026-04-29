<template>
  <div class="config-grid">
    <div v-for="item in configItems" :key="item.label" class="config-item">
      <span class="config-label">{{ item.label }}</span>
      <span class="config-value" :class="item.class">
        <span v-if="item.isBool" class="bool-dot" :class="{ on: item.boolVal }"></span>
        {{ item.display }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { BrokerConfig } from '../../types/config'
import { formatBoolean, formatTtlMs } from '../../utils/formatter'

const props = defineProps<{ config: BrokerConfig | null }>()

const configItems = computed(() => {
  const c = props.config
  return [
    { label: 'Netty 启用', display: formatBoolean(c?.nettyEnabled ?? false), isBool: true, boolVal: c?.nettyEnabled ?? false, class: c?.nettyEnabled ? 'on' : 'off' },
    { label: 'TCP 端口', display: String(c?.tcpPort ?? '--'), isBool: false, boolVal: false, class: 'mono' },
    { label: '存储开关', display: formatBoolean(c?.storageEnabled ?? false), isBool: true, boolVal: c?.storageEnabled ?? false, class: c?.storageEnabled ? 'on' : 'off' },
    { label: '存储模式', display: c?.storageMode ?? '--', isBool: false, boolVal: false, class: 'mono' },
    { label: '离线消息上限', display: String(c?.sessionOfflineMaxMessages ?? '--'), isBool: false, boolVal: false, class: 'mono' },
    { label: '离线消息 TTL', display: c?.sessionOfflineTtlMs ? formatTtlMs(c.sessionOfflineTtlMs) : '--', isBool: false, boolVal: false, class: '' },
    { label: 'Retain 消息上限', display: String(c?.retainMaxMessages ?? '--'), isBool: false, boolVal: false, class: 'mono' },
    { label: 'Retain TTL', display: c?.retainTtlMs ? formatTtlMs(c.retainTtlMs) : '--', isBool: false, boolVal: false, class: '' }
  ]
})
</script>

<style scoped>
.config-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.config-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  background: #f8f8fc;
  border-radius: 10px;
  transition: background 0.2s;
}
.config-item:hover { background: #f0f0f8; }
.config-label { font-size: 13px; color: #8c8c9a; font-weight: 500; }
.config-value { font-size: 14px; font-weight: 600; color: #1a1a2e; display: flex; align-items: center; gap: 6px; }
.config-value.mono { font-family: 'SF Mono', 'Fira Code', monospace; }
.config-value.on { color: #10b981; }
.config-value.off { color: #94a3b8; }
.bool-dot { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
</style>
