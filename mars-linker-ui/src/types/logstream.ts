export interface LogEntry {
  id: number
  timestamp: number
  level: string
  logger: string
  message: string
  thread: string
}

export type LogLevel = 'TRACE' | 'DEBUG' | 'INFO' | 'WARN' | 'ERROR'
