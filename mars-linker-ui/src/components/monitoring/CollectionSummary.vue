<template>
  <div class="glass-card">
    <div class="card-header">
      <div class="card-icon coll-icon">C</div>
      <span class="card-title">采集统计</span>
    </div>
    <div class="stat-row">
      <div class="stat-block">
        <div class="stat-number mono">{{ stats?.bufferedSize ?? '--' }}</div>
        <div class="stat-label">缓冲区</div>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-block">
        <div class="stat-number mono">{{ stats?.totalCollected ?? '--' }}</div>
        <div class="stat-label">总采集数</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { CollectionStats } from '../../types/monitoring'

defineProps<{ stats: CollectionStats | null }>()
</script>

<style scoped>
.glass-card {
  background: rgba(255,255,255,0.85);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255,255,255,0.6);
  border-radius: 14px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.04);
  transition: box-shadow 0.25s, transform 0.25s;
}
.glass-card:hover { box-shadow: 0 6px 24px rgba(0,0,0,0.08); transform: translateY(-2px); }
.card-header { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.card-icon { width: 32px; height: 32px; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 14px; color: #fff; }
.coll-icon { background: linear-gradient(135deg, #10b981, #059669); }
.card-title { font-size: 15px; font-weight: 600; color: #1a1a2e; }
.stat-row { display: flex; align-items: center; justify-content: center; gap: 24px; padding: 8px 0; }
.stat-block { text-align: center; flex: 1; }
.stat-number { font-size: 28px; font-weight: 700; color: #1a1a2e; font-family: 'SF Mono', 'Fira Code', monospace; }
.stat-label { font-size: 12px; color: #8c8c9a; font-weight: 500; margin-top: 4px; }
.stat-divider { width: 1px; height: 40px; background: #e0e0ec; }
</style>
