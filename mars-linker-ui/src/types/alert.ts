export interface AlertRule {
  id: string
  name: string
  metric: string
  operator: string
  threshold: number
  durationSeconds: number
  enabled: boolean
  createdAt: number
  updatedAt: number
}

export interface AlertEvent {
  id: string
  ruleId: string
  ruleName: string
  metric: string
  actualValue: number
  threshold: number
  severity: string
  triggeredAt: number
  resolvedAt: number
  active: boolean
}

export type AlertStatus = 'firing' | 'resolved' | 'silenced'

export interface SilenceRequest {
  durationMs: number
}

export interface ExtendedAlertRule extends AlertRule {
  severity?: 'critical' | 'warning' | 'info'
  durationMs?: number
}

export interface ExtendedAlertEvent extends AlertEvent {
  status?: AlertStatus
  silencedUntil?: number
}
