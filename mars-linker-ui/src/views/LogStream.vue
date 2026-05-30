<template>
  <div class="log-page">
    <div class="log-toolbar">
      <div class="toolbar-left">
        <div class="conn-status" :class="store.connected ? 'on' : 'off'">
          <span class="conn-dot"></span>
          <span class="conn-text">{{ store.connected ? '已连接' : '未连接' }}</span>
        </div>
        <div class="level-pills">
          <button v-for="lv in levelOptions" :key="lv" class="pill" :class="{ active: store.levelFilter === lv }" @click="store.levelFilter = lv">{{ lv }}</button>
        </div>
      </div>
      <div class="toolbar-right">
        <span class="stat-chip">共 {{ store.stats.total }} 条</span>
        <span class="stat-chip err" v-if="store.stats.error">ERR {{ store.stats.error }}</span>
        <span class="stat-chip wrn" v-if="store.stats.warn">WRN {{ store.stats.warn }}</span>
        <button class="ml-btn ml-btn-ghost ml-btn-icon" @click="store.paused = !store.paused" :title="store.paused ? '继续' : '暂停'">
          <svg v-if="store.paused" width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21"/></svg>
          <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="4" height="16"/><rect x="14" y="4" width="4" height="16"/></svg>
        </button>
        <button class="ml-btn ml-btn-ghost ml-btn-icon" @click="store.clearLogs" title="清空">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
        </button>
      </div>
    </div>

    <div class="log-list" ref="logListEl">
      <div v-if="store.filteredLogs.length === 0" class="log-empty">
        {{ store.connected ? '等待日志...' : '未连接到日志流' }}
      </div>
      <div v-for="entry in store.filteredLogs" :key="entry.id" class="log-row" :class="levelClass(entry.level)">
        <span class="log-time mono">{{ formatTime(entry.timestamp) }}</span>
        <span class="log-level" :class="levelClass(entry.level)">{{ entry.level }}</span>
        <span class="log-logger" :title="entry.logger">{{ shortLogger(entry.logger) }}</span>
        <span class="log-msg">{{ entry.message }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useLogStreamStore } from '../stores/logstream'
import type { LogEntry } from '../types/logstream'

const store = useLogStreamStore()
const logListEl = ref<HTMLElement | null>(null)
const autoScroll = ref(true)

const levelOptions = ['ALL', 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE']

const levelClass = (level: string) => {
  switch (level) {
    case 'ERROR': return 'err'
    case 'WARN': return 'wrn'
    case 'INFO': return 'inf'
    case 'DEBUG': return 'dbg'
    case 'TRACE': return 'trc'
    default: return ''
  }
}

const shortLogger = (name: string) => {
  const parts = name.split('.')
  if (parts.length <= 2) return name
  return parts.slice(0, 2).join('.') + '.' + parts[parts.length - 1]
}

const formatTime = (ts: number) => {
  const d = new Date(ts)
  const h = d.getHours().toString().padStart(2, '0')
  const m = d.getMinutes().toString().padStart(2, '0')
  const s = d.getSeconds().toString().padStart(2, '0')
  const ms = d.getMilliseconds().toString().padStart(3, '0')
  return `${h}:${m}:${s}.${ms}`
}

watch(() => store.logs.length, async () => {
  if (autoScroll.value && logListEl.value) {
    await nextTick()
    logListEl.value.scrollTop = logListEl.value.scrollHeight
  }
})

let ws: WebSocket | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null

const connect = () => {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsPort = import.meta.env.VITE_LOG_WS_PORT || '11885'
  const url = `${protocol}//${location.hostname}:${wsPort}/api/ui/logs/stream`
  ws = new WebSocket(url)

  ws.onopen = () => { store.connected = true }
  ws.onclose = () => {
    store.connected = false
    scheduleReconnect()
  }
  ws.onerror = () => {
    store.connected = false
    ws?.close()
  }
  ws.onmessage = (event) => {
    try {
      const entry: LogEntry = JSON.parse(event.data)
      store.pushLog(entry)
    } catch { /* ignore */ }
  }
}

const scheduleReconnect = () => {
  if (reconnectTimer) return
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    connect()
  }, 3000)
}

const disconnect = () => {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (ws) {
    ws.onclose = null
    ws.close()
    ws = null
  }
  store.connected = false
}

onMounted(connect)
onUnmounted(disconnect)
</script>

<style scoped>
.log-page { display: flex; flex-direction: column; height: calc(100vh - 56px - 48px); animation: fadeIn 0.3s ease; }

.log-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 0; gap: 12px; flex-shrink: 0;
}
.toolbar-left { display: flex; align-items: center; gap: 16px; }
.toolbar-right { display: flex; align-items: center; gap: 8px; }

.conn-status { display: flex; align-items: center; gap: 6px; }
.conn-dot { width: 8px; height: 8px; border-radius: 50%; }
.conn-status.on .conn-dot { background: #10b981; box-shadow: 0 0 6px rgba(16,185,129,0.4); }
.conn-status.off .conn-dot { background: #64748b; }
.conn-text { font-size: 12px; font-weight: 500; }
.conn-status.on .conn-text { color: #10b981; }
.conn-status.off .conn-text { color: #64748b; }

.level-pills { display: flex; gap: 2px; }
.pill {
  padding: 4px 10px; border: 1px solid rgba(255,255,255,0.06); background: transparent;
  border-radius: 6px; font-size: 11px; font-weight: 600; color: rgba(255,255,255,0.4);
  cursor: pointer; transition: all 0.15s;
}
.pill.active { background: rgba(77,109,255,0.15); color: #4d6dff; border-color: rgba(77,109,255,0.3); }

.stat-chip { padding: 3px 8px; border-radius: 6px; font-size: 11px; font-weight: 600; background: rgba(255,255,255,0.04); color: rgba(255,255,255,0.5); }
.stat-chip.err { background: rgba(239,68,68,0.1); color: #ef4444; }
.stat-chip.wrn { background: rgba(245,158,11,0.1); color: #f59e0b; }

.log-list {
  flex: 1; overflow-y: auto; background: rgba(0,0,0,0.3);
  border: 1px solid rgba(255,255,255,0.04); border-radius: 10px;
  padding: 8px 0; font-family: 'SF Mono','Fira Code',monospace; font-size: 12px;
}
.log-empty { padding: 40px; text-align: center; color: rgba(255,255,255,0.2); font-family: inherit; }

.log-row {
  display: flex; align-items: baseline; gap: 10px; padding: 3px 14px;
  border-bottom: 1px solid rgba(255,255,255,0.02); transition: background 0.1s;
}
.log-row:hover { background: rgba(255,255,255,0.02); }
.log-row.err { background: rgba(239,68,68,0.04); }
.log-row.wrn { background: rgba(245,158,11,0.03); }

.log-time { color: rgba(255,255,255,0.3); flex-shrink: 0; white-space: nowrap; }
.log-level { font-weight: 700; flex-shrink: 0; width: 48px; text-align: center; }
.log-level.err { color: #ef4444; }
.log-level.wrn { color: #f59e0b; }
.log-level.inf { color: #10b981; }
.log-level.dbg { color: #64748b; }
.log-level.trc { color: rgba(255,255,255,0.2); }
.log-logger { color: #7c5cfc; flex-shrink: 0; max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.log-msg { color: rgba(255,255,255,0.75); word-break: break-all; }

.mono { font-family: 'SF Mono','Fira Code',monospace; }

@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
</style>
