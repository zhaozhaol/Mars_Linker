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
