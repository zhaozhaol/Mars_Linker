<template>
  <div class="runtime-page">
    <div v-if="configStore.runtimeLoading" class="skeleton">
      <div v-for="i in 3" :key="i" class="skeleton-row"></div>
    </div>
    <template v-else-if="configStore.runtimeConfig">
      <div class="config-section">
        <h3 class="section-title">只读参数</h3>
        <div class="readonly-row">
          <div class="config-row">
            <div class="config-row-left">
              <span class="config-icon"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg></span>
              <span class="config-name">启用状态</span>
            </div>
            <span class="config-val" :class="configStore.runtimeConfig.enabled ? 'val-on' : 'val-off'">
              <span class="dot" :class="{ on: configStore.runtimeConfig.enabled }"></span>
              {{ formatBoolean(configStore.runtimeConfig.enabled) }}
            </span>
          </div>
          <div class="config-row">
            <div class="config-row-left">
              <span class="config-icon"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="4" y="4" width="16" height="16" rx="2"/><path d="M4 12h16"/></svg></span>
              <span class="config-name">缓冲区大小</span>
            </div>
            <span class="config-val mono">{{ configStore.runtimeConfig.collectionBufferSize }}</span>
          </div>
        </div>
      </div>
      <div class="config-section">
        <h3 class="section-title">可编辑参数</h3>
        <div class="editable-rows">
          <div class="edit-row">
            <div class="edit-left">
              <span class="edit-label">采集模式</span>
              <span class="edit-hint">当前：{{ configStore.runtimeConfig.collectMode }}</span>
            </div>
            <input v-model="form.collectMode" class="edit-input" placeholder="请输入采集模式" />
          </div>
          <div class="edit-row">
            <div class="edit-left">
              <span class="edit-label">刷新间隔 (ms)</span>
              <span class="edit-hint">当前：{{ configStore.runtimeConfig.monitorRefreshMs }} ms</span>
            </div>
            <div class="edit-input-wrap">
              <input v-model.number="form.monitorRefreshMs" type="number" class="edit-input" :min="1000" :step="1000" />
              <span v-if="refreshError" class="field-error">{{ refreshError }}</span>
            </div>
          </div>
        </div>
        <div class="form-actions">
          <button class="ml-btn ml-btn-primary" :disabled="!canSave" @click="handleSave">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
            保存变更
          </button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { reactive, computed, watch, onMounted } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useConfigStore } from '../stores/config'
import { formatBoolean } from '../utils/formatter'

const configStore = useConfigStore()

const form = reactive({ collectMode: '', monitorRefreshMs: 5000 })

watch(() => configStore.runtimeConfig, (c) => {
  if (c) { form.collectMode = c.collectMode; form.monitorRefreshMs = c.monitorRefreshMs }
}, { immediate: true })

const refreshError = computed(() => {
  if (!form.monitorRefreshMs || !Number.isInteger(form.monitorRefreshMs)) return '请输入正整数'
  if (form.monitorRefreshMs < 1000) return '不得小于 1000'
  return ''
})

const hasChanges = computed(() => {
  const c = configStore.runtimeConfig
  if (!c) return false
  return form.collectMode !== c.collectMode || form.monitorRefreshMs !== c.monitorRefreshMs
})

const canSave = computed(() => hasChanges.value && !refreshError.value && form.collectMode.trim() !== '')

const handleSave = async () => {
  if (!canSave.value) return
  const current = configStore.runtimeConfig!
  const changes: string[] = []
  if (form.collectMode !== current.collectMode) changes.push(`采集模式：${current.collectMode} → ${form.collectMode}`)
  if (form.monitorRefreshMs !== current.monitorRefreshMs) changes.push(`刷新间隔：${current.monitorRefreshMs} ms → ${form.monitorRefreshMs} ms`)
  try {
    await ElMessageBox.confirm(`即将修改以下配置：\n${changes.join('\n')}`, '确认修改', { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' })
  } catch { return }
  try {
    await configStore.doUpdateRuntimeConfig({ collectMode: form.collectMode, monitorRefreshMs: form.monitorRefreshMs })
    ElMessage.success('配置已更新')
  } catch { ElMessage.error('配置更新失败，请重试') }
}

onMounted(() => configStore.fetchRuntimeConfig())
</script>

<style scoped>
.runtime-page { animation: fadeIn 0.3s ease; }
.config-section { margin-bottom: 24px; }
.section-title { font-size: 13px; font-weight: 600; color: rgba(255,255,255,0.4); text-transform: uppercase; letter-spacing: 0.5px; margin: 0 0 12px; }
.readonly-row { display: flex; flex-direction: column; gap: 4px; }
.config-row { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.04); border-radius: 10px; }
.config-row-left { display: flex; align-items: center; gap: 10px; }
.config-icon { color: rgba(255,255,255,0.3); display: flex; align-items: center; }
.config-name { font-size: 13px; color: rgba(255,255,255,0.65); font-weight: 500; }
.config-val { font-size: 14px; font-weight: 600; color: #fff; display: flex; align-items: center; gap: 6px; }
.config-val.mono { font-family: 'SF Mono','Fira Code',monospace; }
.val-on { color: #10b981; }
.val-off { color: #64748b; }
.dot { width: 6px; height: 6px; border-radius: 50%; background: #64748b; }
.dot.on { background: #10b981; box-shadow: 0 0 6px rgba(16,185,129,0.4); }

.editable-rows { display: flex; flex-direction: column; gap: 16px; }
.edit-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.edit-left { display: flex; flex-direction: column; gap: 2px; }
.edit-label { font-size: 13px; color: rgba(255,255,255,0.65); font-weight: 500; }
.edit-hint { font-size: 11px; color: rgba(255,255,255,0.3); font-family: 'SF Mono','Fira Code',monospace; }
.edit-input-wrap { display: flex; flex-direction: column; gap: 4px; }
.edit-input { width: 220px; padding: 10px 14px; border: 1px solid rgba(255,255,255,0.08); border-radius: 8px; font-size: 13px; color: #fff; background: rgba(255,255,255,0.04); outline: none; transition: border-color 0.2s, box-shadow 0.2s; }
.edit-input:focus { border-color: #4d6dff; box-shadow: 0 0 0 3px rgba(77,109,255,0.15); }
.field-error { font-size: 11px; color: #ef4444; }

.form-actions { margin-top: 20px; }

.skeleton {}
.skeleton-row { height: 44px; background: rgba(255,255,255,0.04); border-radius: 10px; margin-bottom: 8px; animation: shimmer 1.5s infinite; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }
@keyframes shimmer { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
</style>
