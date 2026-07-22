<template>
  <div class="monitoring-page">
    <div v-if="monitoringStore.isPaused" class="alert-banner error">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
      <span>连接中断，自动刷新已暂停</span>
      <button class="ml-btn ml-btn-danger ml-btn-sm" @click="manualRefresh">手动刷新</button>
    </div>
    <div v-else-if="monitoringStore.stale" class="alert-banner warning">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
      <span>数据未更新</span>
    </div>

    <div class="push-status-bar">
      <span class="push-mode">自动刷新: {{ refreshInterval / 1000 }}秒</span>
    </div>

    <div v-if="healthStatus" class="health-row">
      <span class="health-label">健康状态</span>
      <HealthBadge :status="healthStatus.status" />
      <div class="health-components">
        <div v-for="comp in healthStatus.components" :key="comp.name" class="comp-item">
          <span class="comp-name">{{ comp.name }}</span>
          <HealthBadge :status="comp.status" />
        </div>
      </div>
    </div>

    <div class="stats-row">
      <div class="stat-card accent-blue">
        <div class="stat-icon-wrap blue-icon">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#4d6dff" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/></svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ monitoringStore.metrics?.connectionsActive ?? '--' }}</div>
          <div class="stat-label">活跃连接</div>
        </div>
      </div>
      <div class="stat-card accent-green">
        <div class="stat-icon-wrap green-icon">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#10b981" stroke-width="2"><polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/></svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ monitoringStore.metrics?.publishInTotal ?? '--' }}</div>
          <div class="stat-label">消息发布入</div>
        </div>
      </div>
      <div class="stat-card accent-purple">
        <div class="stat-icon-wrap purple-icon">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#7c5cfc" stroke-width="2"><path d="M21 15v4a1 1 0 0 1-1 1H5a2 2 0 0 1-2-2V4"/><polyline points="7 17 12 12 17 17"/></svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ monitoringStore.metrics?.publishOutTotal ?? '--' }}</div>
          <div class="stat-label">消息发布出</div>
        </div>
      </div>
      <div class="stat-card" :class="hasAlert ? 'accent-red' : 'accent-slate'">
        <div class="stat-icon-wrap" :class="hasAlert ? 'red-icon' : 'slate-icon'">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" :stroke="hasAlert ? '#ef4444' : '#64748b'" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
        </div>
        <div class="stat-info">
          <div class="stat-value" :class="{ 'text-red': hasAlert }">{{ alertTotal }}</div>
          <div class="stat-label">拒绝/ACL 告警</div>
        </div>
      </div>
    </div>

    <div class="trends-row">
      <div class="trend-card">
        <TrendChart title="活跃连接" :points="monitoringStore.connectionsTrend" color="blue" />
      </div>
      <div class="trend-card">
        <TrendChart title="消息发布入" :points="monitoringStore.publishInTrend" color="green" />
      </div>
      <div class="trend-card">
        <TrendChart title="消息发布出" :points="monitoringStore.publishOutTrend" color="purple" />
      </div>
      <div class="trend-card">
        <TrendChart title="拒绝/ACL" :points="monitoringStore.rejectedTrend" color="red" />
      </div>
    </div>

    <div class="detail-panels">
      <ConnectionPanel
        :data="connectionData"
        :loading="connectionLoading"
        :error="connectionError"
        @retry="fetchConnection"
      />
      <MessagePanel
        :data="messageData"
        :loading="messageLoading"
        :error="messageError"
        @retry="fetchMessage"
      />
      <SubscriptionPanel
        :data="subscriptionData"
        :loading="subscriptionLoading"
        :error="subscriptionError"
        @retry="fetchSubscription"
      />
      <SystemPanel
        :data="systemData"
        :loading="systemLoading"
        :error="systemError"
        @retry="fetchSystem"
      />
    </div>

    <ConnectionListPanel ref="connectionListRef" />

    <div class="panels-grid">
      <div class="panel broker-panel">
        <div class="panel-header">
          <span class="panel-title">Broker 状态</span>
          <span class="panel-badge live">LIVE</span>
        </div>
        <div class="broker-grid">
          <div class="broker-item">
            <span class="broker-label">Netty</span>
            <span class="broker-val" :class="monitoringStore.brokerStatus?.nettyEnabled ? 'val-on' : 'val-off'">
              <span class="dot" :class="{ on: monitoringStore.brokerStatus?.nettyEnabled }"></span>
              {{ formatBoolean(monitoringStore.brokerStatus?.nettyEnabled ?? false) }}
            </span>
          </div>
          <div class="broker-item">
            <span class="broker-label">TCP 端口</span>
            <span class="broker-val mono">{{ monitoringStore.brokerStatus?.tcpPort ?? '--' }}</span>
          </div>
          <div class="broker-item">
            <span class="broker-label">存储</span>
            <span class="broker-val" :class="monitoringStore.brokerStatus?.storageEnabled ? 'val-on' : 'val-off'">
              <span class="dot" :class="{ on: monitoringStore.brokerStatus?.storageEnabled }"></span>
              {{ formatBoolean(monitoringStore.brokerStatus?.storageEnabled ?? false) }}
            </span>
          </div>
          <div class="broker-item">
            <span class="broker-label">存储模式</span>
            <span class="broker-val mono">{{ monitoringStore.brokerStatus?.storageMode ?? '--' }}</span>
          </div>
        </div>
      </div>

      <div class="panel metrics-panel">
        <div class="panel-header">
          <span class="panel-title">核心指标</span>
          <span class="panel-sub">{{ formatTimestamp(monitoringStore.data?.timestamp ?? 0) }}</span>
        </div>
        <div class="metrics-list">
          <div v-for="item in allMetrics" :key="item.key" class="metric-row" :class="{ alert: item.alert }">
            <span class="metric-name">{{ item.label }}</span>
            <span class="metric-val mono">{{ formatMetricValue(item.value) }}</span>
            <span v-if="item.alert" class="metric-alert-badge">ALERT</span>
          </div>
        </div>
      </div>

      <div class="panel rt-panel">
        <div class="panel-header">
          <span class="panel-title">运行时配置</span>
        </div>
        <div class="info-rows">
          <div class="info-row">
            <span class="info-label">启用状态</span>
            <span class="info-val" :class="monitoringStore.runtimeConfig?.enabled ? 'val-on' : 'val-off'">
              <span class="dot" :class="{ on: monitoringStore.runtimeConfig?.enabled }"></span>
              {{ formatBoolean(monitoringStore.runtimeConfig?.enabled ?? false) }}
            </span>
          </div>
          <div class="info-row">
            <span class="info-label">采集模式</span>
            <span class="info-val mono">{{ monitoringStore.runtimeConfig?.collectMode ?? '--' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">刷新间隔</span>
            <span class="info-val mono">{{ monitoringStore.runtimeConfig?.monitorRefreshMs ? monitoringStore.runtimeConfig.monitorRefreshMs + ' ms' : '--' }}</span>
          </div>
        </div>
      </div>

      <div class="panel coll-panel">
        <div class="panel-header">
          <span class="panel-title">采集统计</span>
        </div>
        <div class="coll-stats">
          <div class="coll-stat">
            <div class="coll-num mono">{{ monitoringStore.collectionStats?.bufferedSize ?? '--' }}</div>
            <div class="coll-label">缓冲区大小</div>
          </div>
          <div class="coll-divider"></div>
          <div class="coll-stat">
            <div class="coll-num mono">{{ monitoringStore.collectionStats?.totalCollected ?? '--' }}</div>
            <div class="coll-label">总采集数</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, computed } from 'vue'
import { useMonitoringStore } from '../stores/monitoring'
import { usePolling } from '../composables/usePolling'
import { formatBoolean, formatMetricValue, formatTimestamp } from '../utils/formatter'
import { getConnectionMetrics, getMessageMetrics, getSubscriptionMetrics, getSystemMetrics, getHealthStatus } from '../api/monitoring'
import type { ConnectionMetrics, MessageMetrics, SubscriptionMetrics, SystemMetrics, HealthStatus } from '../types/monitoring'
import TrendChart from '../components/monitoring/TrendChart.vue'
import ConnectionPanel from '../components/monitoring/ConnectionPanel.vue'
import ConnectionListPanel from '../components/monitoring/ConnectionListPanel.vue'
import MessagePanel from '../components/monitoring/MessagePanel.vue'
import SubscriptionPanel from '../components/monitoring/SubscriptionPanel.vue'
import SystemPanel from '../components/monitoring/SystemPanel.vue'
import HealthBadge from '../components/monitoring/HealthBadge.vue'

const monitoringStore = useMonitoringStore()

const refreshInterval = ref(monitoringStore.runtimeConfig?.monitorRefreshMs ?? 10000)

const connectionData = ref<ConnectionMetrics | null>(null)
const connectionLoading = ref(false)
const connectionError = ref<string | null>(null)

const messageData = ref<MessageMetrics | null>(null)
const messageLoading = ref(false)
const messageError = ref<string | null>(null)

const subscriptionData = ref<SubscriptionMetrics | null>(null)
const subscriptionLoading = ref(false)
const subscriptionError = ref<string | null>(null)

const systemData = ref<SystemMetrics | null>(null)
const systemLoading = ref(false)
const systemError = ref<string | null>(null)

const healthStatus = ref<HealthStatus | null>(null)

const connectionListRef = ref<InstanceType<typeof ConnectionListPanel> | null>(null)

const fetchConnection = async () => {
  connectionLoading.value = true; connectionError.value = null
  try { connectionData.value = await getConnectionMetrics() } catch { connectionError.value = '连接指标加载失败' } finally { connectionLoading.value = false }
}
const fetchMessage = async () => {
  messageLoading.value = true; messageError.value = null
  try { messageData.value = await getMessageMetrics() } catch { messageError.value = '消息指标加载失败' } finally { messageLoading.value = false }
}
const fetchSubscription = async () => {
  subscriptionLoading.value = true; subscriptionError.value = null
  try { subscriptionData.value = await getSubscriptionMetrics() } catch { subscriptionError.value = '订阅指标加载失败' } finally { subscriptionLoading.value = false }
}
const fetchSystem = async () => {
  systemLoading.value = true; systemError.value = null
  try { systemData.value = await getSystemMetrics() } catch { systemError.value = '系统指标加载失败' } finally { systemLoading.value = false }
}
const fetchHealth = async () => {
  try { healthStatus.value = await getHealthStatus() } catch { /* ignore */ }
}

const { start, stop, resume } = usePolling({
  interval: refreshInterval,
  callback: () => Promise.all([
    monitoringStore.fetchOverview(),
    fetchConnection(),
    fetchMessage(),
    fetchSubscription(),
    fetchSystem(),
    fetchHealth()
  ])
})

const manualRefresh = async () => {
  await Promise.all([
    monitoringStore.fetchOverview(),
    fetchConnection(),
    fetchMessage(),
    fetchSubscription(),
    fetchSystem(),
    fetchHealth()
  ])
  if (!monitoringStore.isPaused) resume()
}

onMounted(() => {
  start()
  fetchConnection()
  fetchMessage()
  fetchSubscription()
  fetchSystem()
  fetchHealth()
})
onUnmounted(() => {
  stop()
})

const hasAlert = computed(() => {
  const m = monitoringStore.metrics
  if (!m) return false
  return (m.connectRejectedTotal ?? 0) + (m.aclSubscribeDenyTotal ?? 0) + (m.aclPublishDenyTotal ?? 0) > 0
})

const alertTotal = computed(() => {
  const m = monitoringStore.metrics
  if (!m) return 0
  return (m.connectRejectedTotal ?? 0) + (m.aclSubscribeDenyTotal ?? 0) + (m.aclPublishDenyTotal ?? 0)
})

const allMetrics = computed(() => {
  const m = monitoringStore.metrics
  return [
    { key: 'connectionsActive', label: '活跃连接', value: m?.connectionsActive, alert: false },
    { key: 'connectAcceptedTotal', label: '接受总数', value: m?.connectAcceptedTotal, alert: false },
    { key: 'connectRejectedTotal', label: '拒绝总数', value: m?.connectRejectedTotal, alert: (m?.connectRejectedTotal ?? 0) > 0 },
    { key: 'publishInTotal', label: '发布入总数', value: m?.publishInTotal, alert: false },
    { key: 'publishOutTotal', label: '发布出总数', value: m?.publishOutTotal, alert: false },
    { key: 'qos2InPending', label: 'QoS2 待完成', value: m?.qos2InPending, alert: false },
    { key: 'qos2InCompletedTotal', label: 'QoS2 完成', value: m?.qos2InCompletedTotal, alert: false },
    { key: 'aclSubscribeDenyTotal', label: 'ACL 订阅拒绝', value: m?.aclSubscribeDenyTotal, alert: (m?.aclSubscribeDenyTotal ?? 0) > 0 },
    { key: 'aclPublishDenyTotal', label: 'ACL 发布拒绝', value: m?.aclPublishDenyTotal, alert: (m?.aclPublishDenyTotal ?? 0) > 0 }
  ]
})
</script>

<style scoped>
.monitoring-page { animation: fadeIn 0.3s ease; }

.alert-banner {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 16px; border-radius: 10px; margin-bottom: 16px;
  font-size: 13px; font-weight: 500;
}
.alert-banner.warning { background: rgba(245,158,11,0.1); color: #f59e0b; border: 1px solid rgba(245,158,11,0.15); }
.alert-banner.error { background: rgba(239,68,68,0.08); color: #ef4444; border: 1px solid rgba(239,68,68,0.12); }

.push-status-bar {
  display: flex; align-items: center; gap: 12px;
  margin-bottom: 16px; font-size: 12px;
}
.push-mode { color: rgba(255,255,255,0.3); }

.health-row {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 16px; margin-bottom: 16px;
  background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.06);
  border-radius: 10px; flex-wrap: wrap;
}
.health-label { font-size: 13px; color: rgba(255,255,255,0.5); font-weight: 500; }
.health-components { display: flex; gap: 8px; flex-wrap: wrap; }
.comp-item { display: flex; align-items: center; gap: 6px; }
.comp-name { font-size: 12px; color: rgba(255,255,255,0.6); }

.stats-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 20px; }

.trends-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 20px; }
.trend-card {
  background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.06);
  border-radius: 12px; padding: 16px 18px; height: 180px;
  display: flex; flex-direction: column;
  transition: border-color 0.2s;
}
.trend-card:hover { border-color: rgba(255,255,255,0.1); }

.detail-panels {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 20px;
}

.stat-card {
  background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.06);
  border-radius: 12px; padding: 16px; display: flex; align-items: center; gap: 14px;
  transition: border-color 0.2s, transform 0.2s;
}
.stat-card:hover { border-color: rgba(255,255,255,0.1); transform: translateY(-1px); }
.stat-icon-wrap { width: 40px; height: 40px; border-radius: 10px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.blue-icon { background: rgba(77,109,255,0.12); }
.green-icon { background: rgba(16,185,129,0.12); }
.purple-icon { background: rgba(124,92,252,0.12); }
.red-icon { background: rgba(239,68,68,0.12); }
.slate-icon { background: rgba(100,116,139,0.12); }
.stat-info {}
.stat-value { font-size: 24px; font-weight: 700; color: #fff; font-family: 'SF Mono','Fira Code',monospace; line-height: 1; }
.text-red { color: #ef4444 !important; }
.stat-label { font-size: 12px; color: rgba(255,255,255,0.4); margin-top: 4px; }

.panels-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }

.panel {
  background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.06);
  border-radius: 12px; padding: 20px;
}
.panel-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.panel-title { font-size: 14px; font-weight: 600; color: rgba(255,255,255,0.85); }
.panel-sub { font-size: 11px; color: rgba(255,255,255,0.3); font-family: 'SF Mono','Fira Code',monospace; }
.panel-badge { font-size: 9px; font-weight: 700; padding: 2px 8px; border-radius: 4px; letter-spacing: 0.5px; }
.panel-badge.live { background: rgba(16,185,129,0.15); color: #10b981; }

.broker-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.broker-item, .info-row { display: flex; justify-content: space-between; align-items: center; padding: 10px 14px; background: rgba(255,255,255,0.03); border-radius: 8px; }
.broker-label, .info-label { font-size: 12px; color: rgba(255,255,255,0.4); }
.broker-val, .info-val { font-size: 14px; font-weight: 600; color: #fff; display: flex; align-items: center; gap: 6px; }
.broker-val.mono, .info-val.mono { font-family: 'SF Mono','Fira Code',monospace; }
.val-on, .info-val.val-on { color: #10b981; }
.val-off, .info-val.val-off { color: #64748b; }
.dot { width: 6px; height: 6px; border-radius: 50%; background: #64748b; }
.dot.on { background: #10b981; box-shadow: 0 0 6px rgba(16,185,129,0.4); }

.metrics-list { display: flex; flex-direction: column; gap: 2px; }
.metric-row {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 14px; border-radius: 8px; transition: background 0.15s;
}
.metric-row:hover { background: rgba(255,255,255,0.04); }
.metric-row.alert { background: rgba(239,68,68,0.06); }
.metric-name { font-size: 13px; color: rgba(255,255,255,0.5); flex: 1; }
.metric-val { font-size: 14px; font-weight: 600; color: #fff; }
.metric-row.alert .metric-val { color: #ef4444; }
.metric-alert-badge { font-size: 9px; font-weight: 700; color: #ef4444; background: rgba(239,68,68,0.1); padding: 1px 6px; border-radius: 4px; letter-spacing: 0.5px; }

.info-rows { display: flex; flex-direction: column; gap: 10px; }

.coll-stats { display: flex; align-items: center; justify-content: center; gap: 32px; padding: 12px 0; }
.coll-stat { text-align: center; flex: 1; }
.coll-num { font-size: 28px; font-weight: 700; color: #fff; font-family: 'SF Mono','Fira Code',monospace; }
.coll-label { font-size: 12px; color: rgba(255,255,255,0.4); margin-top: 4px; }
.coll-divider { width: 1px; height: 40px; background: rgba(255,255,255,0.08); }

@keyframes fadeIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }
</style>
