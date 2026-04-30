export interface BrokerStatus {
  nettyEnabled: boolean
  tcpPort: number
  storageEnabled: boolean
  storageMode: string
}

export interface BrokerMetrics {
  connectionsActive: number
  connectAcceptedTotal: number
  connectRejectedTotal: number
  publishInTotal: number
  publishOutTotal: number
  qos2InPending: number
  qos2InCompletedTotal: number
  aclSubscribeDenyTotal: number
  aclPublishDenyTotal: number
}

export interface CollectionStats {
  bufferedSize: number
  totalCollected: number
}

export interface MonitoringOverview {
  timestamp: number
  broker: BrokerStatus
  metrics: BrokerMetrics
  runtimeConfig: RuntimeConfig
  collection: CollectionStats
}

export interface RuntimeConfig {
  enabled: boolean
  collectMode: string
  monitorRefreshMs: number
  collectionBufferSize: number
}

export interface ConnectionMetrics {
  timestamp: number
  connectionsActive: number
  connectionsPeakTotal: number
  connectAcceptedTotal: number
  connectRejectedTotal: number
}

export interface MessageMetrics {
  timestamp: number
  publishInTotal: number
  publishOutTotal: number
  publishInRate: number
  publishOutRate: number
}

export interface SubscriptionMetrics {
  timestamp: number
  subscriptionsTotal: number
  topicCount: number
  subscriptionTreeDepth: number
  degraded: boolean
}

export interface SystemMetrics {
  timestamp: number
  heapUsedRatio: number
  cpuUsageRatio: number
  threadCount: number
  gcCount: number
  gcTimeMs: number
}

export interface ComponentHealth {
  name: string
  status: 'UP' | 'DOWN' | 'DEGRADED'
  detail: string
}

export interface HealthStatus {
  timestamp: number
  status: 'UP' | 'DOWN' | 'DEGRADED'
  components: ComponentHealth[]
}

export interface MonitoringSelfMetrics {
  timestamp: number
  collectionTimeMs: number
  pushDelayMs: number
  historyMemoryBytes: number
  pushConnectionCount: number
  threadPoolActiveCount: number
  threadPoolQueueSize: number
  eventDiscardCount: number
}

export interface MonitoringSnapshot {
  timestamp: number
  category: string
  data: Record<string, any>
}

export interface PagedResult<T> {
  items: T[]
  totalCount: number
  page: number
  size: number
}

export interface ExtendedBrokerMetrics extends BrokerMetrics {
  connectionsPeakTotal?: number
  publishInRate?: number
  publishOutRate?: number
}

export interface SubscriptionTopicInfo {
  topicFilter: string
  subscriberCount: number
  subscriptionType: 'exact' | 'wildcard' | 'shared'
}

export interface SubscriberDetail {
  clientId: string
  grantedQos: number
  protocolLevel: number
  connectedDurationMs: number
  remoteAddress: string
}

export interface ClientSubscriptionInfo {
  clientId: string
  subscriptions: SubscriptionTopicInfo[]
}
