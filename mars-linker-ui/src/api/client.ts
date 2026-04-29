import axios from 'axios'
import { ApiError, ModuleDisabledError } from '../types/error'

const apiClient = axios.create({
  baseURL: '/api/ui',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' }
})

apiClient.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const status = error.response?.status
    if (status === 404) {
      throw new ModuleDisabledError()
    }
    throw new ApiError(status || 0, error.message || '请求失败')
  }
)

export default apiClient
