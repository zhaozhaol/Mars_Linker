import apiClient from './client'
import type { SystemHealth } from '../types/system'

export function getSystemHealth(): Promise<SystemHealth> {
  return apiClient.get('/system/health')
}
