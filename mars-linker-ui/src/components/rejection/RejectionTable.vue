<script setup lang="ts">
import type { RejectionMessage, RejectionType, PagedResult } from '../../types/rejection'
import { formatTimestamp } from '../../utils/formatter'

defineProps<{
  data: PagedResult<RejectionMessage> | null
}>()

const emit = defineEmits<{
  (e: 'remove', id: number): void
  (e: 'page-change', page: number): void
}>()

function typeBadgeClass(type: RejectionType): string {
  switch (type) {
    case 'connect_refused': return 'badge--connect'
    case 'acl_subscribe_denied': return 'badge--subscribe'
    case 'acl_publish_denied': return 'badge--publish'
  }
}

function typeLabel(type: RejectionType): string {
  switch (type) {
    case 'connect_refused': return '连接拒绝'
    case 'acl_subscribe_denied': return '订阅拒绝'
    case 'acl_publish_denied': return '发布拒绝'
  }
}

function reasonLabel(reason: string): string {
  const map: Record<string, string> = {
    protocol_name_invalid: '协议名无效',
    unsupported_protocol_level: '协议级别不支持',
    client_id_empty: 'ClientID为空',
    will_qos2_unsupported: 'Will QoS2不支持',
    auth_failed: '鉴权失败',
    acl_subscribe_denied: 'ACL订阅拒绝',
    acl_publish_denied: 'ACL发布拒绝'
  }
  return map[reason] || reason
}
</script>

<template>
  <div class="rejection-table">
    <table v-if="data && data.items.length > 0">
      <thead>
        <tr>
          <th>时间</th>
          <th>客户端ID</th>
          <th>拒绝类型</th>
          <th>拒绝原因</th>
          <th>远程地址</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="msg in data.items" :key="msg.id">
          <td class="td--mono">{{ formatTimestamp(msg.timestamp) }}</td>
          <td>{{ msg.clientId }}</td>
          <td><span :class="['badge', typeBadgeClass(msg.type)]">{{ typeLabel(msg.type) }}</span></td>
          <td>{{ reasonLabel(msg.reason) }}</td>
          <td class="td--mono">{{ msg.remoteAddress }}</td>
          <td><el-button type="danger" size="small" @click="emit('remove', msg.id)">清除</el-button></td>
        </tr>
      </tbody>
    </table>
    <div v-else class="empty-state">暂无拒绝消息</div>
    <div v-if="data && data.totalCount > 0" class="pagination">
      <el-pagination
        :current-page="data.page"
        :page-size="data.size"
        :total="data.totalCount"
        layout="prev, pager, next"
        @current-change="(p: number) => emit('page-change', p)"
      />
    </div>
  </div>
</template>

<style scoped>
.rejection-table { width: 100%; }
table {
  width: 100%;
  border-collapse: collapse;
  background: rgba(17, 24, 39, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  overflow: hidden;
}
th {
  text-align: left;
  padding: 12px 16px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  background: rgba(17, 24, 39, 0.4);
}
td {
  padding: 10px 16px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.85);
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
}
.td--mono { font-family: 'SF Mono', 'Fira Code', monospace; font-size: 12px; }
.badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}
.badge--connect { background: rgba(239, 68, 68, 0.15); color: #ef4444; }
.badge--subscribe { background: rgba(249, 115, 22, 0.15); color: #f97316; }
.badge--publish { background: rgba(234, 179, 8, 0.15); color: #eab308; }
.empty-state {
  text-align: center;
  padding: 40px;
  color: rgba(255, 255, 255, 0.4);
  font-size: 14px;
  background: rgba(17, 24, 39, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
}
.pagination {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
