import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('ml_token'))
  const refreshToken = ref<string | null>(localStorage.getItem('ml_refresh_token'))
  const username = ref<string | null>(localStorage.getItem('ml_user'))
  const tokenExpiresAt = ref<number | null>(null)

  const isAuthenticated = computed(() => !!token.value)

  const login = (user: string, accessToken: string, rt: string, expiresIn: number) => {
    token.value = accessToken
    refreshToken.value = rt
    username.value = user
    tokenExpiresAt.value = Date.now() + expiresIn * 1000
    localStorage.setItem('ml_token', accessToken)
    localStorage.setItem('ml_refresh_token', rt)
    localStorage.setItem('ml_user', user)
  }

  const updateAccessToken = (accessToken: string, expiresIn: number) => {
    token.value = accessToken
    tokenExpiresAt.value = Date.now() + expiresIn * 1000
    localStorage.setItem('ml_token', accessToken)
  }

  const logout = () => {
    token.value = null
    refreshToken.value = null
    username.value = null
    tokenExpiresAt.value = null
    localStorage.removeItem('ml_token')
    localStorage.removeItem('ml_refresh_token')
    localStorage.removeItem('ml_user')
  }

  const isTokenExpiringSoon = () => {
    if (!tokenExpiresAt.value) return true
    return Date.now() > tokenExpiresAt.value - 120_000
  }

  return { token, refreshToken, username, isAuthenticated, tokenExpiresAt, login, updateAccessToken, logout, isTokenExpiringSoon }
})
