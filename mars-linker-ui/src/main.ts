import { createApp, ref } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './assets/global.css'
import router from './router'
import App from './App.vue'
import { getModuleInfo } from './api/module'
import { ModuleDisabledError } from './types/error'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus)

const moduleEnabled = ref<boolean | null>(null)
const moduleError = ref(false)

const checkModuleStatus = async () => {
  moduleError.value = false
  try {
    const info = await getModuleInfo()
    moduleEnabled.value = info.enabled
  } catch (e) {
    if (e instanceof ModuleDisabledError) {
      moduleEnabled.value = false
    } else {
      moduleEnabled.value = null
      moduleError.value = true
    }
  }
}

app.provide('moduleEnabled', moduleEnabled)
app.provide('moduleError', moduleError)
app.provide('checkModuleStatus', checkModuleStatus)

app.mount('#app')

checkModuleStatus()
