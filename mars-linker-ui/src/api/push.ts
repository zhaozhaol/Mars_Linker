import type { PushMessage, MetricCategory } from '../types/push'

const WS_BASE = `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/api/ui/monitoring/ws`
const SSE_BASE = '/api/ui/monitoring/sse'

export function createWebSocketUrl(categories: MetricCategory[], token?: string): string {
  const params = new URLSearchParams()
  if (categories.length > 0) {
    params.set('categories', categories.join(','))
  }
  if (token) {
    params.set('token', token)
  }
  return `${WS_BASE}?${params.toString()}`
}

export function createSSEUrl(categories: MetricCategory[]): string {
  const params = new URLSearchParams()
  if (categories.length > 0) {
    params.set('categories', categories.join(','))
  }
  return `${SSE_BASE}?${params.toString()}`
}
