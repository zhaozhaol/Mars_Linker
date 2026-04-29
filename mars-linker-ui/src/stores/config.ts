import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getBrokerConfig, getRuntimeConfig, updateRuntimeConfig } from '../api/config'
import type { BrokerConfig, RuntimeConfigUpdateRequest } from '../types/config'
import type { RuntimeConfig } from '../types/monitoring'

export const useConfigStore = defineStore('config', () => {
  const brokerConfig = ref<BrokerConfig | null>(null)
  const runtimeConfig = ref<RuntimeConfig | null>(null)
  const brokerLoading = ref(false)
  const runtimeLoading = ref(false)
  const updating = ref(false)

  const fetchBrokerConfig = async () => {
    brokerLoading.value = true
    try {
      brokerConfig.value = await getBrokerConfig()
    } finally {
      brokerLoading.value = false
    }
  }

  const fetchRuntimeConfig = async () => {
    runtimeLoading.value = true
    try {
      runtimeConfig.value = await getRuntimeConfig()
    } finally {
      runtimeLoading.value = false
    }
  }

  const doUpdateRuntimeConfig = async (updates: RuntimeConfigUpdateRequest) => {
    updating.value = true
    try {
      runtimeConfig.value = await updateRuntimeConfig(updates)
    } finally {
      updating.value = false
    }
  }

  return {
    brokerConfig, runtimeConfig,
    brokerLoading, runtimeLoading, updating,
    fetchBrokerConfig, fetchRuntimeConfig, doUpdateRuntimeConfig
  }
})
