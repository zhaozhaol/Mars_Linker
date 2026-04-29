<template>
  <div class="module-disabled">
    <div class="disabled-card">
      <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round">
        <circle cx="12" cy="12" r="10"/>
        <line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/>
      </svg>
      <h2>管理模块未启用</h2>
      <p>请在配置中开启 <code>mars.linker.ui.enabled=true</code></p>
      <button @click="retry">重新检测</button>
    </div>
  </div>
</template>

<script setup lang="ts">
const emit = defineEmits<{ retry: [] }>()
const retry = () => emit('retry')
</script>

<style scoped>
.module-disabled { display: flex; justify-content: center; align-items: center; height: 100vh; background: #0a0e1a; }
.disabled-card { text-align: center; color: rgba(255,255,255,0.35); }
.disabled-card h2 { font-size: 18px; font-weight: 600; color: #fff; margin: 16px 0 8px; }
.disabled-card p { font-size: 13px; color: rgba(255,255,255,0.5); margin: 0 0 20px; }
.disabled-card code { background: rgba(77,109,255,0.15); color: #4d6dff; padding: 2px 8px; border-radius: 4px; font-size: 12px; }
.disabled-card button { padding: 10px 28px; background: linear-gradient(135deg, #4d6dff, #7c5cfc); color: #fff; border: none; border-radius: 8px; font-size: 14px; font-weight: 600; cursor: pointer; transition: opacity 0.2s; }
.disabled-card button:hover { opacity: 0.9; }
</style>
