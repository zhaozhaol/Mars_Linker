<template>
  <div class="panel subscription-panel">
    <div class="card-header">
      <div class="card-icon sub-icon">S</div>
      <span class="card-title">订阅指标</span>
    </div>
    <MetricSkeleton v-if="loading" />
    <ErrorPlaceholder v-else-if="error" :message="error!" @retry="$emit('retry')" />
    <div v-else class="metrics-grid">
      <div class="metric-item" :class="{ degraded: data?.degraded }">
        <div class="metric-label">订阅总数</div>
        <div class="metric-value" :title="data?.degraded ? '订阅数据暂时不可用' : ''">
          {{ data?.degraded ? '--' : (data?.subscriptionsTotal ?? '--') }}
        </div>
      </div>
      <div class="metric-item" :class="{ degraded: data?.degraded }">
        <div class="metric-label">主题数</div>
        <div class="metric-value" :title="data?.degraded ? '订阅数据暂时不可用' : ''">
          {{ data?.degraded ? '--' : (data?.topicCount ?? '--') }}
        </div>
      </div>
      <div class="metric-item" :class="{ degraded: data?.degraded }">
        <div class="metric-label">订阅树深度</div>
        <div class="metric-value" :title="data?.degraded ? '订阅数据暂时不可用' : ''">
          {{ data?.degraded ? '--' : (data?.subscriptionTreeDepth ?? '--') }}
        </div>
      </div>
    </div>

    <button class="detail-toggle-btn" @click="showDetail = !showDetail">
      {{ showDetail ? '收起详情' : '查看详情' }}
    </button>

    <div v-if="showDetail" class="detail-section">
      <div class="detail-toolbar">
        <input
          v-model="clientIdSearch"
          class="client-search-input"
          placeholder="输入 Client ID 查询订阅"
          @keyup.enter="searchClientSubscriptions"
        />
        <button class="search-btn" @click="searchClientSubscriptions">查询</button>
      </div>

      <div v-if="clientSubResult" class="client-sub-result">
        <div class="client-sub-title">客户端 <span class="mono">{{ clientSubResult.clientId }}</span> 的订阅 ({{ clientSubResult.subscriptions.length }})</div>
        <table class="detail-table" v-if="clientSubResult.subscriptions.length">
          <thead><tr><th>Topic Filter</th><th>类型</th></tr></thead>
          <tbody>
            <tr v-for="s in clientSubResult.subscriptions" :key="s.topicFilter">
              <td class="mono">{{ s.topicFilter }}</td>
              <td><span :class="'type-badge type-' + s.subscriptionType">{{ typeLabel(s.subscriptionType) }}</span></td>
            </tr>
          </tbody>
        </table>
        <div v-else class="empty-hint">该客户端无订阅</div>
      </div>

      <div class="topics-section">
        <div class="topics-header">
          <span class="topics-title">订阅主题列表</span>
          <span class="topics-count" v-if="topicsTotal > 0">共 {{ topicsTotal }} 条</span>
        </div>
        <table class="detail-table" v-if="topics.length">
          <thead><tr><th>Topic Filter</th><th>订阅数</th><th>类型</th><th></th></tr></thead>
          <tbody>
            <template v-for="t in topics" :key="t.topicFilter">
              <tr class="topic-row" @click="toggleSubscribers(t.topicFilter)">
                <td class="mono">{{ t.topicFilter }}</td>
                <td>{{ t.subscriberCount }}</td>
                <td><span :class="'type-badge type-' + t.subscriptionType">{{ typeLabel(t.subscriptionType) }}</span></td>
                <td class="expand-icon">{{ expandedTopic === t.topicFilter ? '▲' : '▼' }}</td>
              </tr>
              <tr v-if="expandedTopic === t.topicFilter" class="subscriber-row">
                <td colspan="4">
                  <div class="subscriber-list" v-if="subscribers.length">
                    <table class="inner-table">
                      <thead><tr><th>Client ID</th><th>QoS</th><th>协议</th><th>连接时长</th><th>IP</th></tr></thead>
                      <tbody>
                        <tr v-for="s in subscribers" :key="s.clientId">
                          <td class="mono">{{ s.clientId }}</td>
                          <td>{{ s.grantedQos }}</td>
                          <td>{{ protocolLabel(s.protocolLevel) }}</td>
                          <td>{{ formatDuration(s.connectedDurationMs) }}</td>
                          <td class="mono">{{ s.remoteAddress }}</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                  <div v-else-if="subscribersLoading" class="empty-hint">加载中...</div>
                  <div v-else class="empty-hint">无订阅者</div>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
        <div v-else-if="topicsLoading" class="empty-hint">加载中...</div>
        <div v-else class="empty-hint">暂无订阅主题</div>

        <div class="pagination" v-if="topicsTotal > topicsPageSize">
          <button class="page-btn" :disabled="topicsPage <= 1" @click="topicsPage--; fetchTopics()">上一页</button>
          <span class="page-info">{{ topicsPage }} / {{ Math.ceil(topicsTotal / topicsPageSize) }}</span>
          <button class="page-btn" :disabled="topicsPage * topicsPageSize >= topicsTotal" @click="topicsPage++; fetchTopics()">下一页</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import type { SubscriptionMetrics, SubscriptionTopicInfo, SubscriberDetail, ClientSubscriptionInfo } from '../../types/monitoring'
import { getSubscriptionTopics, getSubscriptionSubscribers, getClientSubscriptions } from '../../api/monitoring'
import MetricSkeleton from './MetricSkeleton.vue'
import ErrorPlaceholder from './ErrorPlaceholder.vue'

defineProps<{
  data: SubscriptionMetrics | null
  loading: boolean
  error: string | null
}>()

defineEmits<{ retry: [] }>()

const showDetail = ref(false)

const topicsPage = ref(1)
const topicsPageSize = 50
const topics = ref<SubscriptionTopicInfo[]>([])
const topicsTotal = ref(0)
const topicsLoading = ref(false)

const expandedTopic = ref<string | null>(null)
const subscribers = ref<SubscriberDetail[]>([])
const subscribersLoading = ref(false)

const clientIdSearch = ref('')
const clientSubResult = ref<ClientSubscriptionInfo | null>(null)

const fetchTopics = async () => {
  topicsLoading.value = true
  try {
    const res = await getSubscriptionTopics({ page: topicsPage.value, size: topicsPageSize })
    topics.value = res.items
    topicsTotal.value = res.totalCount
  } catch { /* ignore */ } finally { topicsLoading.value = false }
}

const toggleSubscribers = async (topicFilter: string) => {
  if (expandedTopic.value === topicFilter) {
    expandedTopic.value = null
    subscribers.value = []
    return
  }
  expandedTopic.value = topicFilter
  subscribersLoading.value = true
  subscribers.value = []
  try {
    subscribers.value = await getSubscriptionSubscribers(topicFilter)
  } catch { /* ignore */ } finally { subscribersLoading.value = false }
}

const searchClientSubscriptions = async () => {
  if (!clientIdSearch.value.trim()) return
  try {
    clientSubResult.value = await getClientSubscriptions(clientIdSearch.value.trim())
  } catch { /* ignore */ }
}

const typeLabel = (type: string) => {
  switch (type) {
    case 'exact': return '精确'
    case 'wildcard': return '通配符'
    case 'shared': return '共享'
    default: return type
  }
}

const protocolLabel = (level: number) => {
  switch (level) {
    case 3: return 'MQTT 3.1'
    case 4: return 'MQTT 3.1.1'
    case 5: return 'MQTT 5.0'
    default: return `v${level}`
  }
}

const formatDuration = (ms: number) => {
  if (ms < 1000) return `${ms}ms`
  const sec = Math.floor(ms / 1000)
  if (sec < 60) return `${sec}s`
  const min = Math.floor(sec / 60)
  if (min < 60) return `${min}m${sec % 60}s`
  const hr = Math.floor(min / 60)
  return `${hr}h${min % 60}m`
}

watch(showDetail, (v) => { if (v && topics.value.length === 0) fetchTopics() })
</script>

<style scoped>
.panel {
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 12px;
  padding: 20px;
  transition: border-color 0.25s;
}
.panel:hover {
  border-color: rgba(255,255,255,0.1);
}
.card-header { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.card-icon { width: 32px; height: 32px; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 14px; color: #fff; }
.sub-icon { background: linear-gradient(135deg, #7c5cfc, #a855f7); }
.card-title { font-size: 14px; font-weight: 600; color: rgba(255,255,255,0.85); }
.metrics-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.metric-item {
  text-align: center;
  padding: 14px 8px;
  background: rgba(255,255,255,0.03);
  border-radius: 10px;
  transition: background 0.2s;
}
.metric-item.degraded {
  background: rgba(245, 158, 11, 0.06);
  border: 1px solid rgba(245, 158, 11, 0.15);
}
.metric-label { font-size: 11px; color: rgba(255,255,255,0.4); font-weight: 500; margin-bottom: 6px; text-transform: uppercase; letter-spacing: 0.5px; }
.metric-value { font-size: 22px; font-weight: 700; color: #fff; font-family: 'SF Mono', 'Fira Code', monospace; }
.metric-item.degraded .metric-value { color: #f59e0b; }

.detail-toggle-btn {
  margin-top: 12px;
  width: 100%;
  padding: 8px;
  border: 1px solid rgba(124,92,252,0.3);
  border-radius: 8px;
  background: rgba(124,92,252,0.08);
  color: rgba(255,255,255,0.7);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}
.detail-toggle-btn:hover { background: rgba(124,92,252,0.15); color: #fff; }

.detail-section {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid rgba(255,255,255,0.06);
}

.detail-toolbar {
  display: flex; gap: 8px; margin-bottom: 14px;
}
.client-search-input {
  flex: 1;
  padding: 7px 12px;
  border-radius: 8px;
  border: 1px solid rgba(255,255,255,0.1);
  background: rgba(255,255,255,0.04);
  color: #fff;
  font-size: 12px;
  outline: none;
}
.client-search-input::placeholder { color: rgba(255,255,255,0.3); }
.client-search-input:focus { border-color: rgba(124,92,252,0.5); }
.search-btn {
  padding: 7px 16px;
  border-radius: 8px;
  border: none;
  background: linear-gradient(135deg, #7c5cfc, #a855f7);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}
.search-btn:hover { opacity: 0.9; }

.client-sub-result { margin-bottom: 14px; }
.client-sub-title {
  font-size: 12px; color: rgba(255,255,255,0.5); margin-bottom: 8px;
}
.client-sub-title .mono { color: #a855f7; font-family: 'SF Mono','Fira Code',monospace; }

.topics-section {}
.topics-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.topics-title { font-size: 13px; font-weight: 600; color: rgba(255,255,255,0.7); }
.topics-count { font-size: 11px; color: rgba(255,255,255,0.3); }

.detail-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.detail-table th {
  text-align: left;
  padding: 6px 10px;
  color: rgba(255,255,255,0.35);
  font-weight: 500;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
}
.detail-table td {
  padding: 7px 10px;
  color: rgba(255,255,255,0.75);
  border-bottom: 1px solid rgba(255,255,255,0.03);
}
.topic-row { cursor: pointer; transition: background 0.15s; }
.topic-row:hover { background: rgba(255,255,255,0.04); }
.expand-icon { color: rgba(255,255,255,0.3); font-size: 10px; }

.subscriber-row td { padding: 0; }
.subscriber-list { padding: 8px 12px; background: rgba(255,255,255,0.02); border-radius: 8px; margin: 4px 0; }
.inner-table { width: 100%; border-collapse: collapse; font-size: 11px; }
.inner-table th {
  text-align: left; padding: 5px 8px; color: rgba(255,255,255,0.3);
  font-weight: 500; font-size: 10px; text-transform: uppercase; letter-spacing: 0.3px;
  border-bottom: 1px solid rgba(255,255,255,0.04);
}
.inner-table td {
  padding: 5px 8px; color: rgba(255,255,255,0.65);
  border-bottom: 1px solid rgba(255,255,255,0.02);
}

.type-badge {
  display: inline-block;
  padding: 1px 7px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.3px;
}
.type-exact { background: rgba(16,185,129,0.12); color: #10b981; }
.type-wildcard { background: rgba(245,158,11,0.12); color: #f59e0b; }
.type-shared { background: rgba(124,92,252,0.12); color: #a855f7; }

.mono { font-family: 'SF Mono','Fira Code',monospace; }

.empty-hint { font-size: 12px; color: rgba(255,255,255,0.25); padding: 10px 0; text-align: center; }

.pagination {
  display: flex; align-items: center; justify-content: center; gap: 10px; margin-top: 10px;
}
.page-btn {
  padding: 5px 14px; border-radius: 6px; border: 1px solid rgba(255,255,255,0.1);
  background: rgba(255,255,255,0.04); color: rgba(255,255,255,0.6);
  font-size: 11px; cursor: pointer; transition: all 0.2s;
}
.page-btn:hover:not(:disabled) { background: rgba(255,255,255,0.08); color: #fff; }
.page-btn:disabled { opacity: 0.3; cursor: not-allowed; }
.page-info { font-size: 11px; color: rgba(255,255,255,0.4); font-family: 'SF Mono','Fira Code',monospace; }
</style>
