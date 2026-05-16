<template>
  <div class="audit-page">
    <div class="page-header">
      <h2 class="page-title">审计日志</h2>
    </div>
    <div v-if="loading" class="loading-state">加载中...</div>
    <div v-else-if="entries.length === 0" class="empty-state">暂无审计记录</div>
    <table v-else class="audit-table">
      <thead>
        <tr>
          <th>时间</th>
          <th>操作</th>
          <th>目标</th>
          <th>操作人</th>
          <th>变更</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="entry in entries" :key="entry.timestamp">
          <td class="mono">{{ formatTime(entry.timestamp) }}</td>
          <td>{{ entry.action }}</td>
          <td>{{ entry.target }}</td>
          <td>{{ entry.operator }}</td>
          <td class="changes-cell">{{ formatChanges(entry.changes) }}</td>
        </tr>
      </tbody>
    </table>
    <div v-if="totalCount > 0" class="pagination">
      <button class="page-btn" :disabled="page <= 1" @click="page--; fetchAudit()">上一页</button>
      <span>{{ page }} / {{ totalPages }}</span>
      <button class="page-btn" :disabled="page >= totalPages" @click="page++; fetchAudit()">下一页</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import client from '../api/client'

const entries = ref<any[]>([])
const loading = ref(false)
const totalCount = ref(0)
const page = ref(1)
const pageSize = 50

const totalPages = computed(() => Math.max(1, Math.ceil(totalCount.value / pageSize)))

const formatTime = (ts: number) => new Date(ts).toLocaleString('zh-CN', { hour12: false })
const formatChanges = (changes: Record<string, any>) => {
  if (!changes || Object.keys(changes).length === 0) return '-'
  return JSON.stringify(changes)
}

const fetchAudit = async () => {
  loading.value = true
  try {
    const resp = await client.get('/api/ui/audit', { params: { page: page.value, size: pageSize } })
    entries.value = resp.data.items || []
    totalCount.value = resp.data.totalCount || 0
  } catch { /* ignore */ } finally { loading.value = false }
}

onMounted(() => fetchAudit())
</script>

<style scoped>
.audit-page { animation: fadeIn 0.3s ease; }
.page-header { margin-bottom: 20px; }
.page-title { font-size: 18px; font-weight: 600; color: #fff; margin: 0; }
.loading-state { text-align: center; padding: 40px; color: rgba(255,255,255,0.4); }
.empty-state { text-align: center; padding: 40px; color: rgba(255,255,255,0.3); }
.audit-table { width: 100%; border-collapse: collapse; }
.audit-table th { padding: 10px 12px; font-size: 11px; color: rgba(255,255,255,0.4); text-align: left; border-bottom: 1px solid rgba(255,255,255,0.06); }
.audit-table td { padding: 10px 12px; font-size: 13px; color: rgba(255,255,255,0.7); border-bottom: 1px solid rgba(255,255,255,0.04); }
.mono { font-family: 'SF Mono','Fira Code',monospace; font-size: 12px; }
.changes-cell { max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-family: 'SF Mono','Fira Code',monospace; font-size: 11px; }
.pagination { display: flex; align-items: center; justify-content: center; gap: 12px; margin-top: 24px; color: rgba(255,255,255,0.6); }
.page-btn { padding: 6px 16px; border: 1px solid rgba(255,255,255,0.08); background: rgba(255,255,255,0.04); color: rgba(255,255,255,0.6); border-radius: 6px; font-size: 12px; cursor: pointer; }
.page-btn:hover:not(:disabled) { background: rgba(255,255,255,0.08); }
.page-btn:disabled { opacity: 0.3; cursor: not-allowed; }
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
</style>
