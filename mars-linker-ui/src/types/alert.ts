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
