import apiClient from './client'
import type { RejectionMessage, RejectionSummary, PagedResult } from '../types/rejection'
import type { RejectionType, RejectionReason } from '../types/rejection'

export function fetchRejectionMessages(params: {
  page?: number
  size?: number
  type?: RejectionType
  reason?: RejectionReason
}): Promise<PagedResult<RejectionMessage>> {
  return apiClient.get('/rejection/messages', { params })
}

export function fetchRejectionSummary(): Promise<RejectionSummary> {
  return apiClient.get('/rejection/messages/summary')
}

export function removeRejectionMessage(id: number): Promise<{ id: number; removed: boolean }> {
  return apiClient.delete(`/rejection/messages/${id}`)
}

export function removeAllRejectionMessages(type?: RejectionType): Promise<{ removed: number }> {
  const params = type ? { type } : {}
  return apiClient.delete('/rejection/messages', { params })
}
