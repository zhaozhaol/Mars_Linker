<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import RejectionSummaryCards from '../components/rejection/RejectionSummaryCards.vue'
import RejectionFilterBar from '../components/rejection/RejectionFilterBar.vue'
import RejectionTable from '../components/rejection/RejectionTable.vue'
import { fetchRejectionMessages, fetchRejectionSummary, removeRejectionMessage, removeAllRejectionMessages } from '../api/rejection'
import type { RejectionSummary, RejectionMessage, PagedResult, RejectionType, RejectionReason } from '../types/rejection'

const summary = ref<RejectionSummary>({ connectRefused: 0, aclSubscribeDenied: 0, aclPublishDenied: 0, total: 0 })
const messages = ref<PagedResult<RejectionMessage> | null>(null)
const loading = ref(false)

const currentPage = ref(1)
const pageSize = ref(20)
const filterType = ref<RejectionType | null>(null)
const filterReason = ref<RejectionReason | null>(null)

let refreshTimer: ReturnType<typeof setInterval> | null = null

async function loadSummary() {
  try {
    summary.value = await fetchRejectionSummary()
  } catch (e: any) {
    console.warn('加载拒绝消息摘要失败', e)
  }
}

async function loadMessages() {
  loading.value = true
  try {
    messages.value = await fetchRejectionMessages({
      page: currentPage.value,
      size: pageSize.value,
      type: filterType.value || undefined,
      reason: filterReason.value || undefined
    })
  } catch (e: any) {
    console.warn('加载拒绝消息列表失败', e)
  } finally {
    loading.value = false
  }
}

async function refresh() {
  await Promise.all([loadSummary(), loadMessages()])
}

function onFilterChange(payload: { type: RejectionType | null; reason: RejectionReason | null }) {
  filterType.value = payload.type
  filterReason.value = payload.reason
  currentPage.value = 1
  loadMessages()
}

function onPageChange(page: number) {
  currentPage.value = page
  loadMessages()
}

async function onRemove(id: number) {
  try {
    await ElMessageBox.confirm('确认清除该条拒绝消息？', '确认', { type: 'warning' })
    await removeRejectionMessage(id)
    ElMessage.success('已清除')
    await refresh()
  } catch {
    // cancelled or error
  }
}

async function onClearAll(type: RejectionType | null) {
  try {
    const label = type ? '选中类型的' : '所有'
    await ElMessageBox.confirm(`确认清除${label}拒绝消息？`, '确认', { type: 'warning' })
    const result = await removeAllRejectionMessages(type || undefined)
    ElMessage.success(`已清除 ${result.removed} 条`)
    currentPage.value = 1
    await refresh()
  } catch {
    // cancelled or error
  }
}

onMounted(() => {
  refresh()
  refreshTimer = setInterval(refresh, 10000)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<template>
  <div class="rejection-page">
    <RejectionSummaryCards :summary="summary" />
    <RejectionFilterBar @filter-change="onFilterChange" @clear-all="onClearAll" />
    <div v-if="loading" class="loading-state">加载中...</div>
    <RejectionTable v-else :data="messages" @remove="onRemove" @page-change="onPageChange" />
  </div>
</template>

<style scoped>
.rejection-page {
  padding: 24px;
  animation: fadeIn 0.3s ease;
}
.loading-state {
  text-align: center;
  padding: 40px;
  color: rgba(255, 255, 255, 0.4);
  font-size: 14px;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
