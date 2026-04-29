import apiClient from './client'
import type { MonitoringOverview } from '../types/monitoring'

export function getMonitoringOverview(): Promise<MonitoringOverview> {
  return apiClient.get('/monitoring/overview')
}
