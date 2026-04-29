import apiClient from './client'
import type { ModuleInfo } from '../types/module'

export function getModuleInfo(): Promise<ModuleInfo> {
  return apiClient.get('/module/info')
}
