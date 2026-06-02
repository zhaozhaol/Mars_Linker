<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { getTopicRateLimits } from '../api/monitoring'
import type { TopicRateLimitStats, TopicRateLimitOverview } from '../types/monitoring'

const loading = ref(false)
const error = ref<string | null>(null)
const data = ref<TopicRateLimitOverview | null>(null)
const searchQuery = ref('')
const refreshInterval = ref(10000)
let refreshTimer: ReturnType<typeof setInterval> | null = null

const filteredStats = computed(() => {
  if (!data.value?.stats) return []
  const entries = Object.values(data.value.stats)
  if (!searchQuery.value.trim()) return entries
  const q = searchQuery.value.trim().toLowerCase()
  return entries.filter(s => s.topic.toLowerCase().includes(q))
})

const totalRejected = computed(() => {
  if (!data.value?.stats) return 0
  return Object.values(data.value.stats).reduce((sum, s) => sum + s.rejectedCount, 0)
})

async function refresh() {
  loading.value = true
  error.value = null
  try {
    data.value = await getTopicRateLimits()
  } catch (e: any) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function startAutoRefresh() {
  stopAutoRefresh()
  if (refreshInterval.value > 0) {
    refreshTimer = setInterval(refresh, refreshInterval.value)
  }
}

function stopAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

function formatNumber(n: number): string {
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M'
  if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K'
  return String(n)
}

onMounted(() => {
  refresh()
  startAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>

<template>
  <div class="topic-rate-limits-page">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">主题限流</h1>
        <span class="page-subtitle">精确主题消息速率限流监控</span>
      </div>
      <div class="header-right">
        <div class="search-box">
          <svg class="search-icon" viewBox="0 0 20 20" fill="currentColor" width="16" height="16">
            <path fill-rule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clip-rule="evenodd"/>
          </svg>
          <input v-model="searchQuery" type="text" placeholder="搜索主题..." class="search-input" />
        </div>
        <button class="ml-btn-ghost" @click="refresh" :disabled="loading">
          <svg viewBox="0 0 20 20" fill="currentColor" width="16" height="16" :class="{ 'spinning': loading }">
            <path fill-rule="evenodd" d="M4 2a1 1 0 011 1v2.101a7.002 7.002 0 0111.601 2.566 1 1 0 11-1.885.705A5.002 5.002 0 005.999 7H9a1 1 0 010 2H4a1 1 0 01-1-1V3a1 1 0 011-1zm.008 9.057a1 1 0 011.276-.842A5.002 5.002 0 0014.001 13H11a1 1 0 110-2h5a1 1 0 011 1v5a1 1 0 11-2 0v-2.101a7.002 7.002 0 01-11.601-2.566 1 1 0 01.609-1.276z" clip-rule="evenodd"/>
          </svg>
          刷新
        </button>
      </div>
    </div>

    <div class="summary-bar">
      <div class="summary-item">
        <span class="summary-label">限流规则</span>
        <span class="summary-value">{{ data?.rules ?? '-' }}</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">总拒绝数</span>
        <span class="summary-value" :class="{ 'alert-value': totalRejected > 0 }">{{ formatNumber(totalRejected) }}</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">刷新间隔</span>
        <select v-model.number="refreshInterval" @change="startAutoRefresh" class="refresh-select">
          <option :value="0">手动</option>
          <option :value="3000">3秒</option>
          <option :value="5000">5秒</option>
          <option :value="10000">10秒</option>
          <option :value="30000">30秒</option>
        </select>
      </div>
    </div>

    <div v-if="error" class="error-placeholder">
      <svg viewBox="0 0 20 20" fill="currentColor" width="24" height="24">
        <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clip-rule="evenodd"/>
      </svg>
      <span>{{ error }}</span>
    </div>

    <div v-else-if="!data || data.rules === 0" class="empty-placeholder">
      <svg viewBox="0 0 20 20" fill="currentColor" width="48" height="48" style="opacity:0.3">
        <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM7 9a3 3 0 116 0 3 3 0 01-6 0z" clip-rule="evenodd"/>
      </svg>
      <p>暂无限流规则配置</p>
      <p class="empty-hint">在 broker 配置中设置 mars.linker.broker.topic-rate-limits 启用主题限流</p>
    </div>

    <div v-else class="table-container">
      <table class="data-table">
        <thead>
          <tr>
            <th class="col-topic">主题</th>
            <th class="col-rate">上限(msg/s)</th>
            <th class="col-strategy">策略</th>
            <th class="col-tokens">可用令牌</th>
            <th class="col-allowed">已通过</th>
            <th class="col-rejected">已拒绝</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="stat in filteredStats" :key="stat.topic" :class="{ 'row-alert': stat.rejectedCount > 0 }">
            <td class="col-topic">
              <span class="topic-name">{{ stat.topic }}</span>
            </td>
            <td class="col-rate">
              <span class="mono-value">{{ stat.maxPerSecond }}</span>
            </td>
            <td class="col-strategy">
              <span class="strategy-badge" :class="stat.strategy === 'disconnect' ? 'strategy-danger' : 'strategy-default'">
                {{ stat.strategy === 'disconnect' ? '断开' : '丢弃' }}
              </span>
            </td>
            <td class="col-tokens">
              <span class="mono-value">{{ stat.availableTokens }}</span>
            </td>
            <td class="col-allowed">
              <span class="mono-value">{{ formatNumber(stat.allowedCount) }}</span>
            </td>
            <td class="col-rejected">
              <span class="mono-value" :class="{ 'rejected-highlight': stat.rejectedCount > 0 }">
                {{ formatNumber(stat.rejectedCount) }}
              </span>
              <span v-if="stat.rejectedCount > 0" class="alert-dot"></span>
            </td>
          </tr>
          <tr v-if="filteredStats.length === 0">
            <td colspan="6" class="no-match">无匹配结果</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.topic-rate-limits-page {
  padding: 24px;
  min-height: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header-left { display: flex; align-items: baseline; gap: 12px; }

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #e5e7eb;
  margin: 0;
}

.page-subtitle {
  font-size: 13px;
  color: rgba(255,255,255,0.4);
}

.header-right { display: flex; align-items: center; gap: 12px; }

.search-box {
  position: relative;
  display: flex;
  align-items: center;
}

.search-icon {
  position: absolute;
  left: 10px;
  color: rgba(255,255,255,0.3);
}

.search-input {
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 8px;
  padding: 6px 12px 6px 32px;
  color: #e5e7eb;
  font-size: 13px;
  outline: none;
  width: 200px;
  transition: border-color 0.2s;
}

.search-input:focus {
  border-color: rgba(77,109,255,0.5);
}

.summary-bar {
  display: flex;
  gap: 24px;
  padding: 12px 16px;
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 10px;
  margin-bottom: 16px;
}

.summary-item { display: flex; align-items: center; gap: 8px; }

.summary-label {
  font-size: 12px;
  color: rgba(255,255,255,0.4);
}

.summary-value {
  font-size: 14px;
  font-weight: 600;
  color: #e5e7eb;
  font-family: 'SF Mono', 'Fira Code', monospace;
}

.alert-value { color: #ef4444; }

.refresh-select {
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 6px;
  padding: 3px 8px;
  color: #e5e7eb;
  font-size: 12px;
  outline: none;
  cursor: pointer;
}

.error-placeholder, .empty-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: rgba(255,255,255,0.4);
  gap: 8px;
}

.empty-hint { font-size: 12px; opacity: 0.6; }

.table-container {
  background: rgba(255,255,255,0.02);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 10px;
  overflow: hidden;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table th {
  padding: 10px 16px;
  text-align: left;
  font-size: 11px;
  font-weight: 500;
  color: rgba(255,255,255,0.4);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
  background: rgba(255,255,255,0.02);
}

.data-table td {
  padding: 10px 16px;
  font-size: 13px;
  color: #e5e7eb;
  border-bottom: 1px solid rgba(255,255,255,0.04);
}

.data-table tr:hover td {
  background: rgba(255,255,255,0.02);
}

.row-alert td {
  background: rgba(239,68,68,0.04);
}

.col-topic { width: 40%; }
.col-rate, .col-tokens { width: 12%; text-align: center; }
.col-strategy { width: 10%; text-align: center; }
.col-allowed { width: 13%; text-align: right; }
.col-rejected { width: 13%; text-align: right; }

.topic-name {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 12px;
  color: #c4b5fd;
}

.mono-value {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 12px;
}

.strategy-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.strategy-default {
  background: rgba(245,158,11,0.15);
  color: #f59e0b;
}

.strategy-danger {
  background: rgba(239,68,68,0.15);
  color: #ef4444;
}

.rejected-highlight {
  color: #ef4444;
  font-weight: 600;
}

.alert-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ef4444;
  margin-left: 6px;
  animation: pulse 1.5s ease-in-out infinite;
  vertical-align: middle;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.no-match {
  text-align: center;
  color: rgba(255,255,255,0.3);
  padding: 40px 16px !important;
}

.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
