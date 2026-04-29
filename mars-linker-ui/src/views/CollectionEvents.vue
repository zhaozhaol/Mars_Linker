<template>
  <div class="events-page">
    <div class="page-toolbar">
      <div class="toolbar-left">
        <span class="event-count">共 {{ collectionStore.events.length }} 条事件</span>
      </div>
      <div class="toolbar-right">
        <span class="limit-label">显示</span>
        <div class="limit-pills">
          <button v-for="opt in limitOptions" :key="opt" class="pill" :class="{ active: currentLimit === opt }" @click="handleLimitChange(opt)">{{ opt }}</button>
        </div>
      </div>
    </div>

    <div class="events-panel">
      <div v-if="collectionStore.loading" class="loading-state">
        <div class="spinner"></div>
        <span>加载中...</span>
      </div>
      <div v-else-if="!collectionStore.events.length" class="empty-state">
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
        <span>暂无采集事件</span>
      </div>
      <div v-else class="events-timeline">
        <div v-for="(event, idx) in collectionStore.events" :key="idx" class="event-item">
          <div class="event-dot-wrap">
            <div class="event-dot"></div>
            <div v-if="idx < collectionStore.events.length - 1" class="event-line"></div>
          </div>
          <div class="event-content">
            <div class="event-top">
              <span class="event-type-badge">{{ event.type }}</span>
              <span class="event-source">{{ event.source }}</span>
              <span class="event-time mono">{{ formatTimestamp(event.timestamp) }}</span>
            </div>
            <div v-if="event.payload" class="event-payload">{{ event.payload }}</div>
          </div>
        </div>
      </div>
    </div>

    <div class="manual-section">
      <h3 class="section-title">手动采集</h3>
      <div class="manual-form">
        <div class="field">
          <label class="field-label">类型</label>
          <input v-model="manualForm.type" class="field-input" placeholder="custom" />
        </div>
        <div class="field">
          <label class="field-label">来源</label>
          <input v-model="manualForm.source" class="field-input" placeholder="ui" />
        </div>
        <div class="field flex-1">
          <label class="field-label">负载</label>
          <input v-model="manualForm.payload" class="field-input" placeholder="可选内容" />
        </div>
        <button class="ml-btn ml-btn-primary" :disabled="collectionStore.submitting" @click="handleCollect">
          {{ collectionStore.submitting ? '提交中...' : '提交事件' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useCollectionStore } from '../stores/collection'
import { formatTimestamp } from '../utils/formatter'

const collectionStore = useCollectionStore()
const currentLimit = ref(50)
const limitOptions = [20, 50, 100, 200]

const manualForm = reactive({ type: 'custom', source: 'ui', payload: '' })

const handleLimitChange = (val: number) => { currentLimit.value = val; collectionStore.fetchEvents(val) }

const handleCollect = async () => {
  try {
    await collectionStore.doCollectEvent({ type: manualForm.type || 'custom', source: manualForm.source || 'ui', payload: manualForm.payload })
    ElMessage.success('事件已采集')
  } catch { ElMessage.error('事件采集失败，请重试') }
}

onMounted(() => collectionStore.fetchEvents(50))
</script>

<style scoped>
.events-page { animation: fadeIn 0.3s ease; }
.page-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.event-count { font-size: 13px; color: rgba(255,255,255,0.5); }
.toolbar-right { display: flex; align-items: center; gap: 8px; }
.limit-label { font-size: 12px; color: rgba(255,255,255,0.35); }
.limit-pills { display: flex; gap: 4px; }
.pill { padding: 4px 12px; border: 1px solid rgba(255,255,255,0.08); background: transparent; border-radius: 6px; font-size: 12px; color: rgba(255,255,255,0.5); cursor: pointer; transition: all 0.2s; }
.pill:hover { border-color: #4d6dff; color: #4d6dff; }
.pill.active { background: #4d6dff; color: #fff; border-color: #4d6dff; }

.events-panel { background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.06); border-radius: 12px; padding: 20px; margin-bottom: 20px; min-height: 200px; }

.loading-state, .empty-state { display: flex; align-items: center; justify-content: center; gap: 8px; padding: 32px; color: rgba(255,255,255,0.35); font-size: 13px; }
.spinner { width: 18px; height: 18px; border: 2px solid rgba(255,255,255,0.1); border-top-color: #4d6dff; border-radius: 50%; animation: spin 0.6s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.events-timeline { display: flex; flex-direction: column; }
.event-item { display: flex; gap: 14px; }
.event-dot-wrap { display: flex; flex-direction: column; align-items: center; flex-shrink: 0; width: 20px; }
.event-dot { width: 8px; height: 8px; border-radius: 50%; background: #4d6dff; flex-shrink: 0; margin-top: 6px; box-shadow: 0 0 6px rgba(77,109,255,0.3); }
.event-line { width: 1px; flex: 1; background: rgba(255,255,255,0.06); margin: 4px 0; }
.event-content { flex: 1; padding-bottom: 16px; }
.event-top { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.event-type-badge { display: inline-block; padding: 2px 8px; background: rgba(77,109,255,0.12); color: #4d6dff; border-radius: 4px; font-size: 11px; font-weight: 600; }
.event-source { font-size: 12px; color: rgba(255,255,255,0.4); }
.event-time { font-size: 11px; color: rgba(255,255,255,0.25); margin-left: auto; }
.event-payload { font-size: 13px; color: rgba(255,255,255,0.6); padding: 6px 10px; background: rgba(255,255,255,0.03); border-radius: 6px; margin-top: 4px; word-break: break-all; max-height: 60px; overflow: hidden; }

.manual-section {}
.section-title { font-size: 13px; font-weight: 600; color: rgba(255,255,255,0.4); text-transform: uppercase; letter-spacing: 0.5px; margin: 0 0 12px; }
.manual-form { display: flex; align-items: flex-end; gap: 12px; }
.field { display: flex; flex-direction: column; gap: 4px; }
.field.flex-1 { flex: 1; }
.field-label { font-size: 11px; color: rgba(255,255,255,0.35); font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; }
.field-input { padding: 8px 12px; border: 1px solid rgba(255,255,255,0.08); border-radius: 8px; font-size: 13px; color: #fff; background: rgba(255,255,255,0.04); outline: none; transition: border-color 0.2s, box-shadow 0.2s; }
.field-input:focus { border-color: #4d6dff; box-shadow: 0 0 0 3px rgba(77,109,255,0.15); }

@keyframes fadeIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }
</style>
