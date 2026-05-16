<script setup lang="ts">
import type { RejectionSummary } from '../../types/rejection'

defineProps<{
  summary: RejectionSummary
}>()
</script>

<template>
  <div class="summary-cards">
    <div class="card card--connect">
      <div class="card__label">连接拒绝</div>
      <div class="card__value">{{ summary.connectRefused }}</div>
    </div>
    <div class="card card--subscribe">
      <div class="card__label">ACL 订阅拒绝</div>
      <div class="card__value">{{ summary.aclSubscribeDenied }}</div>
    </div>
    <div class="card card--publish">
      <div class="card__label">ACL 发布拒绝</div>
      <div class="card__value">{{ summary.aclPublishDenied }}</div>
    </div>
  </div>
</template>

<style scoped>
.summary-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}
.card {
  background: rgba(17, 24, 39, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  padding: 20px;
  text-align: center;
}
.card__label {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.6);
  margin-bottom: 8px;
}
.card__value {
  font-size: 28px;
  font-weight: 700;
  font-family: 'SF Mono', 'Fira Code', monospace;
}
.card--connect .card__value { color: #ef4444; }
.card--subscribe .card__value { color: #f97316; }
.card--publish .card__value { color: #eab308; }
</style>
