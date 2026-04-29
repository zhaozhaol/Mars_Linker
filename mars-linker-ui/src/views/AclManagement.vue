<template>
  <div class="acl-page">
    <div v-if="loading" class="skeleton">
      <div v-for="i in 3" :key="i" class="skeleton-row"></div>
    </div>
    <template v-else-if="aclConfig">
      <div class="config-section">
        <h3 class="section-title">ACL 状态</h3>
        <div class="status-row">
          <div class="config-row">
            <div class="config-row-left">
              <span class="config-icon">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
              </span>
              <span class="config-name">ACL 启用</span>
            </div>
            <span class="config-val" :class="aclConfig.aclEnabled ? 'val-on' : 'val-off'">
              <span class="dot" :class="{ on: aclConfig.aclEnabled }"></span>
              {{ aclConfig.aclEnabled ? '启用' : '禁用' }}
            </span>
          </div>
          <div class="config-row">
            <div class="config-row-left">
              <span class="config-icon">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
              </span>
              <span class="config-name">ACL 模式</span>
            </div>
            <span class="config-val mono">{{ aclConfig.aclMode }}</span>
          </div>
          <div class="config-row">
            <div class="config-row-left">
              <span class="config-icon">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
              </span>
              <span class="config-name">默认拒绝</span>
            </div>
            <span class="config-val" :class="aclConfig.aclDefaultDeny ? 'val-off' : 'val-on'">
              <span class="dot" :class="{ on: !aclConfig.aclDefaultDeny }"></span>
              {{ aclConfig.aclDefaultDeny ? '是' : '否' }}
            </span>
          </div>
        </div>
      </div>

      <div class="rules-grid">
        <div class="rules-panel">
          <div class="rules-header">
            <span class="rules-title">SUBSCRIBE 规则</span>
          </div>
          <div class="rules-section">
            <div class="rules-label allow">允许前缀</div>
            <div class="prefix-list">
              <div v-for="(p, i) in aclConfig.allowSubscribePrefixes" :key="'as-'+i" class="prefix-item allow">
                <span class="prefix-text mono">{{ p }}</span>
              </div>
              <div v-if="!aclConfig.allowSubscribePrefixes.length" class="prefix-empty">无</div>
            </div>
          </div>
          <div class="rules-section">
            <div class="rules-label deny">拒绝前缀</div>
            <div class="prefix-list">
              <div v-for="(p, i) in aclConfig.denySubscribePrefixes" :key="'ds-'+i" class="prefix-item deny">
                <span class="prefix-text mono">{{ p }}</span>
              </div>
              <div v-if="!aclConfig.denySubscribePrefixes.length" class="prefix-empty">无</div>
            </div>
          </div>
        </div>

        <div class="rules-panel">
          <div class="rules-header">
            <span class="rules-title">PUBLISH 规则</span>
          </div>
          <div class="rules-section">
            <div class="rules-label allow">允许前缀</div>
            <div class="prefix-list">
              <div v-for="(p, i) in aclConfig.allowPublishPrefixes" :key="'ap-'+i" class="prefix-item allow">
                <span class="prefix-text mono">{{ p }}</span>
              </div>
              <div v-if="!aclConfig.allowPublishPrefixes.length" class="prefix-empty">无</div>
            </div>
          </div>
          <div class="rules-section">
            <div class="rules-label deny">拒绝前缀</div>
            <div class="prefix-list">
              <div v-for="(p, i) in aclConfig.denyPublishPrefixes" :key="'dp-'+i" class="prefix-item deny">
                <span class="prefix-text mono">{{ p }}</span>
              </div>
              <div v-if="!aclConfig.denyPublishPrefixes.length" class="prefix-empty">无</div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="aclConfig.aclMode === 'http'" class="config-section">
        <h3 class="section-title">HTTP 动态 ACL</h3>
        <div class="status-row">
          <div class="config-row">
            <div class="config-row-left">
              <span class="config-icon"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg></span>
              <span class="config-name">拉取地址</span>
            </div>
            <span class="config-val mono">{{ aclConfig.aclHttpUrl ?? '--' }}</span>
          </div>
          <div class="config-row">
            <div class="config-row-left">
              <span class="config-icon"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg></span>
              <span class="config-name">刷新间隔</span>
            </div>
            <span class="config-val mono">{{ aclConfig.aclHttpRefreshIntervalMs }} ms</span>
          </div>
        </div>
      </div>

      <div class="config-section">
        <h3 class="section-title">权限测试</h3>
        <div class="test-form">
          <div class="test-row">
            <div class="field">
              <label class="field-label">Topic</label>
              <input v-model="testTopic" class="field-input" placeholder="如 sensor/temperature/room1" />
            </div>
            <div class="field">
              <label class="field-label">操作</label>
              <div class="action-pills">
                <button class="pill" :class="{ active: testAction === 'subscribe' }" @click="testAction = 'subscribe'">SUBSCRIBE</button>
                <button class="pill" :class="{ activeB: testAction === 'publish' }" @click="testAction = 'publish'">PUBLISH</button>
              </div>
            </div>
            <button class="ml-btn ml-btn-primary" @click="runTest" :disabled="testLoading || !testTopic.trim()">
              {{ testLoading ? '测试中...' : '测试' }}
            </button>
          </div>
          <div v-if="testResult !== null" class="test-result" :class="testResult ? 'allowed' : 'denied'">
            <svg v-if="testResult" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
            <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
            <span>{{ testResult ? '允许' : '拒绝' }} — Topic "{{ testTopic }}" 的 {{ testAction }} 操作{{ testResult ? '被允许' : '被拒绝' }}</span>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getAclConfig, testAcl } from '../api/acl'
import type { AclConfig } from '../types/acl'

const aclConfig = ref<AclConfig | null>(null)
const loading = ref(true)

const testTopic = ref('')
const testAction = ref<'subscribe' | 'publish'>('subscribe')
const testLoading = ref(false)
const testResult = ref<boolean | null>(null)

const loadConfig = async () => {
  loading.value = true
  try {
    aclConfig.value = await getAclConfig()
  } catch {
    ElMessage.error('ACL 配置加载失败')
  } finally {
    loading.value = false
  }
}

const runTest = async () => {
  if (!testTopic.value.trim()) return
  testLoading.value = true
  testResult.value = null
  try {
    const result = await testAcl({ topic: testTopic.value, action: testAction.value })
    testResult.value = result.allowed
  } catch {
    ElMessage.error('ACL 测试失败')
  } finally {
    testLoading.value = false
  }
}

onMounted(loadConfig)
</script>

<style scoped>
.acl-page { animation: fadeIn 0.3s ease; }
.config-section { margin-bottom: 24px; }
.section-title { font-size: 13px; font-weight: 600; color: rgba(255,255,255,0.4); text-transform: uppercase; letter-spacing: 0.5px; margin: 0 0 12px; }
.status-row { display: flex; flex-direction: column; gap: 4px; }
.config-row { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.04); border-radius: 10px; }
.config-row-left { display: flex; align-items: center; gap: 10px; }
.config-icon { color: rgba(255,255,255,0.3); display: flex; align-items: center; }
.config-name { font-size: 13px; color: rgba(255,255,255,0.65); font-weight: 500; }
.config-val { font-size: 14px; font-weight: 600; color: #fff; display: flex; align-items: center; gap: 6px; }
.config-val.mono { font-family: 'SF Mono','Fira Code',monospace; }
.val-on { color: #10b981; }
.val-off { color: #64748b; }
.dot { width: 6px; height: 6px; border-radius: 50%; background: #64748b; }
.dot.on { background: #10b981; box-shadow: 0 0 6px rgba(16,185,129,0.4); }

.rules-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 24px; }
.rules-panel { background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.06); border-radius: 12px; padding: 20px; }
.rules-header { margin-bottom: 16px; }
.rules-title { font-size: 14px; font-weight: 600; color: rgba(255,255,255,0.85); }
.rules-section { margin-bottom: 16px; }
.rules-label { font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 8px; }
.rules-label.allow { color: #10b981; }
.rules-label.deny { color: #ef4444; }
.prefix-list { display: flex; flex-direction: column; gap: 4px; }
.prefix-item { padding: 8px 14px; border-radius: 8px; }
.prefix-item.allow { background: rgba(16,185,129,0.06); border: 1px solid rgba(16,185,129,0.1); }
.prefix-item.deny { background: rgba(239,68,68,0.06); border: 1px solid rgba(239,68,68,0.1); }
.prefix-text { font-size: 13px; color: #fff; }
.prefix-empty { font-size: 12px; color: rgba(255,255,255,0.2); padding: 8px 0; }

.test-form {}
.test-row { display: flex; align-items: flex-end; gap: 12px; }
.field { display: flex; flex-direction: column; gap: 4px; }
.field-label { font-size: 11px; color: rgba(255,255,255,0.35); font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; }
.field-input { padding: 10px 14px; border: 1px solid rgba(255,255,255,0.08); border-radius: 8px; font-size: 13px; color: #fff; background: rgba(255,255,255,0.04); outline: none; width: 300px; transition: border-color 0.2s, box-shadow 0.2s; }
.field-input:focus { border-color: #4d6dff; box-shadow: 0 0 0 3px rgba(77,109,255,0.15); }
.action-pills { display: flex; gap: 4px; }
.pill { padding: 8px 16px; border: 1px solid rgba(255,255,255,0.08); background: transparent; border-radius: 8px; font-size: 12px; font-weight: 600; color: rgba(255,255,255,0.5); cursor: pointer; transition: all 0.2s; }
.pill.active { background: rgba(77,109,255,0.15); color: #4d6dff; border-color: rgba(77,109,255,0.3); }
.pill.activeB { background: rgba(124,92,252,0.15); color: #7c5cfc; border-color: rgba(124,92,252,0.3); }

.test-result { display: flex; align-items: center; gap: 8px; margin-top: 16px; padding: 12px 16px; border-radius: 10px; font-size: 14px; font-weight: 500; }
.test-result.allowed { background: rgba(16,185,129,0.08); color: #10b981; border: 1px solid rgba(16,185,129,0.15); }
.test-result.denied { background: rgba(239,68,68,0.08); color: #ef4444; border: 1px solid rgba(239,68,68,0.15); }

.skeleton {}
.skeleton-row { height: 44px; background: rgba(255,255,255,0.04); border-radius: 10px; margin-bottom: 8px; animation: shimmer 1.5s infinite; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }
@keyframes shimmer { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
</style>
