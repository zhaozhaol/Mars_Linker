import apiClient from './client'
import type { CollectedEvent, CollectEventRequest } from '../types/collection'

export function getCollectionEvents(limit = 50): Promise<CollectedEvent[]> {
  return apiClient.get('/collection/events', { params: { limit } })
}

export function collectEvent(request: CollectEventRequest): Promise<CollectedEvent> {
  return apiClient.post('/collection/events', request)
}
