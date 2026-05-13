<template>
  <div class="login-page">
    <div class="bg-orbs">
      <div class="orb orb-1"></div>
      <div class="orb orb-2"></div>
      <div class="orb orb-3"></div>
    </div>
    <div class="login-card">
      <div class="login-header">
        <div class="login-logo">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round">
            <path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/>
          </svg>
        </div>
        <h1 class="login-title">Mars Linker</h1>
        <p class="login-subtitle">运维管理控制台</p>
      </div>
      <form class="login-form" @submit.prevent="handleLogin">
        <div class="input-group">
          <svg class="input-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
          <input
            v-model="form.username"
            type="text"
            class="login-input"
            placeholder="用户名"
            autocomplete="username"
            required
          />
        </div>
        <div class="input-group">
          <svg class="input-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
          <input
            v-model="form.password"
            type="password"
            class="login-input"
            placeholder="密码"
            autocomplete="current-password"
            required
          />
        </div>
        <div class="login-options">
          <label class="remember-me">
            <input v-model="form.remember" type="checkbox" />
            <span>记住我</span>
          </label>
        </div>
        <button type="submit" class="login-btn" :disabled="loading">
          <span v-if="loading" class="btn-spinner"></span>
          {{ loading ? '登录中...' : '登 录' }}
        </button>
      </form>
      <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const form = reactive({ username: '', password: '', remember: false })
const loading = ref(false)
const errorMsg = ref('')

const handleLogin = async () => {
  if (!form.username.trim() || !form.password.trim()) {
    errorMsg.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const resp = await fetch('/api/ui/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: form.username, password: form.password })
    })
    if (!resp.ok) {
      const data = await resp.json().catch(() => ({}))
      throw new Error(data.error || '登录失败')
    }
    const data = await resp.json()
    authStore.login(form.username, data.accessToken, data.refreshToken, data.expiresIn)
    router.push('/')
  } catch (e: any) {
    errorMsg.value = e.message || '登录失败，请检查用户名和密码'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  position: fixed; top: 0; left: 0; width: 100%; height: 100%;
  background: #0a0e1a;
  display: flex; justify-content: center; align-items: center;
  overflow: hidden;
}

.bg-orbs { position: absolute; top: 0; left: 0; width: 100%; height: 100%; pointer-events: none; }
.orb { position: absolute; border-radius: 50%; filter: blur(80px); opacity: 0.3; }
.orb-1 { width: 400px; height: 400px; background: #4d6dff; top: -10%; left: -5%; animation: float1 12s ease-in-out infinite; }
.orb-2 { width: 300px; height: 300px; background: #7c5cfc; bottom: -5%; right: -5%; animation: float2 10s ease-in-out infinite; }
.orb-3 { width: 200px; height: 200px; background: #0ea5e9; top: 50%; left: 60%; animation: float3 14s ease-in-out infinite; }

@keyframes float1 { 0%, 100% { transform: translate(0, 0); } 50% { transform: translate(40px, 30px); } }
@keyframes float2 { 0%, 100% { transform: translate(0, 0); } 50% { transform: translate(-30px, -40px); } }
@keyframes float3 { 0%, 100% { transform: translate(0, 0); } 50% { transform: translate(-20px, 20px); } }

.login-card {
  position: relative; z-index: 1;
  width: 380px; max-width: 90vw;
  background: rgba(255,255,255,0.04);
  backdrop-filter: blur(24px);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 20px;
  padding: 40px 36px;
  animation: cardIn 0.5s ease;
}

@keyframes cardIn { from { opacity: 0; transform: translateY(20px) scale(0.97); } to { opacity: 1; transform: translateY(0) scale(1); } }

.login-header { text-align: center; margin-bottom: 32px; }
.login-logo {
  width: 52px; height: 52px; border-radius: 14px;
  background: linear-gradient(135deg, #4d6dff, #7c5cfc);
  display: inline-flex; align-items: center; justify-content: center;
  box-shadow: 0 6px 20px rgba(77,109,255,0.35);
  margin-bottom: 16px;
}
.login-title { font-size: 22px; font-weight: 700; color: #fff; margin: 0 0 4px; }
.login-subtitle { font-size: 13px; color: rgba(255,255,255,0.4); margin: 0; }

.login-form { display: flex; flex-direction: column; gap: 16px; }
.input-group { position: relative; }
.input-icon {
  position: absolute; left: 14px; top: 50%; transform: translateY(-50%);
  color: rgba(255,255,255,0.25); pointer-events: none;
}
.login-input {
  width: 100%; padding: 14px 16px 14px 44px;
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 12px;
  background: rgba(255,255,255,0.04);
  color: #fff; font-size: 14px;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.login-input::placeholder { color: rgba(255,255,255,0.25); }
.login-input:focus { border-color: #4d6dff; box-shadow: 0 0 0 3px rgba(77,109,255,0.15); }

.login-options { display: flex; justify-content: space-between; align-items: center; }
.remember-me { display: flex; align-items: center; gap: 6px; font-size: 13px; color: rgba(255,255,255,0.5); cursor: pointer; }
.remember-me input { accent-color: #4d6dff; }

.login-btn {
  width: 100%; padding: 14px;
  background: linear-gradient(135deg, #4d6dff, #7c5cfc);
  color: #fff; border: none; border-radius: 12px;
  font-size: 15px; font-weight: 600;
  cursor: pointer; transition: opacity 0.2s, transform 0.2s;
  display: flex; align-items: center; justify-content: center; gap: 8px;
  margin-top: 4px;
}
.login-btn:hover:not(:disabled) { opacity: 0.9; transform: translateY(-1px); }
.login-btn:disabled { opacity: 0.6; cursor: wait; }

.btn-spinner { width: 16px; height: 16px; border: 2px solid rgba(255,255,255,0.3); border-top-color: #fff; border-radius: 50%; animation: spin 0.6s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.error-msg { text-align: center; margin-top: 16px; font-size: 13px; color: #ef4444; }
</style>
