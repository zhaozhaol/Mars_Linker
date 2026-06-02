<template>
  <div class="config-form">
    <div class="form-row readonly">
      <span class="form-label">启用状态</span>
      <span class="form-value" :class="config?.enabled ? 'on' : 'off'">
        <span class="bool-dot" :class="{ on: config?.enabled }"></span>
        {{ formatBoolean(config?.enabled ?? false) }}
      </span>
    </div>
    <div class="form-row">
      <span class="form-label">采集模式</span>
      <div class="input-wrap">
        <input v-model="form.collectMode" class="modern-input" placeholder="请输入采集模式" />
      </div>
    </div>
    <div class="form-row">
      <span class="form-label">刷新间隔 (ms)</span>
      <div class="input-wrap">
        <input v-model.number="form.monitorRefreshMs" type="number" class="modern-input" :min="1000" :step="1000" />
        <span v-if="monitorRefreshMsError" class="field-error">{{ monitorRefreshMsError }}</span>
      </div>
    </div>
    <div class="form-row readonly">
      <span class="form-label">缓冲区大小</span>
      <span class="form-value mono">{{ config?.collectionBufferSize ?? '--' }}</span>
    </div>
    <div class="form-actions">
      <button class="save-btn" :class="{ disabled: !canSave }" :disabled="!canSave" @click="handleSave">保存变更</button>
      <button class="rollback-btn" @click="handleRollback">回滚</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, computed, watch } from 'vue'
import type { RuntimeConfig } from '../../types/monitoring'
import { formatBoolean } from '../../utils/formatter'

const props = defineProps<{ config: RuntimeConfig | null }>()
const emit = defineEmits<{ confirm: [form: { collectMode: string; monitorRefreshMs: number }]; rollback: []; saved: [] }>()

const form = reactive({
  collectMode: '',
  monitorRefreshMs: 10000
})

watch(() => props.config, (c) => {
  if (c) {
    form.collectMode = c.collectMode
    form.monitorRefreshMs = c.monitorRefreshMs
  }
}, { immediate: true })

const monitorRefreshMsError = computed(() => {
  if (!form.monitorRefreshMs || !Number.isInteger(form.monitorRefreshMs)) return '请输入正整数'
  if (form.monitorRefreshMs < 1000) return '不得小于 1000'
  return ''
})

const hasChanges = computed(() => {
  if (!props.config) return false
  return form.collectMode !== props.config.collectMode || form.monitorRefreshMs !== props.config.monitorRefreshMs
})

const canSave = computed(() => hasChanges.value && !monitorRefreshMsError.value && form.collectMode.trim() !== '')

const handleSave = () => {
  if (!canSave.value) return
  emit('confirm', { collectMode: form.collectMode, monitorRefreshMs: form.monitorRefreshMs })
  emit('saved')
}

const handleRollback = () => {
  emit('rollback')
  emit('saved')
}
</script>

<style scoped>
.config-form { display: flex; flex-direction: column; gap: 14px; }
.form-row { display: flex; align-items: center; gap: 12px; }
.form-row.readonly { padding: 12px 16px; background: #f8f8fc; border-radius: 10px; }
.form-label { font-size: 13px; color: #8c8c9a; font-weight: 500; min-width: 120px; }
.form-value { font-size: 14px; font-weight: 600; color: #1a1a2e; display: flex; align-items: center; gap: 6px; }
.form-value.mono { font-family: 'SF Mono', 'Fira Code', monospace; }
.form-value.on { color: #10b981; }
.form-value.off { color: #94a3b8; }
.bool-dot { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.input-wrap { flex: 1; display: flex; flex-direction: column; gap: 4px; }
.modern-input {
  width: 100%;
  max-width: 280px;
  padding: 10px 14px;
  border: 1.5px solid #e0e0ec;
  border-radius: 8px;
  font-size: 14px;
  color: #1a1a2e;
  background: #fff;
  transition: border-color 0.2s, box-shadow 0.2s;
  outline: none;
}
.modern-input:focus {
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}
.field-error { font-size: 12px; color: #ef4444; }
.form-actions { padding-top: 8px; }
.save-btn {
  padding: 10px 28px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s, transform 0.2s;
}
.save-btn:hover { opacity: 0.9; transform: translateY(-1px); }
.save-btn.disabled { opacity: 0.4; cursor: not-allowed; transform: none; }
.rollback-btn {
  padding: 10px 28px;
  background: transparent;
  color: #6366f1;
  border: 1.5px solid #6366f1;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
}
.rollback-btn:hover { background: rgba(99, 102, 241, 0.08); }
</style>
