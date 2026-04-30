import axios from 'axios'
import { ApiError, ModuleDisabledError } from '../types/error'

const TOKEN_KEY = 'ml_token'

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

apiClient.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem('ml_user')
      const currentPath = window.location.pathname
      if (currentPath !== '/login') {
        window.location.href = '/login'
      }
      throw new ApiError(401, '认证已过期，请重新登录')
    }
    if (status === 403) {
      throw new ApiError(403, '权限不足，无法访问该资源')
    }
    if (status === 404) {
      throw new ModuleDisabledError()
    }
    throw new ApiError(status || 0, error.message || '请求失败')
  }
)

export default apiClient
