export interface HeapInfo {
  used: number
  max: number
  committed: number
  usagePercent: number
}

export interface GcInfo {
  youngGcCount: number
  youngGcTimeMs: number
  fullGcCount: number
  fullGcTimeMs: number
}

export interface ThreadInfo {
  active: number
  peak: number
  daemon: number
  totalStarted: number
}

export interface CpuInfo {
  availableProcessors: number
  systemLoadAverage: number
  processCpuPercent: number
  systemCpuPercent: number
}

export interface DiskInfo {
  total: number
  used: number
  free: number
  usable: number
  usagePercent: number
}

export interface SystemHealth {
  timestamp: number
  heap: HeapInfo
  gc: GcInfo
  threads: ThreadInfo
  cpu: CpuInfo
  disk: DiskInfo
}
