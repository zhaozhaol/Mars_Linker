<template>
  <div class="history-page">
    <div class="page-header">
      <h2 class="page-title">告警历史</h2>
      <div class="filter-bar">
        <div class="filter-pills">
          <button class="pill" :class="{ active: activeOnly === false }" @click="activeOnly = false">全部</button>
          <button class="pill" :class="{ active: activeOnly === true }" @click="activeOnly = true">活跃</button>
        </div>
        <div class="time-range">
          <input v-model="startTime" type="datetime-local" class="time-input" placeholder="开始时间" />
          <span class="range-sep">-</span>
          <input v-model="endTime" type="datetime-local" class="time-input" placeholder="结束时间" />
          <button class="apply-btn" @click="fetchHistory">应用</button>
        </div>
      </div>
    </div>

    <div v-if="loading" class="loading-state">加载中...</div>
    <div v-else-if="pagedEvents.length === 0" class="empty-state">
      <span class="empty-icon">
        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
      </span>
      <span class="empty-text">暂无告警事件</span>
    </div>

    <div v-else class="timeline">
      <div v-for="evt in pagedEvents" :key="evt.id" class="timeline-item" :class="{ active: evt.active }">
        <div class="timeline-dot" :class="evt.active ? 'firing' : 'resolved'"></div>
        <div class="timeline-line"></div>
        <div class="timeline-content">
          <div class="evt-header">
            <span class="evt-name">{{ evt.ruleName }}</span>
            <span class="evt-badge" :class="evt.active ? (evt.severity || 'critical') : 'resolved'">{{ evt.active ? '告警中' : '已恢复' }}</span>
            <span v-if="evt.severity" class="severity-badge" :class="evt.severity">{{ evt.severity }}</span>
          </div>
          <div class="evt-body">
            <span class="evt-metric">{{ evt.metric }}</span>
            <span class="evt-desc">{{ evt.metric }} = <strong>{{ evt.actualValue.toFixed(1) }}</strong> (阈值 {{ evt.threshold }})</span>
          </div>
          <div class="evt-time">
            <span>触发: {{ formatTime(evt.triggeredAt) }}</span>
            <span v-if="!evt.active && evt.resolvedAt"> | 恢复: {{ formatTime(evt.resolvedAt) }}</span>
            <span v-if="evt.resolvedAt && !evt.active"> | 已自动恢复</span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="totalCount > 0" class="pagination">
      <button class="page-btn" :disabled="page <= 1" @click="page--; fetchHistory()">上一页</button>
      <span class="page-info">{{ page }} / {{ totalPages }}</span>
      <button class="page-btn" :disabled="page >= totalPages" @click="page++; fetchHistory()">下一页</button>
      <span class="total-info">共 {{ totalCount }} 条</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, computed } from 'vue'
import { useAlertStore } from '../stores/alert'
import { getAlertHistory } from '../api/alert'
import type { AlertEvent } from '../types/alert'

const store = useAlertStore()
const activeOnly = ref(false)
const loading = ref(false)
const pagedEvents = ref<AlertEvent[]>([])
const totalCount = ref(0)
const page = ref(1)
const pageSize = ref(20)
const startTime = ref('')
const endTime = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(totalCount.value / pageSize.value)))

const formatTime = (ts: number) => {
  const d = new Date(ts)
  return d.toLocaleString('zh-CN', { hour12: false })
}

const toEpoch = (datetimeLocal: string): number | undefined => {
  if (!datetimeLocal) return undefined
  const d = new Date(datetimeLocal)
  return isNaN(d.getTime()) ? undefined : d.getTime()
}

const fetchHistory = async () => {
  loading.value = true
  try {
    const result = await getAlertHistory({
      start: toEpoch(startTime.value),
      end: toEpoch(endTime.value),
      page: page.value,
      size: pageSize.value
    })
    let items = result.items
    if (activeOnly.value) {
      items = items.filter(e => e.active)
    }
    pagedEvents.value = items
    totalCount.value = result.totalCount
  } catch {
    const fallback = activeOnly.value
    await store.fetchEvents(fallback)
    pagedEvents.value = store.events
    totalCount.value = store.events.length
  } finally {
    loading.value = false
  }
}

watch(activeOnly, () => { page.value = 1; fetchHistory() })
onMounted(() => fetchHistory())
</script>

<style scoped>
.history-page { animation: fadeIn 0.3s ease; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; flex-wrap: wrap; gap: 12px; }
.page-title { font-size: 18px; font-weight: 600; color: #fff; margin: 0; }

.filter-bar { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.filter-pills { display: flex; gap: 4px; }
.pill {
  padding: 6px 14px; border: 1px solid rgba(255,255,255,0.06); background: transparent;
  border-radius: 8px; font-size: 12px; font-weight: 600; color: rgba(255,255,255,0.4);
  cursor: pointer; transition: all 0.15s;
}
.pill.active { background: rgba(77,109,255,0.15); color: #4d6dff; border-color: rgba(77,109,255,0.3); }

.time-range { display: flex; align-items: center; gap: 6px; }
.time-input {
  padding: 6px 10px; border: 1px solid rgba(255,255,255,0.08);
  border-radius: 6px; background: rgba(255,255,255,0.04);
  color: #fff; font-size: 12px; outline: none;
  transition: border-color 0.2s;
}
.time-input:focus { border-color: #4d6dff; }
.range-sep { color: rgba(255,255,255,0.3); font-size: 12px; }
.apply-btn {
  padding: 6px 12px; background: rgba(77,109,255,0.15); color: #4d6dff;
  border: 1px solid rgba(77,109,255,0.3); border-radius: 6px;
  font-size: 12px; font-weight: 600; cursor: pointer; transition: background 0.15s;
}
.apply-btn:hover { background: rgba(77,109,255,0.25); }

.loading-state { text-align: center; padding: 40px; color: rgba(255,255,255,0.4); font-size: 14px; }

.empty-state { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 60px 0; }
.empty-icon { color: rgba(255,255,255,0.15); }
.empty-text { font-size: 14px; color: rgba(255,255,255,0.3); }

.timeline { display: flex; flex-direction: column; gap: 0; }
.timeline-item { display: flex; position: relative; padding-left: 28px; padding-bottom: 20px; }
.timeline-dot {
  position: absolute; left: 4px; top: 6px; width: 14px; height: 14px;
  border-radius: 50%; border: 2px solid; z-index: 1;
}
.timeline-dot.firing { border-color: #ef4444; background: rgba(239,68,68,0.2); box-shadow: 0 0 8px rgba(239,68,68,0.3); }
.timeline-dot.resolved { border-color: #10b981; background: rgba(16,185,129,0.2); }
.timeline-line { position: absolute; left: 10px; top: 22px; bottom: 0; width: 2px; background: rgba(255,255,255,0.06); }
.timeline-item:last-child .timeline-line { display: none; }

.timeline-content {
  flex: 1; padding: 10px 16px; background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.06);
  border-radius: 10px;
}
.timeline-item.active .timeline-content { border-color: rgba(239,68,68,0.2); background: rgba(239,68,68,0.04); }

.evt-header { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.evt-name { font-size: 14px; font-weight: 600; color: #fff; }
.evt-badge { padding: 2px 8px; border-radius: 6px; font-size: 10px; font-weight: 700; text-transform: uppercase; }
.evt-badge.firing { background: rgba(239,68,68,0.15); color: #ef4444; }
.evt-badge.resolved { background: rgba(16,185,129,0.15); color: #10b981; }
.evt-badge.warning { background: rgba(245,158,11,0.15); color: #f59e0b; }
.evt-badge.info { background: rgba(59,130,246,0.15); color: #3b82f6; }
.severity-badge { padding: 2px 6px; border-radius: 4px; font-size: 10px; font-weight: 600; }
.severity-badge.critical { background: rgba(239,68,68,0.12); color: #ef4444; }
.severity-badge.warning { background: rgba(245,158,11,0.12); color: #f59e0b; }
.severity-badge.info { background: rgba(59,130,246,0.12); color: #3b82f6; }

.evt-body { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.evt-metric { font-size: 12px; color: #7c5cfc; font-family: 'SF Mono','Fira Code',monospace; }
.evt-desc { font-size: 12px; color: rgba(255,255,255,0.6); }
.evt-desc strong { color: #fff; }
.evt-time { font-size: 11px; color: rgba(255,255,255,0.3); }

.pagination {
  display: flex; align-items: center; justify-content: center; gap: 12px;
  margin-top: 24px; padding: 12px 0;
}
.page-btn {
  padding: 6px 16px; border: 1px solid rgba(255,255,255,0.08);
  background: rgba(255,255,255,0.04); color: rgba(255,255,255,0.6);
  border-radius: 6px; font-size: 12px; cursor: pointer; transition: all 0.15s;
}
.page-btn:hover:not(:disabled) { background: rgba(255,255,255,0.08); color: #fff; }
.page-btn:disabled { opacity: 0.3; cursor: not-allowed; }
.page-info { font-size: 13px; color: rgba(255,255,255,0.6); font-family: 'SF Mono','Fira Code',monospace; }
.total-info { font-size: 12px; color: rgba(255,255,255,0.4); margin-left: 8px; }

@keyframes fadeIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }
</style>
