import apiClient from './client'
import type { MonitoringOverview, ConnectionMetrics, MessageMetrics, SubscriptionMetrics, SystemMetrics, HealthStatus, MonitoringSelfMetrics, MonitoringSnapshot, PagedResult, SubscriptionTopicInfo, SubscriberDetail, ClientSubscriptionInfo } from '../types/monitoring'

export function getMonitoringOverview(): Promise<MonitoringOverview> {
  return apiClient.get('/monitoring/overview')
}

export function getConnectionMetrics(): Promise<ConnectionMetrics> {
  return apiClient.get('/monitoring/connections')
}

export function getMessageMetrics(): Promise<MessageMetrics> {
  return apiClient.get('/monitoring/messages')
}

export function getSubscriptionMetrics(): Promise<SubscriptionMetrics> {
  return apiClient.get('/monitoring/subscriptions')
}

export function getSystemMetrics(): Promise<SystemMetrics> {
  return apiClient.get('/monitoring/system')
}

export function getHealthStatus(): Promise<HealthStatus> {
  return apiClient.get('/monitoring/health')
}

export function getMonitoringHistory(params: {
  start?: number
  end?: number
  category?: string
  page?: number
  size?: number
}): Promise<PagedResult<MonitoringSnapshot>> {
  return apiClient.get('/monitoring/history', { params })
}

export function getSelfMetrics(): Promise<MonitoringSelfMetrics> {
  return apiClient.get('/monitoring/self-metrics')
}

export function getSubscriptionTopics(params: { page?: number; size?: number }): Promise<PagedResult<SubscriptionTopicInfo>> {
  return apiClient.get('/monitoring/subscriptions/topics', { params })
}

export function getSubscriptionSubscribers(topicFilter: string): Promise<SubscriberDetail[]> {
  return apiClient.get('/monitoring/subscriptions/subscribers', { params: { topicFilter } })
}

export function getClientSubscriptions(clientId: string): Promise<ClientSubscriptionInfo> {
  return apiClient.get('/monitoring/subscriptions/client', { params: { clientId } })
}
