<template>
  <div class="trend-chart">
    <div class="chart-header">
      <span class="chart-title">{{ title }}</span>
      <span class="chart-current" :class="colorClass">{{ formatNumber(currentValue) }}</span>
    </div>
    <div class="chart-body">
      <svg class="chart-svg" :viewBox="`0 0 ${width} ${height}`" preserveAspectRatio="none">
        <defs>
          <linearGradient :id="`grad-${id}`" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" :stop-color="areaColorTop" />
            <stop offset="100%" :stop-color="areaColorBottom" />
          </linearGradient>
        </defs>
        <path v-if="areaPath" :d="areaPath" :fill="`url(#grad-${id})`" />
        <path v-if="linePath" :d="linePath" fill="none" :stroke="lineColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
        <circle v-if="lastPoint" :cx="lastPoint.x" :cy="lastPoint.y" r="3" :fill="lineColor" />
      </svg>
      <div v-if="!points.length" class="no-data">暂无数据</div>
      <div v-if="sampleRate != null && sampleRate < 1" class="sample-watermark">采样率: {{ (sampleRate * 100).toFixed(0) }}%</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { TrendPoint } from '../../stores/monitoring'

const props = withDefaults(defineProps<{
  title: string
  points: TrendPoint[]
  color?: 'blue' | 'green' | 'purple' | 'red'
  width?: number
  height?: number
  sampleRate?: number | null
}>(), {
  color: 'blue',
  width: 400,
  height: 120,
  sampleRate: null
})

const id = computed(() => props.title.replace(/\s/g, '-'))

const colorMap = {
  blue: { line: '#4d6dff', areaTop: 'rgba(77,109,255,0.25)', areaBottom: 'rgba(77,109,255,0.02)', cls: 'val-blue' },
  green: { line: '#10b981', areaTop: 'rgba(16,185,129,0.25)', areaBottom: 'rgba(16,185,129,0.02)', cls: 'val-green' },
  purple: { line: '#7c5cfc', areaTop: 'rgba(124,92,252,0.25)', areaBottom: 'rgba(124,92,252,0.02)', cls: 'val-purple' },
  red: { line: '#ef4444', areaTop: 'rgba(239,68,68,0.25)', areaBottom: 'rgba(239,68,68,0.02)', cls: 'val-red' }
}

const lineColor = computed(() => colorMap[props.color].line)
const areaColorTop = computed(() => colorMap[props.color].areaTop)
const areaColorBottom = computed(() => colorMap[props.color].areaBottom)
const colorClass = computed(() => colorMap[props.color].cls)

const currentValue = computed(() => {
  if (!props.points.length) return 0
  return props.points[props.points.length - 1].value
})

const formatNumber = (n: number) => {
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M'
  if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K'
  return String(n)
}

const chartData = computed(() => {
  const pts = props.points
  if (pts.length < 2) return { linePath: '', areaPath: '', lastPoint: null }

  const w = props.width
  const h = props.height
  const pad = 4

  const values = pts.map(p => p.value)
  const maxVal = Math.max(...values, 1)
  const minVal = Math.min(...values, 0)
  const range = maxVal - minVal || 1

  const toX = (i: number) => pad + (i / (pts.length - 1)) * (w - pad * 2)
  const toY = (v: number) => h - pad - ((v - minVal) / range) * (h - pad * 2)

  const coords = pts.map((p, i) => ({ x: toX(i), y: toY(p.value) }))

  let line = `M${coords[0].x},${coords[0].y}`
  for (let i = 1; i < coords.length; i++) {
    const prev = coords[i - 1]
    const curr = coords[i]
    const cpx = (prev.x + curr.x) / 2
    line += ` C${cpx},${prev.y} ${cpx},${curr.y} ${curr.x},${curr.y}`
  }

  const last = coords[coords.length - 1]
  const area = line + ` L${last.x},${h} L${coords[0].x},${h} Z`

  return { linePath: line, areaPath: area, lastPoint: last }
})

const linePath = computed(() => chartData.value.linePath)
const areaPath = computed(() => chartData.value.areaPath)
const lastPoint = computed(() => chartData.value.lastPoint)
</script>

<style scoped>
.trend-chart { display: flex; flex-direction: column; flex: 1; min-height: 0; }
.chart-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; flex-shrink: 0; }
.chart-title { font-size: 12px; color: rgba(255,255,255,0.45); font-weight: 500; }
.chart-current { font-size: 16px; font-weight: 700; font-family: 'SF Mono','Fira Code',monospace; }
.val-blue { color: #4d6dff; }
.val-green { color: #10b981; }
.val-purple { color: #7c5cfc; }
.val-red { color: #ef4444; }

.chart-body { position: relative; flex: 1; min-height: 0; }
.chart-svg { width: 100%; height: 100%; display: block; }
.no-data { position: absolute; top: 50%; left: 50%; transform: translate(-50%,-50%); font-size: 12px; color: rgba(255,255,255,0.2); }
.sample-watermark { position: absolute; bottom: 4px; right: 8px; font-size: 10px; color: rgba(255,255,255,0.25); font-weight: 600; }
</style>
