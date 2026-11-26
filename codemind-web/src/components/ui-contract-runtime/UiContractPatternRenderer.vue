<template>
  <section
    class="pattern-runtime"
    :class="[
      `pattern-${runtimeContext.pattern.toLowerCase()}`,
      `theme-${runtimeContext.theme.key}`,
      `industry-${runtimeContext.industryStyle.cssClass}`,
      `density-${runtimeContext.theme.density}`,
    ]"
  >
    <template v-if="isAuthPattern">
      <section class="auth-page">
        <div class="auth-visual">
          <p
            class="theme-label"
            data-ui-selectable="true"
            data-ui-element-kind="TEXT"
            data-ui-text-key="themeLabel"
            :style="pageHeaderInlineStyle('themeLabel')"
            :data-ui-component-id="firstComponentId"
            data-ui-component-type="PAGE_HEADER"
            :data-ui-element-label="runtimeContext.theme.label"
          >{{ runtimeContext.theme.label }}</p>
          <h1
            data-ui-selectable="true"
            data-ui-element-kind="TITLE"
            data-ui-text-key="brandTitle"
            :style="pageHeaderInlineStyle('brandTitle')"
            :data-ui-component-id="firstComponentId"
            data-ui-component-type="PAGE_HEADER"
            :data-ui-element-label="runtimeContext.brandTitle"
          >{{ runtimeContext.brandTitle }}</h1>
          <p
            data-ui-selectable="true"
            data-ui-element-kind="DESCRIPTION"
            data-ui-text-key="brandSubtitle"
            :style="pageHeaderInlineStyle('brandSubtitle')"
            :data-ui-component-id="firstComponentId"
            data-ui-component-type="PAGE_HEADER"
            :data-ui-element-label="runtimeContext.brandSubtitle"
          >{{ runtimeContext.brandSubtitle }}</p>

          <div class="auth-bullets">
            <article
              v-for="item in runtimeContext.brandBullets"
              :key="item"
              data-ui-selectable="true"
              data-ui-element-kind="TEXT"
              data-ui-text-key="brandBullet"
              :data-ui-component-id="firstComponentId"
              data-ui-component-type="PAGE_HEADER"
              :data-ui-element-label="item"
            >
              <span />
              <strong>{{ item }}</strong>
            </article>
          </div>
        </div>

        <div class="auth-card-wrap">
          <article
            class="auth-card"
            data-ui-selectable="true"
            data-ui-element-kind="COMPONENT"
            :data-ui-component-id="authFormComponentId"
            :data-ui-component-type="authFormComponent?.type || 'FORM'"
            :data-ui-element-label="runtimeContext.visibleTitle"
            :style="authComponentInlineStyle()"
          >
            <header>
              <p
                class="eyebrow"
                data-ui-selectable="true"
                data-ui-element-kind="TEXT"
                data-ui-text-key="eyebrow"
            :style="authTextInlineStyle('eyebrow')"
                :data-ui-component-id="authFormComponentId"
                :data-ui-component-type="authFormComponent?.type || 'FORM'"
                :data-ui-element-label="runtimeContext.pattern === 'REGISTER_FORM' ? 'Create Account' : 'Welcome Back'"
              >
                {{ runtimeContext.pattern === 'REGISTER_FORM' ? 'Create Account' : 'Welcome Back' }}
              </p>
              <h2
                data-ui-selectable="true"
                data-ui-element-kind="TITLE"
                data-ui-text-key="title"
            :style="authTextInlineStyle('title')"
                :data-ui-component-id="authFormComponentId"
                :data-ui-component-type="authFormComponent?.type || 'FORM'"
                :data-ui-element-label="runtimeContext.visibleTitle"
              >{{ runtimeContext.visibleTitle }}</h2>
              <span
                data-ui-selectable="true"
                data-ui-element-kind="DESCRIPTION"
                data-ui-text-key="description"
            :style="authTextInlineStyle('description')"
                :data-ui-component-id="authFormComponentId"
                :data-ui-component-type="authFormComponent?.type || 'FORM'"
                :data-ui-element-label="authSubtitle"
              >{{ authSubtitle }}</span>
            </header>

            <form
              class="auth-form"
              @submit.prevent="executeAction(authPrimaryActionId, authFormComponentId)"
            >
              <label
                v-for="field in authFields"
                :key="fieldKey(field)"
                class="auth-field"
                v-show="authFieldVisible(field)"
                :style="authFieldInlineStyle(field, 'field')"
                data-ui-selectable="true"
                data-ui-element-kind="FIELD"
                :data-ui-component-id="authFormComponentId"
                :data-ui-component-type="authFormComponent?.type || 'FORM'"
                :data-ui-field-key="fieldKey(field)"
                :data-ui-element-label="fieldLabel(field)"
                :class="{ invalid: Boolean(fieldErrors[fieldKey(field)]) }"
              >
                <span
                  data-ui-selectable="true"
                  data-ui-element-kind="FIELD_LABEL"
                  :data-ui-component-id="authFormComponentId"
                  :data-ui-component-type="authFormComponent?.type || 'FORM'"
                  :data-ui-field-key="fieldKey(field)"
                  :data-ui-element-label="fieldLabel(field)"
                  :style="authFieldInlineStyle(field, 'fieldLabel')"
                >
                  {{ fieldLabel(field) }}
                  <em v-if="fieldRequired(field)">*</em>
                </span>

                <input
                  v-if="isTextInput(field)"
                  :type="inputType(field)"
                  data-ui-selectable="true"
                  data-ui-element-kind="FIELD_INPUT"
                  :data-ui-component-id="authFormComponentId"
                  :data-ui-component-type="authFormComponent?.type || 'FORM'"
                  :data-ui-field-key="fieldKey(field)"
                  :data-ui-element-label="fieldLabel(field) || fieldPlaceholder(field)"
                  :style="authFieldInlineStyle(field, 'fieldControl')"
                  :value="displayValue(formValues[fieldKey(field)])"
                  :placeholder="fieldPlaceholder(field)"
                  :disabled="fieldDisabled(field)"
                  @input="updateField(fieldKey(field), inputValue($event))"
                />

                <select
                  v-else-if="fieldType(field) === 'SELECT'"
                  data-ui-selectable="true"
                  data-ui-element-kind="FIELD_INPUT"
                  :data-ui-component-id="authFormComponentId"
                  :data-ui-component-type="authFormComponent?.type || 'FORM'"
                  :data-ui-field-key="fieldKey(field)"
                  :data-ui-element-label="fieldLabel(field) || fieldPlaceholder(field)"
                  :style="authFieldInlineStyle(field, 'fieldControl')"
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
                  data-ui-selectable="true"
                  data-ui-element-kind="FIELD_INPUT"
                  :data-ui-component-id="authFormComponentId"
                  :data-ui-component-type="authFormComponent?.type || 'FORM'"
                  :data-ui-field-key="fieldKey(field)"
                  :data-ui-element-label="fieldLabel(field) || fieldPlaceholder(field)"
                  :style="authFieldInlineStyle(field, 'fieldControl')"
                  :value="displayValue(formValues[fieldKey(field)])"
                  :placeholder="fieldPlaceholder(field)"
                  :disabled="fieldDisabled(field)"
                  @input="updateField(fieldKey(field), inputValue($event))"
                />

                <input
                  v-else
                  :type="inputType(field)"
                  data-ui-selectable="true"
                  data-ui-element-kind="FIELD_INPUT"
                  :data-ui-component-id="authFormComponentId"
                  :data-ui-component-type="authFormComponent?.type || 'FORM'"
                  :data-ui-field-key="fieldKey(field)"
                  :data-ui-element-label="fieldLabel(field) || fieldPlaceholder(field)"
                  :style="authFieldInlineStyle(field, 'fieldControl')"
                  :value="displayValue(formValues[fieldKey(field)])"
                  :placeholder="fieldPlaceholder(field)"
                  :disabled="fieldDisabled(field)"
                  @input="updateField(fieldKey(field), inputValue($event))"
                />

                <small
                  v-if="fieldErrors[fieldKey(field)]"
                  class="field-error"
                  data-ui-selectable="true"
                  data-ui-element-kind="TEXT"
                  data-ui-text-key="fieldError"
                  :data-ui-component-id="authFormComponentId"
                  :data-ui-component-type="authFormComponent?.type || 'FORM'"
                  :data-ui-field-key="fieldKey(field)"
                  :data-ui-element-label="fieldErrors[fieldKey(field)]"
                >
                  {{ fieldErrors[fieldKey(field)] }}
                </small>
              </label>

              <button
                v-if="authPrimaryActionId"
                class="auth-primary-button"
                type="submit"
                data-ui-selectable="true"
                data-ui-element-kind="ACTION"
                :data-ui-component-id="authFormComponentId"
                :data-ui-component-type="authFormComponent?.type || 'FORM'"
                :data-ui-action-id="authPrimaryActionId"
                :data-ui-element-label="runtimeContext.primaryActionText"
                v-show="!authActionHidden(authPrimaryActionId)"
                :style="authActionInlineStyle(authPrimaryActionId)"
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
                data-ui-selectable="true"
                data-ui-element-kind="ACTION"
                :data-ui-component-id="authFormComponentId"
                :data-ui-component-type="authFormComponent?.type || 'FORM'"
                :data-ui-action-id="action.actionId"
                :data-ui-element-label="actionLabel(action)"
                v-show="!authActionHidden(action.actionId)"
                :style="authActionInlineStyle(action.actionId)"
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
            <p
              class="eyebrow"
              data-ui-selectable="true"
              data-ui-element-kind="TEXT"
              data-ui-text-key="eyebrow"
              :data-ui-component-id="firstComponentId"
              data-ui-component-type="PAGE_HEADER"
              :data-ui-element-label="businessEyebrow"
              :style="pageHeaderInlineStyle('eyebrow')"
            >{{ businessEyebrow }}</p>
            <h1
              data-ui-selectable="true"
              data-ui-element-kind="TITLE"
              data-ui-text-key="title"
              :data-ui-component-id="firstComponentId"
              data-ui-component-type="PAGE_HEADER"
              :data-ui-element-label="runtimeContext.visibleTitle"
              :style="pageHeaderInlineStyle('title')"
            >{{ runtimeContext.visibleTitle }}</h1>
            <span
              data-ui-selectable="true"
              data-ui-element-kind="DESCRIPTION"
              data-ui-text-key="description"
              :data-ui-component-id="firstComponentId"
              data-ui-component-type="PAGE_HEADER"
              :data-ui-element-label="runtimeContext.pageSubtitle || runtimeContext.theme.tone"
              :style="pageHeaderInlineStyle('description')"
            >{{ runtimeContext.pageSubtitle || runtimeContext.theme.tone }}</span>
          </div>

          <div
            v-if="headerActions.length"
            class="header-actions"
          >
            <button
              v-for="action in headerActions"
              :key="action.actionId"
              type="button"
              data-ui-selectable="true"
              data-ui-element-kind="ACTION"
              :data-ui-component-id="firstComponentId"
              data-ui-component-type="PAGE_HEADER"
              :data-ui-action-id="action.actionId"
              :data-ui-element-label="actionLabel(action)"
              :style="pageHeaderInlineStyle(`action:${action.actionId}`)"
              @click="executeAction(action.actionId, firstComponentId)"
            >
              {{ actionLabel(action) }}
            </button>
          </div>
        </header>

        <main class="business-body">
          <!-- A9.10.14.4.3.15.6.24G_ADMIN_PAGE_RENDERER: page-level admin layout renderer -->
          <AntDesignAdminPageRenderer
            v-if="shouldUseAntDesignRenderer"
            :components="businessDisplayComponents"
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

<ElementPlusAdminPageRenderer
            v-else
            :components="businessDisplayComponents"
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
import ElementPlusAdminPageRenderer from './ElementPlusAdminPageRenderer.vue'
import AntDesignAdminPageRenderer from './AntDesignAdminPageRenderer.vue'
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

const businessDisplayComponents = computed(() => {
  if (isAuthPattern.value) {
    return props.canvasComponents
  }

  const [root] = props.canvasComponents as Array<UiContractComponent & LooseRecord>

  if (
    props.canvasComponents.length === 1 &&
    root &&
    Array.isArray(root.children) &&
    root.children.length > 0 &&
    ['SECTION', 'PAGE_HEADER'].includes(String(root.type || '').toUpperCase())
  ) {
    return (root.children as UiContractComponent[])
      .filter((component) => String(component.type || '').toUpperCase() !== 'PAGE_HEADER')
  }

  return props.canvasComponents
    .filter((component) => String(component.type || '').toUpperCase() !== 'PAGE_HEADER')
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

const businessEyebrowBase = computed(() => {
  const map: Record<string, string> = {
    SEARCH_LIST: '查询列表',
    CRUD_LIST: '数据管理',
    DASHBOARD: '业务看板',
    PROFILE_PAGE: '资料详情',
    DETAIL_PAGE: '详情页面',
    EDIT_FORM: '表单处理',
    SETTINGS_FORM: '配置表单',
    UPLOAD_FORM: '导入上传',
    WORKFLOW_STEP: '流程处理',
    GENERIC_PAGE: '业务页面',
  }

  return map[props.runtimeContext.pattern] || 'Business Page'
})

const businessEyebrow = computed(() => {
  const industryName = cleanDisplayText(props.runtimeContext.industryStyle?.name)
  if (!industryName || industryName === '通用企业系统') {
    return businessEyebrowBase.value
  }

  return `${industryName} · ${businessEyebrowBase.value}`
})

const firstComponentId = computed(() => {
  return props.canvasComponents[0]?.componentId || props.contract.page.pageId || 'page_root'
})

// A9.10.14.4.3.13.2.9_HEADER_ACTION_SCOPE_FIX
// Header actions must be explicit PAGE_HEADER actions only. Do not infer them
// from all interactions, otherwise query buttons, row edit actions and modal
// save buttons are incorrectly promoted to the page header.
const headerActions = computed(() => {
  const headerActionIds = allComponents.value
    .filter((component) => String(component.type || '').toUpperCase() === 'PAGE_HEADER')
    .flatMap((component) => Array.isArray(component.actionIds) ? component.actionIds : [])
    .map((actionId) => String(actionId || '').trim())
    .filter(Boolean)

  if (!headerActionIds.length) return []

  const allowed = new Set(headerActionIds)
  return props.interactions.filter((action) => allowed.has(String(action.actionId || '')))
})


function authComponentInlineStyle(): Record<string, string> {
  const component = authFormComponent.value as UiContractComponent & LooseRecord | null
  return normalizeRuntimeInlineStyle(component?.props?.style)
}

function authElementInlineStyle(
  key: string | null | undefined,
): Record<string, string> {
  const normalizedKey = String(key || '').trim()
  if (!normalizedKey) return {}

  const component = authFormComponent.value as UiContractComponent & LooseRecord | null
  const props = component?.props
  const elementStyles = props?.elementStyles

  if (!elementStyles || typeof elementStyles !== 'object' || Array.isArray(elementStyles)) {
    return {}
  }

  return normalizeRuntimeInlineStyle((elementStyles as LooseRecord)[normalizedKey])
}

function pageHeaderInlineStyle(
  role: string,
): Record<string, string> {
  const component = componentById(firstComponentId.value)
  const props = (component as UiContractComponent & LooseRecord | null)?.props
  const elementStyles = props?.elementStyles

  if (!elementStyles || typeof elementStyles !== 'object' || Array.isArray(elementStyles)) {
    return {}
  }

  const styleKey = role.includes(':') ? role : `text:${role}`
  return normalizeRuntimeInlineStyle((elementStyles as LooseRecord)[styleKey])
}

function authTextInlineStyle(
  role: string,
): Record<string, string> {
  return authElementInlineStyle(`text:${role}`)
}

function authFieldInlineStyle(
  field: LooseRecord,
  scope: 'field' | 'fieldLabel' | 'fieldControl',
): Record<string, string> {
  const key = fieldKey(field)
  if (!key) return {}

  const styleKey =
    scope === 'fieldLabel'
      ? `fieldLabel:${key}`
      : scope === 'fieldControl'
        ? `fieldControl:${key}`
        : `field:${key}`

  const style = authElementInlineStyle(styleKey)

  if (scope === 'field' && field.hidden === true) {
    style.display = 'none'
  }

  return style
}

function authFieldVisible(
  field: LooseRecord,
): boolean {
  return field.hidden !== true
}

function authActionInlineStyle(
  actionId?: unknown,
): Record<string, string> {
  const id = String(actionId || '').trim()
  if (!id) return {}

  const style = authElementInlineStyle(`action:${id}`)
  const action = actionById(id) as UiContractInteraction & LooseRecord | undefined

  if (action?.hidden === true) {
    style.display = 'none'
  }

  return style
}

function authActionHidden(
  actionId?: unknown,
): boolean {
  const action = actionById(String(actionId || '')) as UiContractInteraction & LooseRecord | undefined
  return action?.hidden === true
}

function componentById(
  componentId: string,
): UiContractComponent | null {
  if (!componentId) return null
  return props.componentMap[componentId] || null
}

function normalizeRuntimeInlineStyle(
  value: unknown,
): Record<string, string> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return {}
  }

  const source = value as LooseRecord
  const allowedKeys = [
    'backgroundColor',
    'color',
    'borderColor',
    'transform',
    'marginLeft',
    'marginRight',
    'marginTop',
    'marginBottom',
    'padding',
    'minWidth',
    'maxWidth',
    'width',
    'height',
    'minHeight',
    'fontSize',
    'fontWeight',
    'borderRadius',
    'opacity',
    'justifySelf',
    'alignSelf',
    'justifyContent',
    'alignItems',
    'placeSelf',
    'order',
    'gridColumn',
    'gridRow',
    'flex',
    'display',
    'gap',
    'paddingInline',
    'paddingBlock',
    'whiteSpace',
    'display',
    'boxShadow',
  ]
  const result: Record<string, string> = {}

  for (const key of allowedKeys) {
    const raw = source[key]
    if (typeof raw === 'string' && raw.trim()) {
      result[key] = raw.trim()
    }
  }

  if (source.hidden === true) {
    result.display = 'none'
  }

  return result
}

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


// A9.10.14.4.3.15.7.24L3A1_ANT_DESIGN_VUE_RUNTIME_BASELINE
// Ant Design Vue becomes the default admin-page renderer.
// Existing contracts can still force Element Plus through designSystem/uiFramework/renderEngine.
const shouldUseAntDesignRenderer = computed(() => {
  const source = props.contract as any
  const page = source?.page || {}
  const layout = page?.layout || {}

  const raw = [
    source?.designSystem,
    source?.uiFramework,
    source?.renderEngine,
    source?.renderer,
    source?.runtime,
    page?.designSystem,
    page?.uiFramework,
    page?.renderEngine,
    page?.renderer,
    layout?.designSystem,
    layout?.uiFramework,
    layout?.renderer,
  ].map((item) => String(item || '').toUpperCase()).join(' ')

  // A9.10.14.4.3.15.7.24L3A3_ANTD_DEFAULT_SEMANTIC_CLEAN_CONTRACT
  // New generated prototypes default to Ant Design Vue.
  // Only explicit Element Plus contracts opt out.
  return !(
    raw.includes('ELEMENT_PLUS') ||
    raw.includes('ELEMENTPLUS') ||
    raw.includes('ELEMENT PLUS')
  )
})

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

/* Phase 10-A4.11: demo-grade business page visual polish */
.business-page {
  min-height: 100%;
  overflow: hidden;
  border-radius: 28px;
  background:
    radial-gradient(circle at 18% 0%, rgba(37, 99, 235, 0.13), transparent 28%),
    radial-gradient(circle at 88% 6%, rgba(14, 165, 233, 0.15), transparent 30%),
    linear-gradient(135deg, #f8fbff 0%, #eef4ff 48%, #f8fafc 100%);
}

.business-header {
  padding: 42px 54px 18px;
}

.business-header h1 {
  margin: 6px 0 8px;
  letter-spacing: -0.04em;
  font-size: clamp(32px, 4vw, 48px);
  line-height: 1.04;
}

.business-header span {
  max-width: 760px;
  color: #475569;
  font-size: 15px;
  line-height: 1.8;
}

.header-actions button {
  min-height: 38px;
  border-radius: 999px;
  padding: 9px 16px;
  box-shadow: 0 10px 26px rgba(37, 99, 235, 0.12);
}

.business-body {
  align-items: stretch;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
  padding: 20px 54px 54px;
}

.business-page--dashboard .business-body {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.business-page :deep(.runtime-node),
.business-page :deep(.node-card),
.business-page :deep(.component-card),
.business-page :deep(.alert-node),
.business-page :deep(.empty-state) {
  min-height: 164px;
  border: 1px solid rgba(203, 213, 225, 0.72);
  border-radius: 24px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.96));
  box-shadow: 0 20px 52px rgba(15, 23, 42, 0.09);
}

.business-page :deep(.runtime-node:hover),
.business-page :deep(.node-card:hover),
.business-page :deep(.component-card:hover),
.business-page :deep(.alert-node:hover) {
  transform: translateY(-2px);
  box-shadow: 0 28px 70px rgba(15, 23, 42, 0.13);
}

.business-page :deep(.node-header h3) {
  font-size: 20px;
  letter-spacing: -0.02em;
}

.business-page :deep(.node-header p),
.business-page :deep(.component-description),
.business-page :deep(.node-description) {
  display: block !important;
  color: #64748b;
  line-height: 1.7;
}

.business-page :deep(.metric-grid) {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.business-page :deep(.metric-grid article) {
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 16px;
  padding: 13px;
  background: #ffffff;
}

.business-page :deep(.metric-grid span) {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.business-page :deep(.metric-grid strong) {
  display: block;
  margin-top: 6px;
  color: #0f172a;
  font-size: 18px;
}

.business-page :deep(.children-grid) {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.business-page :deep(.action-row) {
  margin-top: 16px;
}

.business-page :deep(.action-row button),
.business-page :deep(button) {
  border-radius: 999px;
  font-weight: 850;
}

@media (max-width: 1180px) {
  .business-body,
  .business-page--dashboard .business-body,
  .business-page :deep(.children-grid) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .business-header {
    padding: 30px 24px 14px;
  }

  .business-body,
  .business-page--dashboard .business-body,
  .business-page :deep(.children-grid) {
    grid-template-columns: 1fr;
    padding: 16px 24px 34px;
  }
}

/* A9_7_PREMIUM_PROTOTYPE_VISUALS: improve generated prototype fidelity without adding extra controls. */
.pattern-runtime :deep(input),
.pattern-runtime :deep(select),
.pattern-runtime :deep(textarea) {
  min-height: 44px;
  border: 1px solid rgba(148, 163, 184, 0.42);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.pattern-runtime :deep(input:focus),
.pattern-runtime :deep(select:focus),
.pattern-runtime :deep(textarea:focus) {
  border-color: var(--runtime-primary);
  box-shadow:
    0 0 0 4px color-mix(in srgb, var(--runtime-primary) 13%, transparent),
    0 12px 28px rgba(15, 23, 42, 0.08);
}

.pattern-runtime :deep(button) {
  min-height: 38px;
  letter-spacing: 0.01em;
  box-shadow: 0 10px 26px rgba(37, 99, 235, 0.10);
  transition: transform 0.16s ease, box-shadow 0.16s ease, background 0.16s ease;
}

.pattern-runtime :deep(button:hover) {
  transform: translateY(-1px);
  box-shadow: 0 16px 36px rgba(37, 99, 235, 0.16);
}

.business-page :deep(table) {
  overflow: hidden;
  border: 1px solid rgba(226, 232, 240, 0.88);
  border-radius: 18px;
  background: #ffffff;
  box-shadow: 0 14px 36px rgba(15, 23, 42, 0.06);
}

.business-page :deep(th) {
  background: linear-gradient(180deg, #f8fafc 0%, #eef4ff 100%);
  color: #334155;
  font-weight: 900;
}

.business-page :deep(td) {
  background: rgba(255, 255, 255, 0.92);
}

.business-page :deep(.form-field),
.business-page :deep(.auth-field) {
  min-width: 0;
}

/* A9.9.1 Enterprise Admin Runtime visual overrides
   Keep the current UI_CONTRACT schema and element picker intact, but make
   business pages look like real admin systems instead of landing-page cards. */
.business-page {
  background:
    linear-gradient(180deg, #f6f8fc 0%, #f8fafc 100%) !important;
  padding: 22px 28px 72px !important;
}

.business-page .business-body {
  max-width: 1180px;
  margin: 0 auto;
  gap: 16px;
}

.business-page--form .business-body,
.business-page--detail .business-body,
.business-page--workflow .business-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
}

.business-page :deep(.runtime-node),
.business-page :deep(.node-card),
.business-page :deep(.component-card),
.business-page :deep(.node-form),
.business-page :deep(.component-form) {
  border: 1px solid #e5e7eb !important;
  border-radius: 10px !important;
  background: #ffffff !important;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.05) !important;
}

.business-page :deep(.node-card),
.business-page :deep(.component-card),
.business-page :deep(.node-form),
.business-page :deep(.component-form) {
  padding: 18px 20px !important;
}

.business-page :deep(.page-header-node),
.business-page :deep(.toolbar-node),
.business-page :deep(.node-header) {
  border-bottom: 1px solid #eef2f7;
  padding-bottom: 12px;
  margin-bottom: 16px;
}

.business-page :deep(.form-grid) {
  grid-template-columns: repeat(3, minmax(0, 1fr)) !important;
  gap: 16px 20px !important;
}

.business-page :deep(.form-field > span) {
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

.business-page :deep(input),
.business-page :deep(textarea),
.business-page :deep(select) {
  border-radius: 6px !important;
  border-color: #d7dde8 !important;
  background: #ffffff !important;
  min-height: 34px;
  font-size: 13px;
}

.business-page :deep(input:focus),
.business-page :deep(textarea:focus),
.business-page :deep(select:focus) {
  border-color: #2563eb !important;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.12);
}

.business-page :deep(.table-wrap) {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: auto;
}

.business-page :deep(table) {
  background: #ffffff;
}

.business-page :deep(th) {
  background: #f8fafc !important;
  color: #475569 !important;
  font-weight: 700;
}

.business-page :deep(td),
.business-page :deep(th) {
  border-bottom-color: #e5e7eb !important;
  padding: 10px 12px !important;
  font-size: 13px;
}

.business-page :deep(.action-row),
.business-page :deep(.toolbar-actions) {
  justify-content: flex-end;
  gap: 8px;
}

.business-page :deep(button) {
  border-radius: 6px !important;
  min-height: 32px;
  padding: 6px 13px !important;
  font-size: 13px;
}

@media (max-width: 960px) {
  .business-page :deep(.form-grid) {
    grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
  }
}

@media (max-width: 680px) {
  .business-page {
    padding: 16px !important;
  }

  .business-page :deep(.form-grid) {
    grid-template-columns: 1fr !important;
  }
}

/* A9.9.2 Enterprise Admin Page Density: keep business pages full-width inside the real preview canvas. */
.business-page {
  width: 100%;
  max-width: none;
  min-height: 820px;
  padding: 0;
  background: #f3f6fb;
  box-sizing: border-box;
}

.business-hero,
.business-body,
.business-page > * {
  width: 100%;
  max-width: none;
  box-sizing: border-box;
}

.business-page--form .business-body,
.business-page--detail .business-body,
.business-page--workflow .business-body,
.business-page--dashboard .business-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.business-page :deep(.runtime-component),
.business-page :deep(.runtime-node),
.business-page :deep(.component-card),
.business-page :deep(.node-card),
.business-page :deep(.component-form),
.business-page :deep(.node-form),
.business-page :deep(.component-table),
.business-page :deep(.node-table),
.business-page :deep(.component-section),
.business-page :deep(.node-section) {
  width: 100%;
  max-width: none;
  box-sizing: border-box;
}

.business-page :deep(.component-card),
.business-page :deep(.node-card),
.business-page :deep(.component-section),
.business-page :deep(.node-section) {
  border-radius: 10px;
  border: 1px solid #dfe7f3;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.business-page :deep(.component-form),
.business-page :deep(.node-form) {
  padding: 18px;
  border-radius: 8px;
}

.business-page :deep(.form-grid) {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px 24px;
  align-items: start;
}

.business-page :deep(.form-field),
.business-page :deep(.field-item),
.business-page :deep(.runtime-field) {
  min-width: 0;
}

.business-page :deep(input),
.business-page :deep(select),
.business-page :deep(textarea) {
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
}

.business-page :deep(.component-table),
.business-page :deep(.node-table) {
  overflow-x: auto;
}

.business-page :deep(table) {
  width: 100%;
  min-width: 760px;
  border-collapse: separate;
  border-spacing: 0;
}

.business-page :deep(.component-actions),
.business-page :deep(.node-actions),
.business-page :deep(.actions-row) {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  width: 100%;
  max-width: none;
}

/* A9.9.3 Enterprise Form Runtime: keep create/edit pages full-width like a real admin system. */
.business-page--form :deep(.node-section > .children-grid),
.business-page--generic :deep(.node-section > .children-grid) {
  display: grid !important;
  grid-template-columns: minmax(0, 1fr) !important;
  gap: 16px !important;
}

.business-page--form :deep(.node-section),
.business-page--generic :deep(.node-section) {
  min-height: auto !important;
  padding: 20px !important;
}

.business-page--form :deep(.node-section > .node-header),
.business-page--generic :deep(.node-section > .node-header) {
  margin-bottom: 14px !important;
}

.business-page--form :deep(.node-form),
.business-page--generic :deep(.node-form),
.business-page--form :deep(.node-table),
.business-page--generic :deep(.node-table) {
  min-height: auto !important;
}

.business-page--form :deep(.node-toolbar),
.business-page--generic :deep(.node-toolbar) {
  min-height: auto !important;
  padding: 12px 16px !important;
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
}

.business-page--form :deep(.toolbar-node),
.business-page--generic :deep(.toolbar-node) {
  justify-content: flex-end !important;
  margin-bottom: 0 !important;
  padding-bottom: 0 !important;
  border-bottom: 0 !important;
}

.business-page--form :deep(.node-alert),
.business-page--generic :deep(.node-alert) {
  min-height: auto !important;
}

.business-page--form :deep(.form-grid),
.business-page--generic :deep(.form-grid) {
  grid-template-columns: repeat(4, minmax(0, 1fr)) !important;
}

.business-page--form :deep(.table-wrap),
.business-page--generic :deep(.table-wrap) {
  width: 100%;
}

.business-page--form :deep(table),
.business-page--generic :deep(table) {
  min-width: 980px;
}


/* A9.9.8 Page Pattern Runtime Layout Gate
   Generic rendering constraints for canonical PAGE patterns.
   LIST_PAGE/TABLE_LIST must never fall back to the premium card-gallery grid. */
.business-page--list .business-body {
  grid-template-columns: 1fr !important;
  max-width: none !important;
  width: 100% !important;
  align-items: stretch !important;
  gap: 18px !important;
}

.business-page--list :deep(.runtime-node),
.business-page--list :deep(.node-card),
.business-page--list :deep(.component-card),
.business-page--list :deep(.node-form),
.business-page--list :deep(.component-form),
.business-page--list :deep(.empty-state),
.business-page--list :deep(.alert-node) {
  width: 100% !important;
  max-width: none !important;
  min-height: 0 !important;
  box-sizing: border-box !important;
}

.business-page--list :deep(.children-grid) {
  grid-template-columns: 1fr !important;
  gap: 14px !important;
  width: 100% !important;
}

.business-page--list :deep(.table-wrapper),
.business-page--list :deep(.table-scroll),
.business-page--list :deep(.runtime-table),
.business-page--list :deep(table) {
  width: 100% !important;
  max-width: none !important;
}

.business-page--list :deep(table) {
  min-width: 100% !important;
}

.business-page--list :deep(.node-card:has(table)),
.business-page--list :deep(.component-card:has(table)) {
  overflow: hidden !important;
}

.business-page--list :deep(.action-row),
.business-page--list :deep(.toolbar-actions),
.business-page--list :deep(.node-actions) {
  justify-content: flex-end !important;
  flex-wrap: wrap !important;
}

.business-page--form .business-body,
.business-page--detail .business-body,
.business-page--workflow .business-body {
  grid-template-columns: 1fr !important;
  max-width: 1120px !important;
  width: 100% !important;
}

.business-page--form :deep(.children-grid),
.business-page--detail :deep(.children-grid),
.business-page--workflow :deep(.children-grid) {
  grid-template-columns: 1fr !important;
}



/* A9.10.14.4.3.13.2.11_COMPACT_LIST_LAYOUT */
.business-page--list .business-body {
  gap: 14px;
  padding: 14px 38px 42px;
}

.business-page--list :deep(.runtime-node),
.business-page--list :deep(.node-card),
.business-page--list :deep(.component-card),
.business-page--list :deep(.node-form),
.business-page--list :deep(.component-form) {
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.06);
}

.business-page--list :deep(.node-header) {
  margin-bottom: 10px;
}

.business-page--list :deep(.node-header h3) {
  font-size: 19px;
}


/* A9.10.14.4.3.15.4.3_RUNTIME_INDUSTRY_STYLE_RENDER */
.pattern-runtime {
  --a9-business-header-padding-x: 56px;
  --a9-business-body-padding-x: 56px;
}

.business-page {
  background: var(--runtime-page-gradient);
}

.business-body {
  gap: var(--runtime-grid-gap);
}

.business-page :deep(.runtime-node),
.business-page :deep(.node-card),
.business-page :deep(.component-card),
.business-page :deep(.node-form),
.business-page :deep(.component-form) {
  border-color: var(--runtime-border);
  border-radius: var(--runtime-radius);
  background: var(--runtime-surface);
  box-shadow: var(--runtime-card-shadow);
}

.business-page :deep(input),
.business-page :deep(textarea),
.business-page :deep(select) {
  min-height: var(--runtime-control-height);
  border-radius: var(--runtime-radius-sm);
}

.business-page :deep(button),
.header-actions button,
.auth-primary-button {
  border-radius: var(--runtime-button-radius);
}

.business-page :deep(th),
.business-page :deep(td) {
  padding: var(--runtime-table-cell-padding-y) var(--runtime-table-cell-padding-x);
}

.industry-warehouse .business-page,
.industry-manufacturing .business-page {
  background:
    linear-gradient(90deg, rgba(15, 23, 42, 0.035) 1px, transparent 1px),
    linear-gradient(180deg, rgba(15, 23, 42, 0.035) 1px, transparent 1px),
    var(--runtime-page-gradient);
  background-size: 28px 28px, 28px 28px, auto;
}

.industry-warehouse .business-body,
.industry-manufacturing .business-body,
.density-compact .business-body {
  gap: 12px;
  padding-top: 12px;
}

.industry-warehouse :deep(.form-grid),
.industry-manufacturing :deep(.form-grid),
.density-compact :deep(.form-grid) {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px 14px;
}

.industry-warehouse :deep(.runtime-node),
.industry-manufacturing :deep(.runtime-node),
.density-compact :deep(.runtime-node) {
  padding: var(--runtime-card-padding);
}

.industry-commerce .business-header {
  border-bottom: 0;
}

.industry-commerce .business-page {
  background:
    radial-gradient(circle at 12% 10%, rgba(249, 115, 22, 0.16), transparent 30%),
    radial-gradient(circle at 88% 12%, rgba(14, 165, 233, 0.12), transparent 28%),
    var(--runtime-page-gradient);
}

.industry-commerce .business-page :deep(.runtime-node) {
  border-color: rgba(253, 186, 116, 0.42);
}

.industry-work-order .business-page,
.industry-foreign-trade .business-page,
.industry-crm .business-page {
  background:
    radial-gradient(circle at 14% 12%, color-mix(in srgb, var(--runtime-primary) 12%, transparent), transparent 30%),
    var(--runtime-page-gradient);
}

.industry-finance .business-page,
.industry-gov .business-page {
  background: linear-gradient(135deg, #f8fafc 0%, var(--runtime-page-bg) 100%);
}

.industry-finance .business-page :deep(.runtime-node),
.industry-gov .business-page :deep(.runtime-node) {
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.065);
}

.auth-card {
  border-radius: calc(var(--runtime-radius) + 8px);
}

/* A9.10.14.4.3.15.4.11_VISUAL_QUALITY_SYSTEM */
.pattern-runtime {
  --a9-pattern-section-gap: var(--runtime-grid-gap);
}

.business-page {
  border-radius: calc(var(--runtime-radius) + 10px);
}

.business-page--search-list .business-body,
.business-page--table-list .business-body {
  grid-template-columns: minmax(0, 1fr);
}

.business-page--form-page .business-body,
.business-page--form-detail .business-body,
.business-page--edit-form .business-body {
  grid-template-columns: minmax(0, 1fr);
  max-width: 1480px;
}

.business-page--detail-view .business-body,
.business-page--detail-tabs .business-body {
  grid-template-columns: minmax(360px, .82fr) minmax(0, 1.18fr);
}

.business-page--dashboard .business-body,
.business-page--home-page .business-body {
  grid-template-columns: repeat(12, minmax(0, 1fr));
}

.business-page--workflow .business-body,
.business-page--approval-workbench .business-body {
  grid-template-columns: minmax(320px, .72fr) minmax(0, 1.28fr);
}

.business-header {
  padding: clamp(22px, 3vw, 34px);
}

.business-header > div:first-child {
  max-width: 880px;
}

.business-header-actions button,
.auth-primary-button {
  box-shadow: 0 12px 28px color-mix(in srgb, var(--runtime-primary) 20%, transparent);
}

.pattern-form_page .business-page :deep(.form-grid),
.pattern-form_detail .business-page :deep(.form-grid),
.pattern-edit_form .business-page :deep(.form-grid) {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.pattern-detail_view .business-page :deep(.description-list),
.pattern-detail_tabs .business-page :deep(.description-list) {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.auth-card {
  box-shadow: 0 28px 90px rgba(15, 23, 42, 0.16);
}

.industry-warehouse .business-header,
.industry-manufacturing .business-header {
  background:
    linear-gradient(90deg, rgba(15, 23, 42, 0.035) 1px, transparent 1px),
    linear-gradient(180deg, rgba(15, 23, 42, 0.035) 1px, transparent 1px),
    linear-gradient(135deg, var(--runtime-surface) 0%, color-mix(in srgb, var(--runtime-primary) 7%, var(--runtime-page-bg)) 100%);
  background-size: 26px 26px, 26px 26px, auto;
}

.industry-finance .business-header,
.industry-gov .business-header {
  background: linear-gradient(135deg, #ffffff 0%, #f8fafc 100%);
}

.industry-commerce .business-header {
  background:
    radial-gradient(circle at 12% 8%, rgba(249, 115, 22, 0.16), transparent 30%),
    linear-gradient(135deg, #ffffff 0%, #fff7ed 100%);
}


/* A9.10.14.4.3.15.6.24D2_RUNTIME_VISUAL_RATIO */
.business-body {
  width: min(100%, 1240px) !important;
  max-width: 1240px !important;
  margin: 0 auto !important;
  padding: 20px 24px 40px !important;
  box-sizing: border-box !important;
}

.business-header,
.business-hero,
.business-page-header,
.business-page-hero {
  min-height: 108px !important;
  padding: 24px 32px !important;
  border-radius: 18px !important;
  box-sizing: border-box !important;
}

.business-header h1,
.business-hero h1,
.business-page-header h1,
.business-page-hero h1,
.business-title,
.business-page-title {
  font-size: 34px !important;
  line-height: 1.16 !important;
  letter-spacing: -0.04em !important;
  margin: 0 0 8px !important;
}

.business-header p,
.business-hero p,
.business-page-header p,
.business-page-hero p,
.business-subtitle,
.business-page-subtitle {
  font-size: 13px !important;
  line-height: 1.6 !important;
  margin: 0 !important;
}

.business-body :deep(.ep-runtime-node + .ep-runtime-node) {
  margin-top: 18px !important;
}

.business-body :deep(.ep-container-panel) {
  margin: 0 !important;
  padding: 0 !important;
}

.business-body :deep(.ep-container-header) {
  padding-left: 0 !important;
  padding-right: 0 !important;
}

@media (max-width: 900px) {
  .business-body {
    padding: 16px !important;
  }

  .business-header,
  .business-hero,
  .business-page-header,
  .business-page-hero {
    padding: 20px !important;
  }

  .business-header h1,
  .business-hero h1,
  .business-page-header h1,
  .business-page-hero h1,
  .business-title,
  .business-page-title {
    font-size: 28px !important;
  }
}


/* A9.10.14.4.3.15.6.24D4_ADMIN_DENSITY_POPPER */
.business-body {
  width: min(100%, 1180px) !important;
  max-width: 1180px !important;
  padding: 18px 22px 36px !important;
  margin: 0 auto !important;
  box-sizing: border-box !important;
}

.business-header,
.business-hero,
.business-page-header,
.business-page-hero {
  min-height: 92px !important;
  padding: 20px 28px !important;
  border-radius: 14px !important;
}

.business-header h1,
.business-hero h1,
.business-page-header h1,
.business-page-hero h1,
.business-title,
.business-page-title {
  font-size: 26px !important;
  line-height: 1.18 !important;
  letter-spacing: -0.02em !important;
  margin: 0 0 6px !important;
}

.business-header p,
.business-hero p,
.business-page-header p,
.business-page-hero p,
.business-subtitle,
.business-page-subtitle {
  font-size: 12.5px !important;
  line-height: 1.55 !important;
}

.business-body :deep(.ep-runtime-node + .ep-runtime-node) {
  margin-top: 14px !important;
}


/* A9.10.14.4.3.15.6.24D5_ADMIN_ALIGNMENT_ACTIONBAR */
.business-body {
  width: min(100%, 1180px) !important;
  max-width: 1180px !important;
  padding-left: 0 !important;
  padding-right: 0 !important;
  margin-left: auto !important;
  margin-right: auto !important;
}

.business-header,
.business-hero,
.business-page-header,
.business-page-hero {
  width: min(100%, 1180px) !important;
  max-width: 1180px !important;
  margin-left: auto !important;
  margin-right: auto !important;
  box-sizing: border-box !important;
}

.business-body :deep(.ep-action-toolbar) {
  margin-bottom: 0 !important;
}

.business-body :deep(.ep-action-toolbar + .ep-runtime-node) {
  margin-top: 8px !important;
}


/* A9.10.14.4.3.15.6.24G_ADMIN_PAGE_RENDERER */
.business-page {
  background: #f4f7fb !important;
  padding: 20px 24px 40px !important;
  box-sizing: border-box !important;
}

.business-header {
  width: 100% !important;
  max-width: none !important;
  min-height: 104px !important;
  margin: 0 0 16px !important;
  padding: 20px 24px !important;
  box-sizing: border-box !important;
  border-radius: 14px !important;
}

.business-header h1 {
  font-size: 24px !important;
  line-height: 1.2 !important;
  margin: 0 0 6px !important;
}

.business-header span {
  font-size: 12.5px !important;
  line-height: 1.55 !important;
}

.business-body {
  width: 100% !important;
  max-width: none !important;
  padding: 0 !important;
  margin: 0 !important;
  display: block !important;
  box-sizing: border-box !important;
}

.business-body :deep(.a9-admin-page) {
  width: 100% !important;
  max-width: none !important;
  min-width: 0 !important;
}

.pattern-runtime :deep(.a9-admin-form .el-input__inner),
.pattern-runtime :deep(.a9-admin-form .el-select__input),
.pattern-runtime :deep(.a9-admin-form .el-range-input) {
  border: 0 !important;
  box-shadow: none !important;
  background: transparent !important;
}


/* A9.10.14.4.3.15.6.24G2_ADMIN_CENTER_WIDTH */
.business-page {
  padding: 20px 32px 40px !important;
  box-sizing: border-box !important;
}

.business-header,
.business-body {
  width: min(100%, 1180px) !important;
  max-width: 1180px !important;
  margin-left: auto !important;
  margin-right: auto !important;
  box-sizing: border-box !important;
}

.business-header {
  margin-bottom: 16px !important;
}

.business-body {
  display: block !important;
  padding: 0 !important;
}

.business-body :deep(.a9-admin-page),
.business-body :deep(.a9-admin-card),
.business-body :deep(.a9-admin-table) {
  width: 100% !important;
  max-width: 100% !important;
  box-sizing: border-box !important;
}

@media (max-width: 900px) {
  .business-page {
    padding: 16px !important;
  }

  .business-header,
  .business-body {
    width: 100% !important;
    max-width: none !important;
  }
}


/* A9.10.14.4.3.15.6.24G3_ADMIN_CANVAS_GUTTER
   Undo 24G2 centered max-width behavior.
   Use canvas gutters instead: header/body share the same left/right boundary. */
.business-page {
  padding: 20px 96px 40px 52px !important;
  box-sizing: border-box !important;
}

.business-header,
.business-body {
  width: 100% !important;
  max-width: none !important;
  margin-left: 0 !important;
  margin-right: 0 !important;
  box-sizing: border-box !important;
}

.business-header {
  min-height: 104px !important;
  margin-bottom: 16px !important;
  padding: 20px 24px !important;
}

.business-body {
  display: block !important;
  padding: 0 !important;
}

.business-body :deep(.a9-admin-page),
.business-body :deep(.a9-admin-card),
.business-body :deep(.a9-admin-table) {
  width: 100% !important;
  max-width: 100% !important;
  min-width: 0 !important;
  box-sizing: border-box !important;
}

.business-body :deep(.el-table) {
  width: 100% !important;
}

.business-body :deep(.el-table__inner-wrapper),
.business-body :deep(.el-table__body-wrapper),
.business-body :deep(.el-table__header-wrapper) {
  width: 100% !important;
}

@media (max-width: 900px) {
  .business-page {
    padding: 16px !important;
  }
}


/* A9.10.14.4.3.15.6.24G4_SELECTION_OVERFLOW_FIX
   Selection outline / selected badge must not enlarge the admin page scroll width. */
.pattern-runtime,
.business-page,
.business-body {
  overflow-x: hidden !important;
}

.business-body :deep(.a9-admin-page),
.business-body :deep(.a9-admin-card),
.business-body :deep(.a9-admin-table-card),
.business-body :deep(.el-table),
.business-body :deep(.el-table__inner-wrapper),
.business-body :deep(.el-table__body-wrapper),
.business-body :deep(.el-table__header-wrapper) {
  max-width: 100% !important;
  min-width: 0 !important;
  box-sizing: border-box !important;
}

.pattern-runtime :deep([data-ui-selectable]) {
  box-sizing: border-box !important;
  min-width: 0 !important;
}

.pattern-runtime :deep([data-ui-selectable][data-ui-selected='true']),
.pattern-runtime :deep([data-ui-selectable][data-selected='true']),
.pattern-runtime :deep([data-ui-selectable].is-selected),
.pattern-runtime :deep([data-ui-selectable].is-prototype-selected),
.pattern-runtime :deep([data-ui-selectable].prototype-selected),
.pattern-runtime :deep([data-ui-selectable].a9-selected) {
  outline: 2px solid #3b82f6 !important;
  outline-offset: -2px !important;
  border-color: transparent !important;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.18) !important;
}

.pattern-runtime :deep(.selection-label),
.pattern-runtime :deep(.selected-label),
.pattern-runtime :deep(.prototype-selection-label),
.pattern-runtime :deep(.prototype-selected-label),
.pattern-runtime :deep(.ui-selection-label),
.pattern-runtime :deep(.a9-selection-label) {
  max-width: min(240px, calc(100vw - 32px)) !important;
  white-space: nowrap !important;
  overflow: hidden !important;
  text-overflow: ellipsis !important;
  pointer-events: none !important;
}

</style>