export function formatBoolean(value: boolean): string {
  return value ? '启用' : '禁用'
}

export function formatTtlMs(ms: number): string {
  if (ms <= 0) return String(ms)
  const seconds = Math.floor(ms / 1000)
  if (seconds < 60) return `${ms} ms（${seconds}秒）`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${ms} ms（${minutes}分钟）`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${ms} ms（${hours}小时）`
  const days = Math.floor(hours / 24)
  return `${ms} ms（${days}天）`
}

export function formatTimestamp(ts: number): string {
  if (!ts || ts <= 0) return '--'
  const d = new Date(ts)
  const pad = (n: number, len = 2) => String(n).padStart(len, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}.${pad(d.getMilliseconds(), 3)}`
}

export function formatMetricValue(value: number | undefined): string {
  if (value === undefined || value === null) return '--'
  return String(value)
}
