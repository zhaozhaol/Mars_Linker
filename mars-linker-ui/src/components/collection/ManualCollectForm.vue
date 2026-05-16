<template>
  <div class="manual-form">
    <div class="form-fields">
      <div class="field">
        <label class="field-label">类型</label>
        <input v-model="form.type" class="field-input" placeholder="custom" />
      </div>
      <div class="field">
        <label class="field-label">来源</label>
        <input v-model="form.source" class="field-input" placeholder="ui" />
      </div>
      <div class="field flex-1">
        <label class="field-label">负载</label>
        <input v-model="form.payload" class="field-input" placeholder="可选内容" />
      </div>
    </div>
    <button class="submit-btn" :class="{ loading: submitting }" :disabled="submitting" @click="handleSubmit">
      {{ submitting ? '提交中...' : '提交事件' }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue'

defineProps<{ submitting: boolean }>()
const emit = defineEmits<{ submit: [form: { type: string; source: string; payload: string }] }>()

const form = reactive({ type: 'custom', source: 'ui', payload: '' })

const handleSubmit = () => {
  emit('submit', { type: form.type || 'custom', source: form.source || 'ui', payload: form.payload })
}
</script>

<style scoped>
.manual-form { display: flex; align-items: flex-end; gap: 16px; }
.form-fields { display: flex; gap: 12px; flex: 1; }
.field { display: flex; flex-direction: column; gap: 4px; }
.field.flex-1 { flex: 1; }
.field-label { font-size: 11px; color: #8c8c9a; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; }
.field-input {
  padding: 8px 12px;
  border: 1.5px solid #e0e0ec;
  border-radius: 8px;
  font-size: 13px;
  color: #1a1a2e;
  background: #fff;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.field-input:focus { border-color: #6366f1; box-shadow: 0 0 0 3px rgba(99,102,241,0.1); }
.submit-btn {
  padding: 8px 20px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: opacity 0.2s;
}
.submit-btn:hover { opacity: 0.9; }
.submit-btn.loading { opacity: 0.6; cursor: wait; }
</style>
