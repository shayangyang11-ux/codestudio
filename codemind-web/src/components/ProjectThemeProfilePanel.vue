<template>
  <!-- A9.10.14.4.3.15.4.4_STYLE_OVERRIDE_LOCK -->
  <section ref="panelRootRef" class="theme-profile-panel">
    <div class="outer-resize-handle" title="拖动调整右侧面板宽度" @mousedown.prevent="startResize" />

    <header class="panel-header">
      <div>
        <p class="eyebrow">Project Theme Profile</p>
        <h3>项目风格配置</h3>
        <span>统一当前项目所有交互原型和后续生成页面的视觉方向。</span>
      </div>

      <button type="button" :disabled="isLoading || isSaving" @click="loadProfile">刷新</button>
    </header>

    <ProjectUnifiedStyleAssistantPanel :workspace-id="workspaceId" :project-code="projectCode" @applied="handleAssistantApplied" />

    <div v-if="errorMessage" class="message error">{{ errorMessage }}</div>
    <div v-if="savedMessage" class="message success">{{ savedMessage }}</div>

    <div class="theme-grid">
      <label>
        <span>项目行业</span>
        <select v-model="form.industry" :disabled="isLoading || isSaving" @change="handleIndustryChanged">
          <option v-for="option in PROJECT_INDUSTRY_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
        </select>
        <small>{{ selectedIndustryDescription }}</small>
      </label>

      <label>
        <span>主题风格</span>
        <select v-model="form.themeKey" :disabled="isLoading || isSaving" @change="handleThemeChanged">
          <option v-for="option in PROJECT_THEME_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
        </select>
      </label>

      <label><span>主色</span><input v-model="form.primaryColor" :disabled="isLoading || isSaving" type="color" /></label>
      <label><span>密度</span><select v-model="form.density" :disabled="isLoading || isSaving"><option value="COMPACT">紧凑</option><option value="STANDARD">标准</option><option value="COMFORTABLE">舒展</option></select></label>
      <label><span>圆角</span><select v-model="form.radius" :disabled="isLoading || isSaving"><option value="SMALL">小圆角</option><option value="MEDIUM">中圆角</option><option value="LARGE">大圆角</option></select></label>
      <label><span>登录页布局</span><select v-model="form.loginLayout" :disabled="isLoading || isSaving"><option value="BRAND_SPLIT">品牌分栏</option><option value="CENTER_CARD">居中卡片</option><option value="MINIMAL">极简</option></select></label>
      <label><span>导航风格</span><select v-model="form.navigationStyle" :disabled="isLoading || isSaving"><option value="SIDE_NAV">侧边导航</option><option value="TOP_NAV">顶部导航</option><option value="MIXED">混合导航</option></select></label>
      <label class="check-row"><input v-model="form.darkModeEnabled" :disabled="isLoading || isSaving" type="checkbox" /><span>启用暗色模式偏好</span></label>
      <label class="check-row lock-row">
        <input v-model="form.locked" :disabled="isLoading || isSaving" type="checkbox" />
        <span>锁定当前风格，不再被自动识别覆盖</span>
      </label>
    </div>

    <label class="notes-field">
      <span>风格备注</span>
      <textarea v-model="form.notes" :disabled="isLoading || isSaving" placeholder="例如：更偏科技感、按钮更圆润、首页信息密度更高。" />
    </label>

    <footer>
      <div class="profile-meta">
        <span v-if="profile">来源：{{ profile.source }}</span>
        <span v-if="profile?.locked">已锁定用户风格</span>
        <span v-if="profile?.updatedAt">更新：{{ formatDate(profile.updatedAt) }}</span>
      </div>
      <button type="button" class="primary" :disabled="isLoading || isSaving" @click="saveProfile">{{ isSaving ? '保存中...' : '保存风格配置' }}</button>
    </footer>

    <ProjectThemeProfileHistoryPanel v-if="profile" class="history-panel" :workspace-id="workspaceId" :project-code="projectCode" @rollback="handleRollbackApplied" />
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import ProjectUnifiedStyleAssistantPanel from './ProjectUnifiedStyleAssistantPanel.vue'
import ProjectThemeProfileHistoryPanel from './ProjectThemeProfileHistoryPanel.vue'
import { fetchProjectThemeProfile, updateProjectThemeProfile } from '../api/themeProfileApi'
import type { ProjectThemeProfile, UpdateProjectThemeProfileRequest } from '../types/themeProfile'
import { PROJECT_INDUSTRY_OPTIONS, PROJECT_THEME_OPTIONS, defaultPrimaryColorForTheme, defaultThemeKeyForIndustry, visualToneForTheme } from '../types/themeProfile'

const props = defineProps<{ workspaceId: string; projectCode: string }>()
const emit = defineEmits<{ changed: [profile: ProjectThemeProfile] }>()

const RESIZE_STORAGE_KEY = 'codemind.projectThemeProfileDrawerWidth'
const MIN_DRAWER_WIDTH = 480
const MAX_DRAWER_WIDTH = 980

const profile = ref<ProjectThemeProfile | null>(null)
const isLoading = ref(false)
const isSaving = ref(false)
const errorMessage = ref('')
const savedMessage = ref('')
const panelRootRef = ref<HTMLElement | null>(null)
const resizeTargetRef = ref<HTMLElement | null>(null)

const form = reactive<UpdateProjectThemeProfileRequest>({
  industry: 'GENERAL',
  themeKey: 'enterprise_default',
  visualTone: 'CLEAN',
  primaryColor: '#2563eb',
  secondaryColor: null,
  accentColor: null,
  density: 'STANDARD',
  radius: 'MEDIUM',
  loginLayout: 'BRAND_SPLIT',
  navigationStyle: 'SIDE_NAV',
  darkModeEnabled: false,
  locked: false,
  notes: '',
})

const selectedIndustryDescription = computed(() => PROJECT_INDUSTRY_OPTIONS.find((option) => option.value === form.industry)?.description || '')

watch(() => [props.workspaceId, props.projectCode], () => { void loadProfile() })

onMounted(() => {
  void loadProfile()
  void nextTick(() => restoreDrawerWidth())
})

onBeforeUnmount(() => stopResize())

async function loadProfile() {
  if (!props.workspaceId || !props.projectCode) return
  isLoading.value = true
  errorMessage.value = ''
  savedMessage.value = ''
  try {
    const response = await fetchProjectThemeProfile(props.workspaceId, props.projectCode)
    profile.value = response
    syncForm(response)
    emit('changed', response)
  } catch (error) {
    errorMessage.value = resolveErrorMessage(error, '加载项目风格配置失败。')
  } finally {
    isLoading.value = false
  }
}

async function saveProfile() {
  if (!props.workspaceId || !props.projectCode) return
  isSaving.value = true
  errorMessage.value = ''
  savedMessage.value = ''
  try {
    const response = await updateProjectThemeProfile(props.workspaceId, props.projectCode, { ...form, notes: form.notes?.trim() || null })
    profile.value = response
    syncForm(response)
    savedMessage.value = '项目风格配置已保存。'
    emit('changed', response)
  } catch (error) {
    errorMessage.value = resolveErrorMessage(error, '保存项目风格配置失败。')
  } finally {
    isSaving.value = false
  }
}

function handleAssistantApplied(nextProfile: ProjectThemeProfile) {
  profile.value = nextProfile
  syncForm(nextProfile)
  savedMessage.value = '风格助手建议已应用。'
  errorMessage.value = ''
  emit('changed', nextProfile)
}

function handleRollbackApplied(nextProfile: ProjectThemeProfile) {
  profile.value = nextProfile
  syncForm(nextProfile)
  savedMessage.value = '项目风格已回滚。'
  errorMessage.value = ''
  emit('changed', nextProfile)
}

function handleIndustryChanged() {
  form.locked = true
  form.themeKey = defaultThemeKeyForIndustry(form.industry)
  handleThemeChanged()
}

function handleThemeChanged() {
  form.locked = true
  form.visualTone = visualToneForTheme(form.themeKey)
  form.primaryColor = defaultPrimaryColorForTheme(form.themeKey)
}

function syncForm(source: ProjectThemeProfile) {
  form.industry = source.industry
  form.themeKey = source.themeKey
  form.visualTone = source.visualTone
  form.primaryColor = source.primaryColor || defaultPrimaryColorForTheme(source.themeKey)
  form.secondaryColor = source.secondaryColor || null
  form.accentColor = source.accentColor || null
  form.density = source.density
  form.radius = source.radius
  form.loginLayout = source.loginLayout
  form.navigationStyle = source.navigationStyle
  form.darkModeEnabled = source.darkModeEnabled
  form.locked = source.locked
  form.notes = source.notes || ''
}

function restoreDrawerWidth() {
  const stored = Number(localStorage.getItem(RESIZE_STORAGE_KEY))
  if (!Number.isFinite(stored) || stored <= 0) return
  const target = resolveResizeTarget()
  if (target) applyDrawerWidth(target, clampWidth(stored))
}

function startResize(event: MouseEvent) {
  const target = resolveResizeTarget()
  if (!target) return
  resizeTargetRef.value = target
  const startX = event.clientX
  const startWidth = target.getBoundingClientRect().width
  const handleMouseMove = (moveEvent: MouseEvent) => {
    const delta = startX - moveEvent.clientX
    applyDrawerWidth(target, clampWidth(startWidth + delta))
  }
  const handleMouseUp = () => {
    document.removeEventListener('mousemove', handleMouseMove)
    document.removeEventListener('mouseup', handleMouseUp)
    document.body.classList.remove('codemind-resizing-theme-drawer')
    localStorage.setItem(RESIZE_STORAGE_KEY, String(Math.round(target.getBoundingClientRect().width)))
  }
  document.addEventListener('mousemove', handleMouseMove)
  document.addEventListener('mouseup', handleMouseUp)
  document.body.classList.add('codemind-resizing-theme-drawer')
}

function stopResize() { document.body.classList.remove('codemind-resizing-theme-drawer') }

function resolveResizeTarget() {
  if (resizeTargetRef.value) return resizeTargetRef.value
  let current = panelRootRef.value?.parentElement || null
  while (current && current !== document.body) {
    const className = String(current.className || '').toLowerCase()
    const role = String(current.getAttribute('role') || '').toLowerCase()
    const width = current.getBoundingClientRect().width
    if (className.includes('drawer') || className.includes('side') || className.includes('panel') || role.includes('dialog') || width >= 420) {
      resizeTargetRef.value = current
      return current
    }
    current = current.parentElement
  }
  resizeTargetRef.value = panelRootRef.value || null
  return resizeTargetRef.value
}

function applyDrawerWidth(target: HTMLElement, width: number) {
  const value = `${width}px`
  target.style.width = value
  target.style.minWidth = value
  target.style.maxWidth = value
  target.style.flexBasis = value
  target.style.overflowX = 'hidden'
}

function clampWidth(width: number) {
  const viewportLimit = Math.max(MIN_DRAWER_WIDTH, Math.min(window.innerWidth - 80, MAX_DRAWER_WIDTH))
  return Math.max(MIN_DRAWER_WIDTH, Math.min(width, viewportLimit))
}

function formatDate(value: string) { return value ? new Date(value).toLocaleString() : '' }
function resolveErrorMessage(error: unknown, fallback: string): string { return error instanceof Error && error.message ? error.message : fallback }
</script>

<style scoped>
.theme-profile-panel { position: relative; width: 100%; min-width: 0; overflow-x: hidden; display: grid; gap: 16px; border: 1px solid #dbe4ef; border-radius: 18px; padding: 18px; background: #ffffff; box-shadow: 0 16px 38px rgba(15, 23, 42, 0.06); }
.theme-profile-panel, .theme-profile-panel * { box-sizing: border-box; overflow-wrap: anywhere; word-break: break-word; }
.outer-resize-handle { position: absolute; top: 0; left: -8px; z-index: 30; width: 12px; height: 100%; cursor: ew-resize; border-radius: 999px; }
.outer-resize-handle:hover { background: linear-gradient(to right, rgba(79, 70, 229, 0.18), rgba(79, 70, 229, 0.04)); }
.panel-header { display: flex; justify-content: space-between; gap: 16px; }
.eyebrow { margin: 0 0 5px; color: #2563eb; font-size: 11px; font-weight: 900; letter-spacing: 0.08em; text-transform: uppercase; }
h3 { margin: 0; color: #0f172a; }
.panel-header span, label small, .profile-meta { color: #64748b; font-size: 12px; line-height: 1.6; }
.panel-header button, footer button { height: 36px; border: 1px solid #dbe4ef; border-radius: 11px; padding: 0 13px; background: #ffffff; color: #334155; font-weight: 800; cursor: pointer; }
.theme-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
label { min-width: 0; display: grid; gap: 7px; color: #0f172a; font-size: 13px; font-weight: 800; }
select, input[type='color'], textarea { width: 100%; min-width: 0; min-height: 38px; border: 1px solid #dbe4ef; border-radius: 11px; padding: 0 10px; background: #ffffff; color: #334155; }
input[type='color'] { padding: 4px; }
.check-row { grid-template-columns: auto 1fr; align-items: center; gap: 8px; }
textarea { min-height: 78px; padding: 10px; resize: vertical; }
footer { display: flex; justify-content: space-between; gap: 16px; }
footer button.primary { border-color: #2563eb; background: #2563eb; color: #ffffff; }
.message { border-radius: 12px; padding: 10px 12px; font-size: 13px; font-weight: 700; }
.message.error { background: #fef2f2; color: #b91c1c; }
.message.success { background: #f0fdf4; color: #166534; }
.history-panel { min-width: 0; }
:global(.codemind-resizing-theme-drawer) { cursor: ew-resize !important; user-select: none !important; }
@media (max-width: 760px) { .theme-grid { grid-template-columns: 1fr; } footer { display: grid; } }
</style>
