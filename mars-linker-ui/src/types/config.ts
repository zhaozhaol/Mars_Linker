import type { RuntimeConfig } from './monitoring'

export interface BrokerConfig {
  nettyEnabled: boolean
  tcpPort: number
  storageEnabled: boolean
  storageMode: string
  sessionOfflineMaxMessages: number
  sessionOfflineTtlMs: number
  retainMaxMessages: number
  retainTtlMs: number
}

export type RuntimeConfigUpdateRequest = Partial<Pick<RuntimeConfig, 'collectMode' | 'monitorRefreshMs'>>
