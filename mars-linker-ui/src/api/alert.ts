import apiClient from './client'
import type { AlertRule, AlertEvent, SilenceRequest } from '../types/alert'
import type { PagedResult } from '../types/monitoring'

export function listAlertRules(): Promise<AlertRule[]> {
  return apiClient.get('/alert/rules')
}

export function createAlertRule(rule: Omit<AlertRule, 'id' | 'createdAt' | 'updatedAt'>): Promise<AlertRule> {
  return apiClient.post('/alert/rules', rule)
}

export function updateAlertRule(id: string, rule: Partial<AlertRule>): Promise<AlertRule> {
  return apiClient.put(`/alert/rules/${id}`, rule)
}

export function deleteAlertRule(id: string): Promise<void> {
  return apiClient.delete(`/alert/rules/${id}`)
}

export function listAlertEvents(activeOnly?: boolean): Promise<AlertEvent[]> {
  return apiClient.get('/alert/events', { params: { activeOnly: activeOnly ?? false } })
}

export function silenceRule(id: string, request: SilenceRequest): Promise<any> {
  return apiClient.post(`/alert/rules/${id}/silence`, request)
}

export function getAlertHistory(params: {
  start?: number
  end?: number
  page?: number
  size?: number
}): Promise<PagedResult<AlertEvent>> {
  return apiClient.get('/alert/history', { params })
}
