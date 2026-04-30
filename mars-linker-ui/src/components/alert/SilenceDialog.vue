<template>
  <div v-if="visible" class="dialog-overlay" @click.self="$emit('close')">
    <div class="dialog-card">
      <div class="dialog-header">
        <span class="dialog-title">静默规则</span>
        <button class="close-btn" @click="$emit('close')">&times;</button>
      </div>
      <div class="dialog-body">
        <div class="form-row">
          <label class="form-label">静默时长（分钟）</label>
          <input v-model.number="durationMin" type="number" class="form-input" :min="1" :max="1440" placeholder="输入静默时长" />
        </div>
        <div class="form-hint">规则将在指定时间内暂停告警通知（1-1440 分钟）</div>
      </div>
      <div class="dialog-footer">
        <button class="btn-cancel" @click="$emit('close')">取消</button>
        <button class="btn-confirm" :disabled="!canConfirm || submitting" @click="handleSilence">
          {{ submitting ? '处理中...' : '确认静默' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { silenceRule } from '../../api/alert'

const props = defineProps<{
  visible: boolean
  ruleId: string
}>()

const emit = defineEmits<{
  silenced: []
  close: []
}>()

const durationMin = ref(30)
const submitting = ref(false)

const canConfirm = computed(() => durationMin.value >= 1 && durationMin.value <= 1440)

const handleSilence = async () => {
  if (!canConfirm.value || submitting.value) return
  submitting.value = true
  try {
    await silenceRule(props.ruleId, { durationMs: durationMin.value * 60 * 1000 })
    emit('silenced')
  } catch { /* error handled by interceptor */ }
  finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.dialog-overlay {
  position: fixed; top: 0; left: 0; width: 100%; height: 100%;
  background: rgba(0, 0, 0, 0.5); backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center;
  z-index: 1000;
}
.dialog-card {
  width: 400px; max-width: 90vw;
  background: #1a1a2e; border: 1px solid rgba(255,255,255,0.08);
  border-radius: 16px; padding: 24px;
  animation: fadeIn 0.2s ease;
}
.dialog-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; }
.dialog-title { font-size: 16px; font-weight: 600; color: #fff; }
.close-btn {
  background: none; border: none; color: rgba(255,255,255,0.4);
  font-size: 20px; cursor: pointer; padding: 0 4px;
  transition: color 0.15s;
}
.close-btn:hover { color: #fff; }
.dialog-body { display: flex; flex-direction: column; gap: 12px; }
.form-row { display: flex; flex-direction: column; gap: 6px; }
.form-label { font-size: 13px; color: rgba(255,255,255,0.6); font-weight: 500; }
.form-input {
  padding: 10px 14px; border: 1px solid rgba(255,255,255,0.1);
  border-radius: 8px; background: rgba(255,255,255,0.04);
  color: #fff; font-size: 14px; outline: none;
  transition: border-color 0.2s;
}
.form-input:focus { border-color: #4d6dff; }
.form-hint { font-size: 12px; color: rgba(255,255,255,0.3); }
.dialog-footer { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
.btn-cancel {
  padding: 8px 20px; border: 1px solid rgba(255,255,255,0.1);
  background: transparent; color: rgba(255,255,255,0.6);
  border-radius: 8px; font-size: 13px; cursor: pointer;
  transition: background 0.15s;
}
.btn-cancel:hover { background: rgba(255,255,255,0.06); }
.btn-confirm {
  padding: 8px 20px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff; border: none; border-radius: 8px;
  font-size: 13px; font-weight: 600; cursor: pointer;
  transition: opacity 0.2s;
}
.btn-confirm:disabled { opacity: 0.4; cursor: not-allowed; }
@keyframes fadeIn { from { opacity: 0; transform: scale(0.95); } to { opacity: 1; transform: scale(1); } }
</style>
