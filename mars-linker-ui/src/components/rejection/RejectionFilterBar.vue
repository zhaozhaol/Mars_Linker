<script setup lang="ts">
import { ref, watch } from 'vue'
import type { RejectionType, RejectionReason } from '../../types/rejection'

const emit = defineEmits<{
  (e: 'filter-change', payload: { type: RejectionType | null; reason: RejectionReason | null }): void
  (e: 'clear-all', type: RejectionType | null): void
}>()

const selectedType = ref<RejectionType | ''>('')
const selectedReason = ref<RejectionReason | ''>('')

const typeOptions: { value: RejectionType; label: string }[] = [
  { value: 'connect_refused', label: '连接拒绝' },
  { value: 'acl_subscribe_denied', label: 'ACL 订阅拒绝' },
  { value: 'acl_publish_denied', label: 'ACL 发布拒绝' }
]

const reasonOptions: { value: RejectionReason; label: string }[] = [
  { value: 'protocol_name_invalid', label: '协议名无效' },
  { value: 'unsupported_protocol_level', label: '协议级别不支持' },
  { value: 'client_id_empty', label: 'ClientID 为空' },
  { value: 'will_qos2_unsupported', label: 'Will QoS2 不支持' },
  { value: 'auth_failed', label: '鉴权失败' },
  { value: 'acl_subscribe_denied', label: 'ACL 订阅拒绝' },
  { value: 'acl_publish_denied', label: 'ACL 发布拒绝' }
]

function onFilterChange() {
  emit('filter-change', {
    type: selectedType.value || null,
    reason: selectedReason.value || null
  })
}

function onClearAll() {
  emit('clear-all', selectedType.value || null)
}

watch([selectedType, selectedReason], onFilterChange)
</script>

<template>
  <div class="filter-bar">
    <div class="filter-bar__selectors">
      <el-select v-model="selectedType" placeholder="拒绝类型" clearable size="default" style="width: 160px">
        <el-option v-for="opt in typeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-select v-model="selectedReason" placeholder="拒绝原因" clearable size="default" style="width: 180px">
        <el-option v-for="opt in reasonOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
    </div>
    <el-button type="danger" @click="onClearAll">全部清除</el-button>
  </div>
</template>

<style scoped>
.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: rgba(17, 24, 39, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
}
.filter-bar__selectors {
  display: flex;
  gap: 12px;
}
</style>
