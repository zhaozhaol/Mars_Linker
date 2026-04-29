<template>
  <div class="event-table-wrap">
    <div v-if="loading" class="table-loading">
      <div class="spinner"></div>
      <span>加载中...</span>
    </div>
    <table v-else class="modern-table">
      <thead>
        <tr>
          <th class="col-time">时间</th>
          <th class="col-type">类型</th>
          <th class="col-source">来源</th>
          <th class="col-payload">负载</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(event, idx) in events" :key="idx" class="table-row">
          <td class="mono">{{ formatTimestamp(event.timestamp) }}</td>
          <td><span class="type-badge">{{ event.type }}</span></td>
          <td>{{ event.source }}</td>
          <td class="payload-cell">
            <span v-if="event.payload && event.payload.length > 100" :title="event.payload">{{ event.payload.substring(0, 100) }}...</span>
            <span v-else>{{ event.payload }}</span>
          </td>
        </tr>
        <tr v-if="!events.length" class="empty-row">
          <td colspan="4">暂无采集事件</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import type { CollectedEvent } from '../../types/collection'
import { formatTimestamp } from '../../utils/formatter'

defineProps<{ events: CollectedEvent[]; loading: boolean }>()
</script>

<style scoped>
.event-table-wrap { min-height: 120px; }
.table-loading { display: flex; align-items: center; gap: 8px; justify-content: center; padding: 32px; color: #8c8c9a; font-size: 13px; }
.spinner { width: 18px; height: 18px; border: 2px solid #e0e0ec; border-top-color: #6366f1; border-radius: 50%; animation: spin 0.6s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.modern-table { width: 100%; border-collapse: separate; border-spacing: 0; }
.modern-table thead th {
  padding: 10px 14px;
  font-size: 11px;
  font-weight: 600;
  color: #8c8c9a;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  text-align: left;
  border-bottom: 1px solid #e8e8f0;
  background: #fafafc;
}
.modern-table thead th:first-child { border-radius: 8px 0 0 0; }
.modern-table thead th:last-child { border-radius: 0 8px 0 0; }
.table-row { transition: background 0.15s; }
.table-row:hover { background: #f8f8fc; }
.table-row td { padding: 12px 14px; font-size: 13px; color: #1a1a2e; border-bottom: 1px solid #f0f0f5; }
.mono { font-family: 'SF Mono', 'Fira Code', monospace; font-size: 12px; }
.type-badge { display: inline-block; padding: 2px 8px; background: rgba(99,102,241,0.08); color: #6366f1; border-radius: 4px; font-size: 12px; font-weight: 500; }
.payload-cell { max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.empty-row td { text-align: center; padding: 32px; color: #b0b0c0; font-size: 14px; }
</style>
