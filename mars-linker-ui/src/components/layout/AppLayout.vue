<template>
  <div class="app-shell">
    <aside class="sidebar" :class="{ collapsed: isCollapsed }">
      <div class="brand">
        <div class="brand-icon">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round">
            <path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/>
          </svg>
        </div>
        <transition name="fade">
          <div v-if="!isCollapsed" class="brand-text">
            <span class="brand-name">Mars Linker</span>
            <span class="brand-sub">Operations Console</span>
          </div>
        </transition>
      </div>
      <nav class="nav-menu">
        <template v-for="(group, gi) in menuGroups" :key="gi">
          <div v-if="!isCollapsed" class="nav-group-label">{{ group.label }}</div>
          <div v-else class="nav-group-sep"></div>
          <router-link
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            class="nav-item"
            :class="{ active: currentRoute === item.path }"
          >
            <span class="nav-icon" v-html="item.icon"></span>
            <transition name="fade">
              <span v-if="!isCollapsed" class="nav-label">{{ item.label }}</span>
            </transition>
          </router-link>
        </template>
      </nav>
      <div class="sidebar-bottom">
        <div class="sidebar-user" v-if="!isCollapsed">
          <div class="user-avatar">{{ userInitial }}</div>
          <div class="user-info">
            <span class="user-name">{{ authStore.username ?? 'Admin' }}</span>
          </div>
        </div>
        <div class="sidebar-user-collapsed" v-else>
          <div class="user-avatar small">{{ userInitial }}</div>
        </div>
        <div class="sidebar-divider"></div>
        <button class="collapse-btn" @click="toggleCollapse" :title="isCollapsed ? '展开侧栏' : '收起侧栏'">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <polyline v-if="isCollapsed" points="9 18 15 12 9 6"/>
            <polyline v-else points="15 18 9 12 15 6"/>
          </svg>
        </button>
        <button v-if="!isCollapsed" class="logout-btn" @click="handleLogout" title="退出登录">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/>
          </svg>
          <span>退出</span>
        </button>
      </div>
    </aside>
    <main class="main-area">
      <header class="top-bar">
        <div class="top-left">
          <button class="mobile-menu-btn" @click="toggleCollapse">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2"><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="18" x2="21" y2="18"/></svg>
          </button>
          <div class="top-title">{{ currentPageTitle }}</div>
        </div>
        <div class="top-actions">
          <span class="status-dot online"></span>
          <span class="status-text">Broker 在线</span>
        </div>
      </header>
      <div class="content-wrap">
        <router-view />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const currentRoute = computed(() => route.path)

const isCollapsed = ref(false)

const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
}

const userInitial = computed(() => {
  const name = authStore.username ?? 'A'
  return name.charAt(0).toUpperCase()
})

const handleLogout = () => {
  authStore.logout()
  router.push('/login')
}

const menuGroups = [
  {
    label: '监控',
    items: [
      { path: '/monitoring', label: '概览', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/></svg>' },
      { path: '/system/health', label: '系统健康', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M22 12h-4l-3 9L9 3l-3 9H2"/></svg>' },
      { path: '/logs/stream', label: '实时日志', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>' }
    ]
  },
  {
    label: '配置',
    items: [
      { path: '/config/broker', label: 'Broker 配置', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>' },
      { path: '/config/runtime', label: '运行时配置', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>' },
      { path: '/collection/events', label: '采集事件', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>' }
    ]
  },
  {
    label: '规则',
    items: [
      { path: '/config/acl', label: 'ACL 规则', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>' },
      { path: '/alert/rules', label: '告警规则', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>' }
    ]
  },
  {
    label: '告警',
    items: [
      { path: '/alert/history', label: '告警历史', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>' }
    ]
  }
]

const allItems = computed(() => menuGroups.flatMap(g => g.items))

const currentPageTitle = computed(() => {
  const item = allItems.value.find(m => m.path === route.path)
  return item?.label ?? '概览'
})
</script>

<style scoped>
.app-shell { display: flex; height: 100vh; background: #0a0e1a; }

.sidebar {
  width: 220px;
  background: linear-gradient(180deg, #0d1330 0%, #0a0e1a 100%);
  display: flex; flex-direction: column; flex-shrink: 0;
  border-right: 1px solid rgba(255,255,255,0.04);
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}
.sidebar.collapsed { width: 64px; }

.brand { display: flex; align-items: center; gap: 12px; padding: 20px 16px 16px; white-space: nowrap; }
.sidebar.collapsed .brand { justify-content: center; padding: 20px 0 16px; }
.brand-icon {
  width: 38px; height: 38px; border-radius: 10px; flex-shrink: 0;
  background: linear-gradient(135deg, #4d6dff, #7c5cfc);
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 4px 12px rgba(77, 109, 255, 0.3);
}
.brand-text { display: flex; flex-direction: column; overflow: hidden; }
.brand-name { font-size: 15px; font-weight: 600; color: #fff; line-height: 1.2; }
.brand-sub { font-size: 10px; color: rgba(255,255,255,0.35); margin-top: 2px; letter-spacing: 0.5px; text-transform: uppercase; }

.nav-menu { padding: 0 12px; display: flex; flex-direction: column; gap: 2px; flex: 1; overflow-y: auto; }
.sidebar.collapsed .nav-menu { padding: 0 8px; }

.nav-group-label {
  font-size: 10px; font-weight: 700; color: rgba(255,255,255,0.25);
  text-transform: uppercase; letter-spacing: 1px;
  padding: 16px 14px 4px;
}
.nav-group-sep { height: 12px; }

.nav-item {
  display: flex; align-items: center; gap: 10px;
  padding: 9px 14px; border-radius: 8px;
  font-size: 13px; font-weight: 500;
  color: rgba(255,255,255,0.45);
  text-decoration: none; transition: all 0.2s; cursor: pointer;
  white-space: nowrap; overflow: hidden;
}
.sidebar.collapsed .nav-item { padding: 9px; justify-content: center; }
.nav-item:hover { color: rgba(255,255,255,0.8); background: rgba(255,255,255,0.04); }
.nav-item.active {
  color: #fff;
  background: linear-gradient(135deg, rgba(77,109,255,0.2), rgba(124,92,252,0.15));
  border: 1px solid rgba(77,109,255,0.2);
}
.nav-icon { display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.nav-label { overflow: hidden; }

.sidebar-bottom { padding: 12px 12px; border-top: 1px solid rgba(255,255,255,0.04); }
.sidebar.collapsed .sidebar-bottom { padding: 12px 8px; }

.sidebar-user { display: flex; align-items: center; gap: 10px; padding: 4px 4px 8px; }
.user-avatar {
  width: 32px; height: 32px; border-radius: 50%; flex-shrink: 0;
  background: linear-gradient(135deg, #4d6dff, #7c5cfc);
  display: flex; align-items: center; justify-content: center;
  font-size: 13px; font-weight: 700; color: #fff;
}
.user-avatar.small { width: 28px; height: 28px; font-size: 11px; }
.user-info { display: flex; flex-direction: column; overflow: hidden; }
.user-name { font-size: 13px; font-weight: 500; color: rgba(255,255,255,0.7); white-space: nowrap; }

.sidebar-user-collapsed { display: flex; justify-content: center; padding: 4px 0 8px; }
.sidebar-divider { height: 1px; background: rgba(255,255,255,0.06); margin: 8px 0; }

.collapse-btn {
  width: 100%; padding: 8px; border: none; border-radius: 8px;
  background: rgba(255,255,255,0.04); color: rgba(255,255,255,0.4);
  cursor: pointer; display: flex; align-items: center; justify-content: center; transition: all 0.2s;
}
.collapse-btn:hover { background: rgba(255,255,255,0.08); color: rgba(255,255,255,0.7); }

.logout-btn {
  width: 100%; padding: 8px 12px; margin-top: 6px; border: none; border-radius: 8px;
  background: rgba(239,68,68,0.08); color: rgba(239,68,68,0.7);
  cursor: pointer; display: flex; align-items: center; gap: 6px; justify-content: center;
  font-size: 12px; font-weight: 500; transition: all 0.2s;
}
.logout-btn:hover { background: rgba(239,68,68,0.15); color: #ef4444; }

.main-area { flex: 1; display: flex; flex-direction: column; overflow: hidden; background: #111827; }
.top-bar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 32px; height: 56px; flex-shrink: 0;
  border-bottom: 1px solid rgba(255,255,255,0.06);
  background: rgba(17, 24, 39, 0.8); backdrop-filter: blur(12px);
}
.top-left { display: flex; align-items: center; gap: 12px; }
.mobile-menu-btn { display: none; background: none; border: none; color: #fff; cursor: pointer; padding: 4px; }
.top-title { font-size: 16px; font-weight: 600; color: #fff; }
.top-actions { display: flex; align-items: center; gap: 8px; }
.status-dot { width: 8px; height: 8px; border-radius: 50%; }
.status-dot.online { background: #10b981; box-shadow: 0 0 8px rgba(16,185,129,0.4); animation: pulse 2s infinite; }
.status-text { font-size: 12px; color: rgba(255,255,255,0.5); }

.content-wrap { flex: 1; overflow-y: auto; padding: 24px 32px; }

.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }

@media (max-width: 768px) {
  .sidebar { position: fixed; z-index: 100; height: 100vh; }
  .sidebar.collapsed { width: 0; border: none; }
  .mobile-menu-btn { display: flex; }
}
</style>
