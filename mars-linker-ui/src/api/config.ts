import apiClient from './client'
import type { BrokerConfig, RuntimeConfigUpdateRequest } from '../types/config'
import type { RuntimeConfig } from '../types/monitoring'

export function getBrokerConfig(): Promise<BrokerConfig> {
  return apiClient.get('/config/broker')
}

export function getRuntimeConfig(): Promise<RuntimeConfig> {
  return apiClient.get('/config/runtime')
}

export function updateRuntimeConfig(updates: RuntimeConfigUpdateRequest): Promise<RuntimeConfig> {
  return apiClient.put('/config/runtime', updates)
}
