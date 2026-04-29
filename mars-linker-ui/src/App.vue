<template>
  <router-view v-if="isLoginPage" />
  <ModuleDisabled v-else-if="isModuleDisabled" @retry="retry" />
  <AppLayout v-else />
</template>

<script setup lang="ts">
import { inject, computed } from 'vue'
import type { Ref } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from './components/layout/AppLayout.vue'
import ModuleDisabled from './views/ModuleDisabled.vue'

const route = useRoute()
const moduleEnabled = inject<Ref<boolean | null>>('moduleEnabled')
const checkModuleStatus = inject<() => Promise<void>>('checkModuleStatus')

const isLoginPage = computed(() => route.path === '/login')
const isModuleDisabled = computed(() => moduleEnabled?.value === false)
const retry = () => checkModuleStatus?.()
</script>

<style>
*, *::before, *::after { margin: 0; padding: 0; box-sizing: border-box; }

body {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: #fff;
  background: #0a0e1a;
  overflow: hidden;
}

::-webkit-scrollbar { width: 5px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.08); border-radius: 3px; }
::-webkit-scrollbar-thumb:hover { background: rgba(255,255,255,0.15); }

::selection { background: rgba(77,109,255,0.25); color: #fff; }

input[type="number"]::-webkit-inner-spin-button,
input[type="number"]::-webkit-outer-spin-button { -webkit-appearance: none; margin: 0; }
input[type="number"] { -moz-appearance: textfield; }

.mono { font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', 'JetBrains Mono', monospace; }
</style>
