import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listAlertRules, createAlertRule, updateAlertRule, deleteAlertRule, listAlertEvents } from '../api/alert'
import type { AlertRule, AlertEvent } from '../types/alert'

export const useAlertStore = defineStore('alert', () => {
  const rules = ref<AlertRule[]>([])
  const events = ref<AlertEvent[]>([])
  const loading = ref(false)

  const fetchRules = async () => {
    try {
      rules.value = await listAlertRules()
    } catch { /* ignore */ }
  }

  const addRule = async (rule: Omit<AlertRule, 'id' | 'createdAt' | 'updatedAt'>) => {
    const created = await createAlertRule(rule)
    rules.value.push(created)
    return created
  }

  const editRule = async (id: string, rule: Partial<AlertRule>) => {
    const updated = await updateAlertRule(id, rule)
    const idx = rules.value.findIndex(r => r.id === id)
    if (idx >= 0) rules.value[idx] = updated
    return updated
  }

  const removeRule = async (id: string) => {
    await deleteAlertRule(id)
    rules.value = rules.value.filter(r => r.id !== id)
  }

  const fetchEvents = async (activeOnly = false) => {
    try {
      events.value = await listAlertEvents(activeOnly)
    } catch { /* ignore */ }
  }

  return {
    rules, events, loading,
    fetchRules, addRule, editRule, removeRule, fetchEvents
  }
})
