<template>
  <div class="history-page">
    <div class="page-header">
      <h2 class="page-title">告警历史</h2>
      <div class="filter-pills">
        <button class="pill" :class="{ active: activeOnly === false }" @click="activeOnly = false">全部</button>
        <button class="pill" :class="{ active: activeOnly === true }" @click="activeOnly = true">活跃</button>
      </div>
    </div>

    <div v-if="store.events.length === 0" class="empty-state">
      <span class="empty-icon">
        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
      </span>
      <span class="empty-text">暂无告警事件</span>
    </div>

    <div v-else class="timeline">
      <div v-for="evt in store.events" :key="evt.id" class="timeline-item" :class="{ active: evt.active }">
        <div class="timeline-dot" :class="evt.active ? 'firing' : 'resolved'"></div>
        <div class="timeline-line"></div>
        <div class="timeline-content">
          <div class="evt-header">
            <span class="evt-name">{{ evt.ruleName }}</span>
            <span class="evt-badge" :class="evt.active ? 'firing' : 'resolved'">{{ evt.active ? '告警中' : '已恢复' }}</span>
          </div>
          <div class="evt-body">
            <span class="evt-metric">{{ evt.metric }}</span>
            <span class="evt-desc">{{ evt.metric }} = <strong>{{ evt.actualValue.toFixed(1) }}</strong> (阈值 {{ evt.threshold }})</span>
          </div>
          <div class="evt-time">
            <span>触发: {{ formatTime(evt.triggeredAt) }}</span>
            <span v-if="!evt.active && evt.resolvedAt"> | 恢复: {{ formatTime(evt.resolvedAt) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useAlertStore } from '../stores/alert'

const store = useAlertStore()
const activeOnly = ref(false)

const formatTime = (ts: number) => {
  const d = new Date(ts)
  return d.toLocaleString('zh-CN', { hour12: false })
}

watch(activeOnly, () => store.fetchEvents(activeOnly.value))
onMounted(() => store.fetchEvents(false))
</script>

<style scoped>
.history-page { animation: fadeIn 0.3s ease; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; }
.page-title { font-size: 18px; font-weight: 600; color: #fff; margin: 0; }

.filter-pills { display: flex; gap: 4px; }
.pill {
  padding: 6px 14px; border: 1px solid rgba(255,255,255,0.06); background: transparent;
  border-radius: 8px; font-size: 12px; font-weight: 600; color: rgba(255,255,255,0.4);
  cursor: pointer; transition: all 0.15s;
}
.pill.active { background: rgba(77,109,255,0.15); color: #4d6dff; border-color: rgba(77,109,255,0.3); }

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

.evt-body { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.evt-metric { font-size: 12px; color: #7c5cfc; font-family: 'SF Mono','Fira Code',monospace; }
.evt-desc { font-size: 12px; color: rgba(255,255,255,0.6); }
.evt-desc strong { color: #fff; }
.evt-time { font-size: 11px; color: rgba(255,255,255,0.3); }

@keyframes fadeIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }
</style>
