import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('../views/Login.vue'),
      meta: { public: true }
    },
    {
      path: '/',
      redirect: '/monitoring'
    },
    {
      path: '/monitoring',
      name: 'Monitoring',
      component: () => import('../views/MonitoringOverview.vue')
    },
    {
      path: '/config/broker',
      name: 'BrokerConfig',
      component: () => import('../views/BrokerConfig.vue')
    },
    {
      path: '/config/runtime',
      name: 'RuntimeConfig',
      component: () => import('../views/RuntimeConfig.vue')
    },
    {
      path: '/collection/events',
      name: 'CollectionEvents',
      component: () => import('../views/CollectionEvents.vue')
    },
    {
      path: '/config/acl',
      name: 'AclManagement',
      component: () => import('../views/AclManagement.vue')
    },
    {
      path: '/system/health',
      name: 'SystemHealth',
      component: () => import('../views/SystemHealth.vue')
    },
    {
      path: '/logs/stream',
      name: 'LogStream',
      component: () => import('../views/LogStream.vue')
    },
    {
      path: '/alert/rules',
      name: 'AlertRules',
      component: () => import('../views/AlertRules.vue')
    },
    {
      path: '/alert/history',
      name: 'AlertHistory',
      component: () => import('../views/AlertHistory.vue')
    }
  ]
})

router.beforeEach((to, _from, next) => {
  const authEnabled = localStorage.getItem('ml_auth_enabled') === 'true'
  if (!authEnabled) {
    next()
    return
  }
  const token = localStorage.getItem('ml_token')
  if (!to.meta.public && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/')
  } else {
    next()
  }
})

export default router
