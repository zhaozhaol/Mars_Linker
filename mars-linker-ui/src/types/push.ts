export type PushType = 'metrics_update' | 'alert_event' | 'config_change'
export type MetricCategory = 'connection' | 'message' | 'subscription' | 'system' | 'all'

export interface PushMessage {
  type: PushType
  category: MetricCategory
  timestamp: number
  payload: any
}
