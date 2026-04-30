<template>
  <div class="skeleton-wrap">
    <div class="skeleton-bar"></div>
  </div>
</template>

<style scoped>
.skeleton-wrap { display: flex; align-items: center; gap: 6px; margin-left: auto; }
.skeleton-bar {
  width: 60px;
  height: 14px;
  border-radius: 6px;
  background: linear-gradient(90deg, #e2e2ec 25%, #f0f0f6 50%, #e2e2ec 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s ease-in-out infinite;
}
@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
