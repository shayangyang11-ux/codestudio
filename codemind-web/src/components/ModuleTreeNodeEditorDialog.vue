<template>
  <div
    v-if="visible"
    class="dialog-mask"
  >
    <section class="dialog-card">
      <header class="dialog-header">
        <div>
          <h3>{{ title }}</h3>
          <p>{{ description }}</p>
        </div>

        <button
          class="icon-button"
          @click="$emit('cancel')"
        >
          ×
        </button>
      </header>

      <div class="dialog-body">
        <label class="form-item">
          <span>节点名称</span>
          <input
            v-model="form.moduleName"
            type="text"
            placeholder="请输入节点名称"
          />
        </label>

        <label class="form-item">
          <span>节点类型</span>
          <select
            v-model="form.moduleType"
            :disabled="mode === 'edit' && !allowChangeType"
          >
            <option
              v-for="option in typeOptions"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </option>
          </select>
        </label>

        <label class="form-item">
          <span>节点 Key</span>
          <input
            v-model="form.moduleKey"
            type="text"
            placeholder="可不填，系统自动生成"
          />
        </label>

        <template v-if="form.moduleType === 'PAGE'">
          <label class="form-item">
            <span>页面类型</span>
            <select v-model="form.pageKind">
              <option value="LIST_PAGE">列表页</option>
              <option value="FORM_PAGE">表单页 / 创建编辑页</option>
              <option value="DETAIL_PAGE">详情页</option>
              <option value="APPROVAL_PAGE">审批页</option>
              <option value="DASHBOARD_PAGE">看板页</option>
              <option value="SETTINGS_PAGE">设置页</option>
            </select>
          </label>

          <label class="form-item">
            <span>页面路由</span>
            <input
              v-model="form.routePath"
              type="text"
              placeholder="例如 /user/login"
            />
          </label>

          <label class="form-item">
            <span>布局类型</span>
            <select v-model="form.layoutType">
              <option value="table-list">表格列表</option>
              <option value="form-detail">表单详情</option>
              <option value="detail-tabs">详情 Tabs</option>
              <option value="approval-workbench">审批工作台</option>
              <option value="dashboard">数据看板</option>
            </select>
          </label>

          <label class="form-item">
            <span>主业务对象</span>
            <input
              v-model="form.primaryEntity"
              type="text"
              placeholder="例如 User、Order、ProcessingTask"
            />
          </label>
        </template>

        <template v-if="form.moduleType === 'FEATURE'">
          <label class="form-item">
            <span>功能点角色</span>
            <select v-model="form.featureRole">
              <option value="query">查询</option>
              <option value="create">新增</option>
              <option value="update">修改</option>
              <option value="delete">删除</option>
              <option value="submit">提交</option>
              <option value="approve">审批通过</option>
              <option value="reject">驳回</option>
              <option value="export">导出</option>
              <option value="import">导入</option>
              <option value="navigate">跳转</option>
              <option value="validate">校验</option>
              <option value="custom">自定义</option>
            </select>
          </label>
        </template>
      </div>

      <footer class="dialog-footer">
        <button
          class="secondary-button"
          @click="$emit('cancel')"
        >
          取消
        </button>

        <button
          class="primary-button"
          @click="handleSubmit"
        >
          保存
        </button>
      </footer>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import type { ModuleTreeNode } from '../types/codemind'
import {
  getRequirementNodeTypeLabel,
  resolveRequirementNodeType,
  type RequirementNodeType,
} from '../domain/requirementNodePolicy'

type DialogMode = 'create-root' | 'create-child' | 'edit'

interface NodeEditorForm {
  moduleName: string
  moduleKey: string
  moduleType: RequirementNodeType
  pageKind: string
  routePath: string
  layoutType: string
  primaryEntity: string
  featureRole: string
}

const props = defineProps<{
  visible: boolean
  mode: DialogMode
  parentNode?: ModuleTreeNode | null
  editingNode?: ModuleTreeNode | null
  allowChangeType?: boolean
}>()

const emit = defineEmits<{
  cancel: []
  submit: [payload: {
    moduleName: string
    moduleKey?: string
    moduleType: string
    extra: Record<string, unknown>
  }]
}>()

const form = reactive<NodeEditorForm>({
  moduleName: '',
  moduleKey: '',
  moduleType: 'MODULE',
  pageKind: 'FORM_PAGE',
  routePath: '',
  layoutType: 'form-detail',
  primaryEntity: '',
  featureRole: 'custom',
})

const title = computed(() => {
  if (props.mode === 'create-root') return '新增根节点'
  if (props.mode === 'create-child') return '新增子节点'
  return '编辑节点'
})

const description = computed(() => {
  if (props.mode === 'create-root') {
    return '创建业务域或模块根节点。'
  }

  if (props.mode === 'create-child' && props.parentNode) {
    return `在「${props.parentNode.moduleName || props.parentNode.name || '当前节点'}」下创建子节点。`
  }

  return '修改当前模块树节点信息。'
})

const typeOptions = computed(() => {
  if (props.mode === 'edit' && props.editingNode) {
    const nodeType = resolveRequirementNodeType(props.editingNode)
    return [
      {
        label: getRequirementNodeTypeLabel(nodeType),
        value: nodeType,
      },
    ]
  }

  if (props.mode === 'create-root') {
    return [
      {
        label: '业务域',
        value: 'DOMAIN',
      },
      {
        label: '模块',
        value: 'MODULE',
      },
    ]
  }

  const parentType = resolveRequirementNodeType(props.parentNode)

  if (parentType === 'DOMAIN') {
    return [
      {
        label: '模块',
        value: 'MODULE',
      },
    ]
  }

  if (parentType === 'MODULE') {
    return [
      {
        label: '子模块',
        value: 'SUBMODULE',
      },
      {
        label: '页面',
        value: 'PAGE',
      },
    ]
  }

  if (parentType === 'SUBMODULE') {
    return [
      {
        label: '页面',
        value: 'PAGE',
      },
    ]
  }

  if (parentType === 'PAGE') {
    return [
      {
        label: '功能点',
        value: 'FEATURE',
      },
    ]
  }

  return []
})

watch(
  () => [
    props.visible,
    props.mode,
    props.parentNode?.id,
    props.editingNode?.id,
  ],
  () => {
    if (!props.visible) {
      return
    }

    if (props.mode === 'edit' && props.editingNode) {
      const node = props.editingNode
      const extra = node.extra || {}
      const nodeType = resolveRequirementNodeType(node)

      form.moduleName =
        node.moduleName ||
        node.name ||
        node.title ||
        ''
      form.moduleKey = node.moduleKey || node.key || ''
      form.moduleType = nodeType
      form.pageKind =
        node.pageKind ||
        String(extra.pageKind || extra.page_kind || 'FORM_PAGE')
      form.routePath =
        node.routePath ||
        String(extra.routePath || extra.route_path || '')
      form.layoutType =
        node.layoutType ||
        String(extra.layoutType || extra.layout_type || 'form-detail')
      form.primaryEntity =
        node.primaryEntity ||
        String(extra.primaryEntity || extra.primary_entity || '')
      form.featureRole =
        node.featureRole ||
        String(extra.featureRole || extra.feature_role || 'custom')

      return
    }

    const defaultType = resolveDefaultCreateType()

    form.moduleName = defaultNameForType(defaultType)
    form.moduleKey = ''
    form.moduleType = defaultType
    form.pageKind = defaultPageKind(defaultType)
    form.routePath = ''
    form.layoutType = defaultLayoutType(form.pageKind)
    form.primaryEntity = ''
    form.featureRole = defaultType === 'FEATURE' ? 'custom' : ''
  },
  { immediate: true },
)

watch(
  () => form.pageKind,
  () => {
    form.layoutType = defaultLayoutType(form.pageKind)
  },
)

function resolveDefaultCreateType(): RequirementNodeType {
  const options = typeOptions.value

  if (options.length > 0) {
    return options[0].value as RequirementNodeType
  }

  return 'MODULE'
}

function handleSubmit() {
  if (!form.moduleName.trim()) {
    window.alert('节点名称不能为空。')
    return
  }

  if (!form.moduleType || form.moduleType === 'UNKNOWN') {
    window.alert('请选择节点类型。')
    return
  }

  const extra: Record<string, unknown> = {
    moduleType: form.moduleType,
    type: form.moduleType,
    canGenerateUiContract: form.moduleType === 'PAGE',
  }

  if (form.moduleType === 'PAGE') {
    extra.pageKind = form.pageKind
    extra.routePath = form.routePath.trim()
    extra.layoutType = form.layoutType
    extra.primaryEntity = form.primaryEntity.trim()
  }

  if (form.moduleType === 'FEATURE') {
    extra.featureRole = form.featureRole
  }

  emit('submit', {
    moduleName: form.moduleName.trim(),
    moduleKey: form.moduleKey.trim() || undefined,
    moduleType: form.moduleType,
    extra,
  })
}

function defaultNameForType(moduleType: RequirementNodeType): string {
  if (moduleType === 'DOMAIN') return '新业务域'
  if (moduleType === 'MODULE') return '新模块'
  if (moduleType === 'SUBMODULE') return '新子模块'
  if (moduleType === 'PAGE') return '新页面'
  if (moduleType === 'FEATURE') return '新功能点'

  return '新节点'
}

function defaultPageKind(moduleType: RequirementNodeType): string {
  if (moduleType !== 'PAGE') return 'FORM_PAGE'
  return 'FORM_PAGE'
}

function defaultLayoutType(pageKind: string): string {
  if (pageKind === 'LIST_PAGE') return 'table-list'
  if (pageKind === 'DETAIL_PAGE') return 'detail-tabs'
  if (pageKind === 'APPROVAL_PAGE') return 'approval-workbench'
  if (pageKind === 'DASHBOARD_PAGE') return 'dashboard'
  return 'form-detail'
}
</script>

<style scoped>
.dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgb(15 23 42 / 38%);
}

.dialog-card {
  width: min(520px, calc(100vw - 40px));
  border-radius: 18px;
  background: #ffffff;
  box-shadow: 0 24px 70px rgb(15 23 42 / 28%);
  overflow: hidden;
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
}

.dialog-header h3 {
  margin: 0;
  font-size: 18px;
  color: #0f172a;
}

.dialog-header p {
  margin: 6px 0 0;
  font-size: 13px;
  color: #64748b;
}

.icon-button {
  width: 32px;
  height: 32px;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  background: #ffffff;
  color: #334155;
  cursor: pointer;
}

.dialog-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px 20px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-item span {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.form-item input,
.form-item select {
  height: 36px;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  padding: 0 10px;
  font-size: 14px;
  color: #0f172a;
  background: #ffffff;
}

.form-item input:focus,
.form-item select:focus {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgb(37 99 235 / 12%);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 20px 18px;
  border-top: 1px solid #e2e8f0;
}

.primary-button,
.secondary-button {
  border-radius: 10px;
  padding: 8px 14px;
  font-size: 14px;
  cursor: pointer;
}

.primary-button {
  border: 1px solid #2563eb;
  background: #2563eb;
  color: #ffffff;
}

.primary-button:hover {
  background: #1d4ed8;
}

.secondary-button {
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #334155;
}

.secondary-button:hover {
  border-color: #2563eb;
  color: #2563eb;
}
</style>