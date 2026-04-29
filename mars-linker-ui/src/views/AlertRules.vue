<template>
  <div class="alert-page">
    <div class="page-header">
      <h2 class="page-title">告警规则</h2>
      <button class="ml-btn ml-btn-primary" @click="showForm = true">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        新建规则
      </button>
    </div>

    <div v-if="store.rules.length === 0 && !showForm" class="empty-state">
      <span class="empty-icon">
        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>
      </span>
      <span class="empty-text">暂无告警规则，点击上方按钮新建</span>
    </div>

    <div v-else class="rules-list">
      <div v-for="rule in store.rules" :key="rule.id" class="rule-card" :class="{ disabled: !rule.enabled }">
        <div class="rule-left">
          <div class="rule-name-row">
            <span class="rule-name">{{ rule.name }}</span>
            <span class="rule-badge" :class="rule.enabled ? 'on' : 'off'">{{ rule.enabled ? '启用' : '禁用' }}</span>
          </div>
          <div class="rule-detail">
            <span class="detail-metric">{{ rule.metric }}</span>
            <span class="detail-op">{{ rule.operator }}</span>
            <span class="detail-threshold">{{ rule.threshold }}</span>
            <span class="detail-duration">持续 {{ rule.durationSeconds }}s</span>
          </div>
        </div>
        <div class="rule-actions">
          <button class="ml-btn ml-btn-ghost ml-btn-icon" @click="toggleRule(rule)" :title="rule.enabled ? '禁用' : '启用'">
            <svg v-if="rule.enabled" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/></svg>
            <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
          </button>
          <button class="ml-btn ml-btn-danger ml-btn-icon" @click="handleDelete(rule.id)" title="删除">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
          </button>
        </div>
      </div>
    </div>

    <div v-if="showForm" class="form-overlay" @click.self="showForm = false">
      <div class="form-card">
        <h3 class="form-title">{{ editingRule ? '编辑规则' : '新建告警规则' }}</h3>
        <div class="form-grid">
          <div class="field">
            <label class="field-label">名称</label>
            <input v-model="form.name" class="field-input" placeholder="如：堆内存过高" />
          </div>
          <div class="field">
            <label class="field-label">指标</label>
            <select v-model="form.metric" class="field-input">
              <option v-for="m in availableMetrics" :key="m" :value="m">{{ m }}</option>
            </select>
          </div>
          <div class="field">
            <label class="field-label">比较运算符</label>
            <select v-model="form.operator" class="field-input">
              <option value=">">大于 (>)</option>
              <option value=">=">大于等于 (>=)</option>
              <option value="<">小于 (<)</option>
              <option value="<=">小于等于 (<=)</option>
              <option value="==">等于 (==)</option>
            </select>
          </div>
          <div class="field">
            <label class="field-label">阈值</label>
            <input v-model.number="form.threshold" class="field-input" type="number" step="any" />
          </div>
          <div class="field">
            <label class="field-label">持续时间（秒）</label>
            <input v-model.number="form.durationSeconds" class="field-input" type="number" min="0" />
          </div>
        </div>
        <div class="form-actions">
          <button class="ml-btn ml-btn-ghost" @click="showForm = false">取消</button>
          <button class="ml-btn ml-btn-primary" @click="handleSubmit" :disabled="!form.name || !form.metric">确认</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useAlertStore } from '../stores/alert'
import type { AlertRule } from '../types/alert'

const store = useAlertStore()
const showForm = ref(false)
const editingRule = ref<AlertRule | null>(null)

const availableMetrics = [
  'connections.active',
  'connect.accepted.total',
  'connect.rejected.total',
  'publish.in.total',
  'publish.out.total',
  'acl.subscribe.deny.total',
  'acl.publish.deny.total',
  'heap.usage.percent',
  'heap.used.bytes',
  'cpu.process.percent',
  'cpu.system.percent',
  'cpu.system.load'
]

const form = reactive({
  name: '',
  metric: 'heap.usage.percent',
  operator: '>',
  threshold: 80,
  durationSeconds: 30,
  enabled: true
})

const resetForm = () => {
  form.name = ''
  form.metric = 'heap.usage.percent'
  form.operator = '>'
  form.threshold = 80
  form.durationSeconds = 30
  form.enabled = true
  editingRule.value = null
}

const handleSubmit = async () => {
  try {
    if (editingRule.value) {
      await store.editRule(editingRule.value.id, { ...form })
      ElMessage.success('规则已更新')
    } else {
      await store.addRule({ ...form })
      ElMessage.success('规则已创建')
    }
    showForm.value = false
    resetForm()
  } catch {
    ElMessage.error('操作失败')
  }
}

const toggleRule = async (rule: AlertRule) => {
  try {
    await store.editRule(rule.id, { enabled: !rule.enabled })
  } catch {
    ElMessage.error('操作失败')
  }
}

const handleDelete = async (id: string) => {
  try {
    await store.removeRule(id)
    ElMessage.success('规则已删除')
  } catch {
    ElMessage.error('删除失败')
  }
}

onMounted(store.fetchRules)
</script>

<style scoped>
.alert-page { animation: fadeIn 0.3s ease; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; }
.page-title { font-size: 18px; font-weight: 600; color: #fff; margin: 0; }

.empty-state { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 60px 0; }
.empty-icon { color: rgba(255,255,255,0.15); }
.empty-text { font-size: 14px; color: rgba(255,255,255,0.3); }

.rules-list { display: flex; flex-direction: column; gap: 8px; }
.rule-card {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 20px; background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.06);
  border-radius: 12px; transition: all 0.2s;
}
.rule-card:hover { background: rgba(255,255,255,0.05); }
.rule-card.disabled { opacity: 0.5; }

.rule-left { display: flex; flex-direction: column; gap: 6px; }
.rule-name-row { display: flex; align-items: center; gap: 8px; }
.rule-name { font-size: 14px; font-weight: 600; color: #fff; }
.rule-badge {
  padding: 2px 8px; border-radius: 6px; font-size: 10px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px;
}
.rule-badge.on { background: rgba(16,185,129,0.15); color: #10b981; }
.rule-badge.off { background: rgba(100,116,139,0.15); color: #64748b; }

.rule-detail { display: flex; align-items: center; gap: 6px; }
.detail-metric { font-size: 12px; color: #7c5cfc; font-family: 'SF Mono','Fira Code',monospace; }
.detail-op { font-size: 12px; color: rgba(255,255,255,0.5); font-family: 'SF Mono','Fira Code',monospace; }
.detail-threshold { font-size: 12px; color: #4d6dff; font-weight: 600; font-family: 'SF Mono','Fira Code',monospace; }
.detail-duration { font-size: 11px; color: rgba(255,255,255,0.3); margin-left: 4px; }

.rule-actions { display: flex; gap: 4px; }

.form-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.6); backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center; z-index: 50; animation: fadeIn 0.2s ease;
}
.form-card {
  width: 480px; background: #111827; border: 1px solid rgba(255,255,255,0.1);
  border-radius: 16px; padding: 28px; box-shadow: 0 20px 60px rgba(0,0,0,0.5);
}
.form-title { font-size: 16px; font-weight: 600; color: #fff; margin: 0 0 20px; }
.form-grid { display: flex; flex-direction: column; gap: 14px; }
.field { display: flex; flex-direction: column; gap: 4px; }
.field-label { font-size: 11px; color: rgba(255,255,255,0.35); font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; }
.field-input {
  padding: 10px 14px; border: 1px solid rgba(255,255,255,0.08); border-radius: 8px;
  font-size: 13px; color: #fff; background: rgba(255,255,255,0.04); outline: none; transition: border-color 0.2s;
}
.field-input:focus { border-color: #4d6dff; box-shadow: 0 0 0 3px rgba(77,109,255,0.15); }
select.field-input { appearance: none; cursor: pointer; }
select.field-input option { background: #111827; color: #fff; }

.form-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 20px; }

@keyframes fadeIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }
</style>
