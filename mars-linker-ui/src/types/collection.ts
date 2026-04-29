export interface CollectedEvent {
  timestamp: number
  type: string
  source: string
  payload: string
}

export interface CollectEventRequest {
  type?: string
  source?: string
  payload?: string
}
