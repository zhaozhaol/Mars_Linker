import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getCollectionEvents, collectEvent } from '../api/collection'
import type { CollectedEvent, CollectEventRequest } from '../types/collection'

export const useCollectionStore = defineStore('collection', () => {
  const events = ref<CollectedEvent[]>([])
  const limit = ref(50)
  const loading = ref(false)
  const submitting = ref(false)

  const fetchEvents = async (newLimit?: number) => {
    if (newLimit !== undefined) limit.value = newLimit
    loading.value = true
    try {
      events.value = await getCollectionEvents(limit.value)
    } finally {
      loading.value = false
    }
  }

  const doCollectEvent = async (request: CollectEventRequest) => {
    submitting.value = true
    try {
      await collectEvent(request)
      await fetchEvents()
    } finally {
      submitting.value = false
    }
  }

  return {
    events, limit, loading, submitting,
    fetchEvents, doCollectEvent
  }
})
