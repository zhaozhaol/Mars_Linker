<template>
  <div class="error-placeholder">
    <span class="error-msg">{{ message }}</span>
    <button class="retry-btn" @click="$emit('retry')">重试</button>
  </div>
</template>

<script setup lang="ts">
defineProps<{ message: string }>()
defineEmits<{ retry: [] }>()
</script>

<style scoped>
.error-placeholder {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: rgba(239, 68, 68, 0.06);
  border: 1px solid rgba(239, 68, 68, 0.12);
  border-radius: 10px;
}
.error-msg { font-size: 13px; color: #ef4444; font-weight: 500; }
.retry-btn {
  padding: 5px 14px;
  border: 1px solid rgba(239, 68, 68, 0.3);
  background: rgba(239, 68, 68, 0.08);
  color: #ef4444;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
}
.retry-btn:hover { background: rgba(239, 68, 68, 0.15); }
</style>
