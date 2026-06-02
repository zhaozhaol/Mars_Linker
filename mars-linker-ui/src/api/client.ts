import axios from 'axios'
import { ApiError, ModuleDisabledError } from '../types/error'

const TOKEN_KEY = 'ml_token'
const REFRESH_TOKEN_KEY = 'ml_refresh_token'

const apiClient = axios.create({
  baseURL: '/api/ui',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' }
})

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token && config.url) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let isRefreshing = false
let refreshSubscribers: Array<(token: string) => void> = []

const onRefreshed = (token: string) => {
  refreshSubscribers.forEach(cb => cb(token))
  refreshSubscribers = []
}

apiClient.interceptors.response.use(
  (response) => response.data,
  async (error) => {
    const status = error.response?.status
    const originalRequest = error.config

    if (status === 401 && !originalRequest._retry) {
      const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY)
      if (!refreshToken) {
        clearAuthAndRedirect()
        throw new ApiError(401, '认证已过期，请重新登录')
      }

      if (isRefreshing) {
        return new Promise((resolve) => {
          refreshSubscribers.push((token: string) => {
            originalRequest.headers.Authorization = `Bearer ${token}`
            resolve(apiClient(originalRequest))
          })
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      try {
        const resp = await axios.post('/api/ui/auth/refresh', { refreshToken }, {
          headers: { 'Content-Type': 'application/json' }
        })
        const newToken = resp.data.accessToken
        const expiresIn = resp.data.expiresIn
        localStorage.setItem(TOKEN_KEY, newToken)
        const expiresAt = Date.now() + expiresIn * 1000
        localStorage.setItem('ml_token_expires_at', String(expiresAt))
        isRefreshing = false
        onRefreshed(newToken)
        originalRequest.headers.Authorization = `Bearer ${newToken}`
        return apiClient(originalRequest)
      } catch {
        isRefreshing = false
        refreshSubscribers = []
        clearAuthAndRedirect()
        throw new ApiError(401, '认证已过期，请重新登录')
      }
    }

    if (status === 401) {
      clearAuthAndRedirect()
      throw new ApiError(401, '认证已过期，请重新登录')
    }
    if (status === 403) {
      clearAuthAndRedirect()
      throw new ApiError(403, '权限不足，请重新登录')
    }
    if (status === 404) {
      throw new ModuleDisabledError()
    }
    throw new ApiError(status || 0, error.message || '请求失败')
  }
)

function clearAuthAndRedirect() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  localStorage.removeItem('ml_user')
  const currentPath = window.location.pathname
  if (currentPath !== '/login') {
    window.location.href = '/login'
  }
}

export default apiClient
