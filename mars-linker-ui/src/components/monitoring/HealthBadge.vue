<template>
  <span class="health-badge" :class="badgeClass">{{ label }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ status: 'UP' | 'DOWN' | 'DEGRADED' }>()

const badgeClass = computed(() => props.status.toLowerCase())

const label = computed(() => {
  switch (props.status) {
    case 'UP': return '正常'
    case 'DOWN': return '故障'
    case 'DEGRADED': return '降级'
    default: return props.status
  }
})
</script>

<style scoped>
.health-badge {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}
.health-badge.up {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
}
.health-badge.down {
  background: rgba(239, 68, 68, 0.15);
  color: #ef4444;
}
.health-badge.degraded {
  background: rgba(245, 158, 11, 0.15);
  color: #f59e0b;
}
</style>
