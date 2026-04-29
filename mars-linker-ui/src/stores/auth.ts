import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('ml_token'))
  const username = ref<string | null>(localStorage.getItem('ml_user'))

  const isAuthenticated = computed(() => !!token.value)

  const login = (user: string, t: string) => {
    token.value = t
    username.value = user
    localStorage.setItem('ml_token', t)
    localStorage.setItem('ml_user', user)
  }

  const logout = () => {
    token.value = null
    username.value = null
    localStorage.removeItem('ml_token')
    localStorage.removeItem('ml_user')
  }

  return { token, username, isAuthenticated, login, logout }
})
