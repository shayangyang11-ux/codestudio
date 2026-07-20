<template>
  <section
    class="pattern-runtime"
    :class="[
      `pattern-${runtimeContext.pattern.toLowerCase()}`,
      `theme-${runtimeContext.theme.key}`,
    ]"
  >
    <template v-if="isAuthPattern">
      <section class="auth-page">
        <div class="auth-visual">
          <p class="theme-label">{{ runtimeContext.theme.label }}</p>
          <h1>{{ runtimeContext.brandTitle }}</h1>
          <p>{{ runtimeContext.brandSubtitle }}</p>

          <div class="auth-bullets">
            <article
              v-for="item in runtimeContext.brandBullets"
              :key="item"
            >
              <span />
              <strong>{{ item }}</strong>
            </article>
          </div>
        </div>

        <div class="auth-card-wrap">
          <article class="auth-card">
            <header>
              <p class="eyebrow">
                {{ runtimeContext.pattern === 'REGISTER_FORM' ? 'Create Account' : 'Welcome Back' }}
              </p>
              <h2>{{ runtimeContext.visibleTitle }}</h2>
              <span>{{ authSubtitle }}</span>
            </header>

            <form
              class="auth-form"
              @submit.prevent="executeAction(authPrimaryActionId, authFormComponentId)"
            >
              <label
                v-for="field in authFields"
                :key="fieldKey(field)"
                class="auth-field"
                :class="{ invalid: Boolean(fieldErrors[fieldKey(field)]) }"
              >
                <span>
                  {{ fieldLabel(field) }}
                  <em v-if="fieldRequired(field)">*</em>
                </span>

                <input
                  v-if="isTextInput(field)"
                  :type="inputType(field)"
                  :value="displayValue(formValues[fieldKey(field)])"
                  :placeholder="fieldPlaceholder(field)"
                  :disabled="fieldDisabled(field)"
                  @input="updateField(fieldKey(field), inputValue($event))"
                />

                <select
                  v-else-if="fieldType(field) === 'SELECT'"
                  :value="displayValue(formValues[fieldKey(field)])"
                  :disabled="fieldDisabled(field)"
                  @change="updateField(fieldKey(field), inputValue($event))"
                >
                  <option value="">
                    {{ fieldPlaceholder(field) || '请选择' }}
                  </option>
                  <option
                    v-for="option in fieldOptions(field)"
                    :key="String(option.value)"
                    :value="option.value"
                    :disabled="option.disabled"
                  >
                    {{ option.label }}
                  </option>
                </select>

                <textarea
                  v-else-if="fieldType(field) === 'TEXTAREA'"
                  :value="displayValue(formValues[fieldKey(field)])"
                  :placeholder="fieldPlaceholder(field)"
                  :disabled="fieldDisabled(field)"
                  @input="updateField(fieldKey(field), inputValue($event))"
                />

                <input
                  v-else
                  :type="inputType(field)"
                  :value="displayValue(formValues[fieldKey(field)])"
                  :placeholder="fieldPlaceholder(field)"
                  :disabled="fieldDisabled(field)"
                  @input="updateField(fieldKey(field), inputValue($event))"
                />

                <small v-if="fieldErrors[fieldKey(field)]" class="field-error">
                  {{ fieldErrors[fieldKey(field)] }}
                </small>
              </label>

              <button
                v-if="authPrimaryActionId"
                class="auth-primary-button"
                type="submit"
              >
                {{ runtimeContext.primaryActionText }}
              </button>
            </form>

            <footer
              v-if="authSecondaryActions.length"
              class="auth-footer"
            >
              <button
                v-for="action in authSecondaryActions"
                :key="action.actionId"
                type="button"
                @click="executeAction(action.actionId, authFormComponentId)"
              >
                {{ actionLabel(action) }}
              </button>
            </footer>
          </article>
        </div>
      </section>
    </template>

    <template v-else>
      <section
        class="business-page"
        :class="[
          `business-page--${businessClass}`,
        ]"
      >
        <header class="business-header">
          <div>
            <p class="eyebrow">{{ businessEyebrow }}</p>
            <h1>{{ runtimeContext.visibleTitle }}</h1>
            <span>{{ runtimeContext.pageSubtitle || runtimeContext.theme.tone }}</span>
          </div>

          <div
            v-if="headerActions.length"
            class="header-actions"
          >
            <button
              v-for="action in headerActions"
              :key="action.actionId"
              type="button"
              @click="executeAction(action.actionId, firstComponentId)"
            >
              {{ actionLabel(action) }}
            </button>
          </div>
        </header>

        <main class="business-body">
          <UiContractRuntimeNode
            v-for="component in canvasComponents"
            :key="component.componentId"
            :component="component"
            :component-map="componentMap"
            :interactions="interactions"
            :form-values="formValues"
            :field-errors="fieldErrors"
            :mock-data="mockData"
            :active-tabs="activeTabs"
            :visible-component-ids="visibleComponentIds"
            :disabled-component-ids="disabledComponentIds"
            @execute="$emit('execute', $event)"
            @update-field="$emit('updateField', $event)"
            @switch-tab="$emit('switchTab', $event)"
          />
        </main>
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import {
  computed,
} from 'vue'
import UiContractRuntimeNode from '../UiContractRuntimeNode.vue'
import type {
  UiContractComponent,
  UiContractContent,
  UiContractInteraction,
} from '../../types/uiContract'
import type {
  LooseRecord,
  UiRuntimeRenderContext,
} from './uiContractTheme'
import {
  cleanDisplayText,
  isDeveloperOnlyText,
} from './uiContractTheme'

const props = defineProps<{
  contract: UiContractContent
  runtimeContext: UiRuntimeRenderContext
  canvasComponents: UiContractComponent[]
  componentMap: Record<string, UiContractComponent>
  interactions: UiContractInteraction[]
  formValues: Record<string, unknown>
  fieldErrors: Record<string, string>
  mockData: Record<string, unknown>
  activeTabs: Record<string, string>
  visibleComponentIds: string[]
  disabledComponentIds: string[]
}>()

const emit = defineEmits<{
  execute: [payload: { actionId: string; componentId: string; row?: Record<string, unknown> }]
  updateField: [payload: { fieldKey: string; value: unknown }]
  switchTab: [payload: { componentId: string; tabKey: string }]
}>()

const isAuthPattern = computed(() =>
  ['LOGIN_FORM', 'REGISTER_FORM'].includes(props.runtimeContext.pattern),
)

const allComponents = computed(() => {
  const result: UiContractComponent[] = []

  const visit = (items: UiContractComponent[]) => {
    for (const item of items) {
      result.push(item)
      visit(item.children || [])
    }
  }

  visit(props.canvasComponents)
  return result
})

const formComponents = computed(() => {
  return allComponents.value.filter((component) =>
    ['FORM', 'FIELD_GROUP'].includes(String(component.type || '').toUpperCase()),
  )
})

const authFormComponent = computed(() => {
  return formComponents.value[0] || props.canvasComponents[0] || null
})

const authFormComponentId = computed(() => {
  return authFormComponent.value?.componentId || firstComponentId.value
})

const authFields = computed(() => {
  const source = (authFormComponent.value as UiContractComponent & LooseRecord | null)?.fields
  return Array.isArray(source) ? source as LooseRecord[] : []
})

const authPrimaryActionId = computed(() => {
  const formActionIds = (authFormComponent.value as LooseRecord | null)?.actionIds

  if (Array.isArray(formActionIds) && formActionIds.length) {
    const submit = formActionIds.find((actionId) => {
      const action = actionById(String(actionId))
      return action && isSubmitAction(action)
    })

    return String(submit || formActionIds[0])
  }

  const submitAction = props.interactions.find(isSubmitAction)
  return submitAction?.actionId || ''
})

const authSecondaryActions = computed(() => {
  return props.interactions.filter((action) => {
    if (!action.actionId || action.actionId === authPrimaryActionId.value) {
      return false
    }

    const text = `${action.actionId} ${action.label || ''} ${action.type || ''}`.toLowerCase()

    return String(action.type || '').toUpperCase() === 'NAVIGATE' ||
      text.includes('register') ||
      text.includes('注册') ||
      text.includes('forgot') ||
      text.includes('找回')
  })
})

const authSubtitle = computed(() => {
  if (props.runtimeContext.pattern === 'REGISTER_FORM') {
    return '填写必要信息后即可完成账号创建。'
  }

  return '请输入账号信息进入系统。'
})

const businessClass = computed(() => {
  if (['SEARCH_LIST', 'CRUD_LIST'].includes(props.runtimeContext.pattern)) return 'list'
  if (props.runtimeContext.pattern === 'DASHBOARD') return 'dashboard'
  if (['PROFILE_PAGE', 'DETAIL_PAGE'].includes(props.runtimeContext.pattern)) return 'detail'
  if (['EDIT_FORM', 'SETTINGS_FORM', 'UPLOAD_FORM'].includes(props.runtimeContext.pattern)) return 'form'
  if (props.runtimeContext.pattern === 'WORKFLOW_STEP') return 'workflow'
  return 'generic'
})

const businessEyebrow = computed(() => {
  const map: Record<string, string> = {
    SEARCH_LIST: 'Search & List',
    CRUD_LIST: 'Data Management',
    DASHBOARD: 'Dashboard',
    PROFILE_PAGE: 'Profile',
    DETAIL_PAGE: 'Detail',
    EDIT_FORM: 'Edit Form',
    SETTINGS_FORM: 'Settings',
    UPLOAD_FORM: 'Upload',
    WORKFLOW_STEP: 'Workflow',
    GENERIC_PAGE: 'Business Page',
  }

  return map[props.runtimeContext.pattern] || 'Business Page'
})

const firstComponentId = computed(() => {
  return props.canvasComponents[0]?.componentId || props.contract.page.pageId || 'page_root'
})

const headerActions = computed(() => {
  if (['SEARCH_LIST', 'CRUD_LIST', 'DASHBOARD'].includes(props.runtimeContext.pattern)) {
    return props.interactions
      .filter((action) => {
        const type = String(action.type || '').toUpperCase()
        return ['QUERY_DATA', 'OPEN_MODAL', 'NAVIGATE', 'SUBMIT_FORM'].includes(type)
      })
      .slice(0, 3)
  }

  return []
})

function actionById(
  actionId: string,
): UiContractInteraction | undefined {
  return props.interactions.find((action) => action.actionId === actionId)
}

function isSubmitAction(
  action: UiContractInteraction,
): boolean {
  return ['SUBMIT_FORM', 'SUBMIT'].includes(String(action.type || '').toUpperCase())
}

function actionLabel(
  action: UiContractInteraction,
): string {
  const label = cleanDisplayText(action.label)

  if (label) {
    return label
  }

  const text = `${action.actionId || ''} ${action.type || ''}`.toLowerCase()

  if (text.includes('register') || text.includes('注册')) return '立即注册'
  if (text.includes('forgot') || text.includes('找回')) return '找回密码'
  if (text.includes('login') || text.includes('登录')) return '登录'
  if (text.includes('save') || text.includes('保存')) return '保存'
  if (text.includes('query') || text.includes('search') || text.includes('查询')) return '查询'

  return '操作'
}

function executeAction(
  actionId: string,
  componentId: string,
) {
  if (!actionId) {
    return
  }

  emit('execute', {
    actionId,
    componentId,
  })
}

function updateField(
  fieldKey: string,
  value: unknown,
) {
  emit('updateField', {
    fieldKey,
    value,
  })
}

function fieldKey(
  field: LooseRecord,
): string {
  return String(field.fieldKey || field.key || field.field || field.name || '')
}

function fieldLabel(
  field: LooseRecord,
): string {
  const raw = cleanDisplayText(field.label)

  if (raw) {
    return raw
  }

  const key = fieldKey(field).toLowerCase()

  if (key.includes('password') || key.includes('pwd')) return '密码'
  if (key.includes('phone') || key.includes('mobile')) return '手机号'
  if (key.includes('mail')) return '邮箱'
  if (key.includes('account') || key.includes('user')) return '账号'

  return '字段'
}

function fieldPlaceholder(
  field: LooseRecord,
): string {
  const raw = cleanDisplayText(field.placeholder)
  if (raw) return raw

  const label = fieldLabel(field)
  return `请输入${label}`
}

function fieldRequired(
  field: LooseRecord,
): boolean {
  return field.required === true
}

function fieldDisabled(
  field: LooseRecord,
): boolean {
  return field.disabled === true
}

function fieldType(
  field: LooseRecord,
): string {
  return String(field.type || 'TEXT').toUpperCase()
}

function fieldOptions(
  field: LooseRecord,
): LooseRecord[] {
  return Array.isArray(field.options) ? field.options : []
}

function isTextInput(
  field: LooseRecord,
): boolean {
  return ['TEXT', 'PASSWORD', 'EMAIL', 'PHONE', 'NUMBER', 'DATE'].includes(fieldType(field))
}

function inputType(
  field: LooseRecord,
): string {
  const type = fieldType(field)

  if (type === 'PASSWORD') return 'password'
  if (type === 'EMAIL') return 'email'
  if (type === 'PHONE') return 'tel'
  if (type === 'NUMBER') return 'number'
  if (type === 'DATE') return 'date'

  return 'text'
}

function displayValue(
  value: unknown,
): string {
  if (value === null || value === undefined) return ''
  return String(value)
}

function inputValue(
  event: Event,
): string {
  const target = event.target

  return target instanceof HTMLInputElement ||
    target instanceof HTMLTextAreaElement ||
    target instanceof HTMLSelectElement
    ? target.value
    : ''
}
</script>

<style scoped>
.pattern-runtime {
  width: 100%;
  height: 100%;
  color: var(--runtime-text-main);
}

.auth-page {
  width: 100%;
  height: 100%;
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(430px, 0.95fr);
  background:
    radial-gradient(circle at 20% 20%, color-mix(in srgb, var(--runtime-primary) 16%, transparent), transparent 32%),
    radial-gradient(circle at 88% 16%, color-mix(in srgb, var(--runtime-secondary) 14%, transparent), transparent 28%),
    linear-gradient(135deg, #f8fbff 0%, var(--runtime-page-bg) 50%, #ffffff 100%);
}

.auth-visual {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 84px 96px;
}

.theme-label {
  width: fit-content;
  margin: 0 0 24px;
  border: 1px solid color-mix(in srgb, var(--runtime-primary) 28%, transparent);
  border-radius: 999px;
  padding: 8px 14px;
  background: color-mix(in srgb, var(--runtime-primary) 8%, #fff);
  color: var(--runtime-primary);
  font-size: 13px;
  font-weight: 900;
}

.auth-visual h1 {
  max-width: 560px;
  margin: 0;
  color: var(--runtime-text-main);
  font-size: 48px;
  line-height: 1.12;
  letter-spacing: -0.045em;
}

.auth-visual > p:not(.theme-label) {
  max-width: 540px;
  margin: 20px 0 0;
  color: var(--runtime-text-muted);
  font-size: 17px;
  line-height: 1.85;
}

.auth-bullets {
  display: grid;
  gap: 15px;
  margin-top: 38px;
}

.auth-bullets article {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--runtime-text-main);
  font-size: 15px;
}

.auth-bullets span {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: linear-gradient(135deg, var(--runtime-primary), var(--runtime-secondary));
  box-shadow: 0 0 0 6px color-mix(in srgb, var(--runtime-primary) 12%, transparent);
}

.auth-card-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 72px 100px 72px 40px;
}

.auth-card {
  width: 440px;
  border: 1px solid color-mix(in srgb, var(--runtime-border) 78%, transparent);
  border-radius: 30px;
  padding: 36px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow:
    0 30px 90px rgba(15, 23, 42, 0.16),
    0 0 0 12px rgba(255, 255, 255, 0.28);
  backdrop-filter: blur(18px);
}

.auth-card header {
  margin-bottom: 26px;
}

.eyebrow {
  margin: 0 0 8px;
  color: var(--runtime-primary);
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.auth-card h2 {
  margin: 0;
  color: var(--runtime-text-main);
  font-size: 28px;
  letter-spacing: -0.025em;
}

.auth-card header span {
  display: block;
  margin-top: 9px;
  color: var(--runtime-text-muted);
  font-size: 14px;
  line-height: 1.7;
}

.auth-form {
  display: grid;
  gap: 17px;
}

.auth-field {
  display: grid;
  gap: 8px;
}

.auth-field > span {
  color: var(--runtime-text-main);
  font-size: 13px;
  font-weight: 850;
}

.auth-field em {
  color: #ef4444;
  font-style: normal;
}

.auth-field input,
.auth-field select,
.auth-field textarea {
  width: 100%;
  min-height: 48px;
  box-sizing: border-box;
  border: 1px solid color-mix(in srgb, var(--runtime-border) 92%, transparent);
  border-radius: 14px;
  padding: 0 14px;
  background: #ffffff;
  color: var(--runtime-text-main);
  font-size: 14px;
  outline: none;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.auth-field textarea {
  min-height: 92px;
  padding-top: 12px;
}

.auth-field input:focus,
.auth-field select:focus,
.auth-field textarea:focus {
  border-color: var(--runtime-primary);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--runtime-primary) 12%, transparent);
}

.field-error {
  color: #dc2626;
  font-size: 12px;
}

.auth-primary-button {
  width: 100%;
  min-height: 48px;
  border: none;
  border-radius: 15px;
  background: linear-gradient(135deg, var(--runtime-primary), var(--runtime-secondary));
  color: #ffffff;
  font-size: 15px;
  font-weight: 900;
  cursor: pointer;
  box-shadow: 0 16px 36px color-mix(in srgb, var(--runtime-primary) 28%, transparent);
}

.auth-footer {
  display: flex;
  justify-content: center;
  gap: 14px;
  margin-top: 18px;
}

.auth-footer button {
  border: none;
  background: transparent;
  color: var(--runtime-primary);
  font-size: 13px;
  font-weight: 850;
  cursor: pointer;
}

.business-page {
  width: 100%;
  height: 100%;
  overflow: auto;
  background:
    radial-gradient(circle at 18% 12%, color-mix(in srgb, var(--runtime-primary) 10%, transparent), transparent 28%),
    linear-gradient(135deg, #f8fafc 0%, var(--runtime-page-bg) 100%);
}

.business-header {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 42px 56px 22px;
}

.business-header h1 {
  margin: 0;
  color: var(--runtime-text-main);
  font-size: 30px;
  letter-spacing: -0.03em;
}

.business-header span {
  display: block;
  margin-top: 9px;
  color: var(--runtime-text-muted);
  font-size: 14px;
  line-height: 1.7;
}

.header-actions {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.header-actions button {
  border: 1px solid color-mix(in srgb, var(--runtime-primary) 22%, transparent);
  border-radius: 12px;
  padding: 10px 14px;
  background: #ffffff;
  color: var(--runtime-primary);
  font-weight: 850;
  cursor: pointer;
}

.business-body {
  display: grid;
  gap: 18px;
  padding: 18px 56px 56px;
}

.business-page--dashboard .business-body {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.business-page--detail .business-body,
.business-page--form .business-body,
.business-page--workflow .business-body {
  max-width: 980px;
}

.business-page :deep(.node-header p),
.business-page :deep(.component-description),
.business-page :deep(.node-description) {
  display: none !important;
}

.business-page :deep(.runtime-node),
.business-page :deep(.node-card),
.business-page :deep(.component-card),
.business-page :deep(.node-form),
.business-page :deep(.component-form) {
  border-radius: var(--runtime-radius);
  background: var(--runtime-surface);
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.08);
}

.business-page :deep(input),
.business-page :deep(textarea),
.business-page :deep(select) {
  min-height: 42px;
  border-radius: 12px;
}

.business-page :deep(button) {
  border-radius: 12px;
}

@media (max-width: 980px) {
  .auth-page {
    grid-template-columns: 1fr;
  }

  .auth-visual {
    display: none;
  }

  .auth-card-wrap {
    padding: 48px;
  }

  .business-header {
    padding: 30px 28px 14px;
  }

  .business-body {
    padding: 14px 28px 36px;
  }

  .business-page--dashboard .business-body {
    grid-template-columns: 1fr;
  }
}
</style>
