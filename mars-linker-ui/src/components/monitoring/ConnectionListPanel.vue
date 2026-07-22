<template>
  <div class="panel connection-list-panel">
    <div class="panel-header">
      <div class="header-left">
        <div class="card-icon conn-icon">CL</div>
        <span class="panel-title">在线连接管理</span>
        <span v-if="total > 0" class="panel-count">共 {{ total }} 个</span>
      </div>
      <div class="header-right">
        <button class="refresh-btn" :disabled="loading" @click="fetchClients">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            :class="{ spinning: loading }">
            <path d="M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8" />
            <path d="M21 3v5h-5" />
          </svg>
          <span>刷新</span>
        </button>
      </div>
    </div>

    <div v-if="error" class="error-state">
      <span>{{ error }}</span>
      <button class="retry-btn" @click="fetchClients">重试</button>
    </div>

    <div v-else-if="loading && clients.length === 0" class="loading-state">
      <div class="skeleton-row" v-for="i in 5" :key="i"></div>
    </div>

    <div v-else-if="clients.length === 0" class="empty-state">
      <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
        <circle cx="9" cy="7" r="4" />
        <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
        <path d="M16 3.13a4 4 0 0 1 0 7.75" />
      </svg>
      <span>暂无在线连接</span>
    </div>

    <div v-else class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>客户端 ID</th>
            <th>远程地址</th>
            <th>协议</th>
            <th>Clean</th>
            <th>KeepAlive</th>
            <th>连接时长</th>
            <th>空闲</th>
            <th>订阅</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="client in clients" :key="client.clientId"
            :class="{ 'row-disconnecting': disconnecting === client.clientId }">
            <td class="td-clientId">
              <span class="client-id-text" :title="client.clientId">{{ client.clientId }}</span>
            </td>
            <td class="td-mono">{{ formatAddress(client.remoteAddress) }}</td>
            <td>
              <span class="badge" :class="protocolBadgeClass(client.protocolLevel)">
                {{ protocolLabel(client.protocolLevel) }}
              </span>
            </td>
            <td>
              <span class="dot" :class="{ on: client.cleanSession }"></span>
              {{ client.cleanSession ? '是' : '否' }}
            </td>
            <td class="td-mono">{{ client.keepAliveSeconds > 0 ? client.keepAliveSeconds + 's' : '-' }}</td>
            <td class="td-mono">{{ formatDuration(client.connectedDurationMs) }}</td>
            <td class="td-mono" :class="{ 'idle-warn': client.idleMs > 60000 }">
              {{ formatDuration(client.idleMs) }}
            </td>
            <td class="td-mono">{{ client.subscriptionCount }}</td>
            <td>
              <button class="kick-btn"
                :disabled="disconnecting === client.clientId"
                @click="handleDisconnect(client.clientId)">
                <svg v-if="disconnecting === client.clientId" class="spin" width="12" height="12" viewBox="0 0 24 24"
                  fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 12a9 9 0 1 1-6.219-8.56" />
                </svg>
                <svg v-else width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                  stroke-width="2">
                  <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                  <polyline points="16 17 21 12 16 7" />
                  <line x1="21" y1="12" x2="9" y2="12" />
                </svg>
                {{ disconnecting === client.clientId ? '剔除中' : '剔除' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <div v-if="total > size" class="pagination">
        <el-pagination :current-page="page" :page-size="size" :total="total" layout="prev, pager, next"
          @current-change="handlePageChange" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOnlineClients, disconnectClient } from '../../api/monitoring'
import type { OnlineClientInfo } from '../../types/monitoring'

const clients = ref<OnlineClientInfo[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(50)
const loading = ref(false)
const error = ref<string | null>(null)
const disconnecting = ref<string | null>(null)

async function fetchClients() {
  loading.value = true
  error.value = null
  try {
    const result = await getOnlineClients({ page: page.value, size: size.value })
    clients.value = result.clients || []
    total.value = result.total || 0
  } catch (e: any) {
    error.value = e?.message || '加载在线连接列表失败'
  } finally {
    loading.value = false
  }
}

async function handleDisconnect(clientId: string) {
  try {
    await ElMessageBox.confirm(
      `确认剔除客户端 "${clientId}" ？该操作会立即断开其 TCP 连接，不发布遗嘱消息。`,
      '剔除连接确认',
      {
        confirmButtonText: '确认剔除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch {
    return // 用户取消
  }

  disconnecting.value = clientId
  try {
    await disconnectClient(clientId, 'kicked_by_admin')
    ElMessage.success(`客户端 ${clientId} 已剔除`)
    // 剔除成功后刷新列表
    await fetchClients()
  } catch (e: any) {
    ElMessage.error(e?.message || `剔除客户端 ${clientId} 失败`)
  } finally {
    disconnecting.value = null
  }
}

function handlePageChange(p: number) {
  page.value = p
  fetchClients()
}

function protocolLabel(level: number): string {
  switch (level) {
    case 4: return '3.1.1'
    case 5: return '5.0'
    default: return `L${level}`
  }
}

function protocolBadgeClass(level: number): string {
  switch (level) {
    case 5: return 'badge--v5'
    case 4: return 'badge--v4'
    default: return 'badge--other'
  }
}

function formatAddress(addr: string): string {
  if (!addr) return '-'
  // 去掉前缀 / 或 /0:0:0:0:0:0:0:0:1
  return addr.replace(/^\//, '')
}

function formatDuration(ms: number): string {
  if (!ms || ms <= 0) return '-'
  const seconds = Math.floor(ms / 1000)
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}m${seconds % 60}s`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h${minutes % 60}m`
  const days = Math.floor(hours / 24)
  return `${days}d${hours % 24}h`
}

onMounted(() => {
  fetchClients()
})

defineExpose({ fetchClients })
</script>

<style scoped>
.connection-list-panel {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  padding: 20px;
  transition: border-color 0.25s;
}
.connection-list-panel:hover {
  border-color: rgba(255, 255, 255, 0.1);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.card-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 12px;
  color: #fff;
}
.conn-icon {
  background: linear-gradient(135deg, #ef4444, #f97316);
}
.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.85);
}
.panel-count {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
  background: rgba(255, 255, 255, 0.06);
  padding: 2px 8px;
  border-radius: 4px;
}

.refresh-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.04);
  color: rgba(255, 255, 255, 0.7);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.refresh-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
}
.refresh-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.spinning {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

.error-state {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 24px;
  color: #ef4444;
  font-size: 13px;
  background: rgba(239, 68, 68, 0.06);
  border-radius: 10px;
}
.retry-btn {
  padding: 4px 12px;
  border-radius: 6px;
  border: 1px solid rgba(239, 68, 68, 0.3);
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
  font-size: 12px;
  cursor: pointer;
}
.retry-btn:hover {
  background: rgba(239, 68, 68, 0.15);
}

.loading-state {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.skeleton-row {
  height: 38px;
  background: rgba(255, 255, 255, 0.04);
  border-radius: 8px;
  animation: shimmer 1.5s infinite;
}
@keyframes shimmer {
  0% { opacity: 0.3; }
  50% { opacity: 0.6; }
  100% { opacity: 0.3; }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 48px 20px;
  color: rgba(255, 255, 255, 0.3);
  font-size: 14px;
}

.table-wrap {
  overflow-x: auto;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th {
  text-align: left;
  padding: 10px 12px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.4);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  white-space: nowrap;
}
td {
  padding: 9px 12px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.85);
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
  white-space: nowrap;
}
tbody tr {
  transition: background 0.15s;
}
tbody tr:hover {
  background: rgba(255, 255, 255, 0.03);
}
.row-disconnecting {
  opacity: 0.5;
}

.td-mono {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 12px;
}
.td-clientId {
  max-width: 220px;
}
.client-id-text {
  display: inline-block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 12px;
}

.badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}
.badge--v5 { background: rgba(124, 92, 252, 0.15); color: #7c5cfc; }
.badge--v4 { background: rgba(77, 109, 255, 0.15); color: #4d6dff; }
.badge--other { background: rgba(100, 116, 139, 0.15); color: #64748b; }

.dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #64748b;
  margin-right: 4px;
  vertical-align: middle;
}
.dot.on { background: #10b981; box-shadow: 0 0 6px rgba(16, 189, 129, 0.4); }

.idle-warn { color: #f59e0b; }

.kick-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 6px;
  border: 1px solid rgba(239, 68, 68, 0.25);
  background: rgba(239, 68, 68, 0.08);
  color: #ef4444;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}
.kick-btn:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.15);
  border-color: rgba(239, 68, 68, 0.4);
}
.kick-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.spin {
  animation: spin 0.8s linear infinite;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
