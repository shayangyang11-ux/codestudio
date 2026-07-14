<template>
    <div class="ui-contract-shell">
        <aside class="page-list">
            <h3>页面</h3>

            <button v-for="page in pages" :key="page.pageId" :class="{ active: currentPageId === page.pageId }"
                @click="currentPageId = page.pageId">
                <strong>{{ page.pageName || page.title || page.pageId }}</strong>
                <span>{{ routeOf(page.pageId) }}</span>
            </button>
        </aside>

        <main class="mock-screen">
            <div class="screen-toolbar">
                <div>
                    <h3>{{ currentPage?.pageName || currentPage?.title || currentPage?.pageId }}</h3>
                    <p>{{ currentPage?.description || '当前页面由 UI_CONTRACT 渲染，数据来自 Mock Runtime。' }}</p>
                </div>

                <button class="secondary-button" @click="resetRuntime">
                    重置 Mock 数据
                </button>
            </div>

            <section class="page-canvas">
                <div v-for="component in currentPage?.components || []" :key="component.componentId"
                    class="contract-card">
                    <h4>{{ component.title || component.label || component.componentId }}</h4>

                    <template v-if="component.type === 'form'">
                        <div class="form-grid">
                            <label v-for="field in component.fields || []" :key="field.field">
                                {{ field.label }}
                                <span v-if="field.required" class="required">*</span>

                                <input v-if="field.type === 'input' || !field.type" v-model="formState[field.field]"
                                    :placeholder="field.placeholder || ''" />

                                <textarea v-else-if="field.type === 'textarea'" v-model="formState[field.field]"
                                    :placeholder="field.placeholder || ''" />

                                <select v-else-if="field.type === 'select'" v-model="formState[field.field]">
                                    <option value="">请选择</option>
                                    <option v-for="option in field.options || []" :key="option.value"
                                        :value="option.value">
                                        {{ option.label }}
                                    </option>
                                </select>

                                <input v-else-if="field.type === 'number'" v-model.number="formState[field.field]"
                                    type="number" :placeholder="field.placeholder || ''" />

                                <input v-else v-model="formState[field.field]" :placeholder="field.placeholder || ''" />
                            </label>
                        </div>

                        <div class="action-row">
                            <button v-for="action in component.actions || []" :key="action.actionId || action.label"
                                class="primary-button" @click="handleAction(action)">
                                {{ action.label || action.type }}
                            </button>
                        </div>
                    </template>

                    <template v-else-if="component.type === 'table'">
                        <table>
                            <thead>
                                <tr>
                                    <th v-for="column in component.columns || []" :key="column.field">
                                        {{ column.label }}
                                    </th>
                                </tr>
                            </thead>

                            <tbody>
                                <tr v-for="(row, index) in tableRows(component)" :key="index">
                                    <td v-for="column in component.columns || []" :key="column.field">
                                        {{ displayCell(row, column.field) }}
                                    </td>
                                </tr>

                                <tr v-if="tableRows(component).length === 0">
                                    <td :colspan="component.columns?.length || 1" class="empty-cell">
                                        暂无 Mock 数据
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </template>

                    <template v-else>
                        <div class="unsupported">
                            暂不支持组件类型：{{ component.type }}
                        </div>
                    </template>
                </div>
            </section>

            <div v-if="message" class="mock-message">
                {{ message }}
            </div>
        </main>

        <aside class="contract-inspector">
            <h3>契约约束</h3>

            <dl>
                <template v-for="(value, key) in contract.developmentRules || {}" :key="key">
                    <dt>{{ key }}</dt>
                    <dd>{{ value }}</dd>
                </template>
            </dl>

            <h3>API 绑定</h3>

            <div v-for="binding in contract.apiBindings || []"
                :key="String(binding.bindingKey || binding.path || JSON.stringify(binding))" class="binding-item">
                <strong>{{ binding.method || '-' }} {{ binding.path || '-' }}</strong>
                <span>{{ binding.mockBinding || binding.mockAction || '-' }}</span>
            </div>
        </aside>
    </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type {
    UiContractAction,
    UiContractComponent,
    UiContractContent,
    UiContractPage,
} from '../types/uiContract'

const props = defineProps<{
    contract: UiContractContent
}>()

const currentPageId = ref('')
const message = ref('')

type FormStateValue = string | number

const formState = reactive<Record<string, FormStateValue>>({})
const runtimeData = reactive<Record<string, Array<Record<string, unknown>>>>({})

const pages = computed<UiContractPage[]>(() => props.contract.pages || [])

const currentPage = computed(() => {
    if (!currentPageId.value && pages.value.length > 0) {
        currentPageId.value = pages.value[0].pageId
    }

    return pages.value.find((page) => page.pageId === currentPageId.value) || pages.value[0]
})

resetRuntime()

function resetRuntime() {
    Object.keys(runtimeData).forEach((key) => {
        delete runtimeData[key]
    })

    const mockData = props.contract.mockData || {}

    Object.entries(mockData).forEach(([key, value]) => {
        runtimeData[key] = Array.isArray(value)
            ? JSON.parse(JSON.stringify(value))
            : []
    })

    Object.keys(formState).forEach((key) => {
        delete formState[key]
    })

    message.value = ''
}

function routeOf(pageId: string): string {
    const route = (props.contract.routes || []).find((item) => item.pageId === pageId)
    return route?.path || '-'
}

function tableRows(component: UiContractComponent): Record<string, unknown>[] {
    const key = component.dataSource?.mockBinding

    if (!key) {
        return []
    }

    return runtimeData[key] || []
}

function displayCell(row: Record<string, unknown>, field: string): string {
    const value = row[field]
    return value === null || value === undefined ? '-' : String(value)
}

function handleAction(action: UiContractAction) {
    if (action.type === 'reset-form') {
        Object.keys(formState).forEach((key) => {
            delete formState[key]
        })

        message.value = '表单已重置。'
        return
    }

    if (action.nextPage) {
        currentPageId.value = action.nextPage
    }

    if (action.mockAction || action.type === 'submit') {
        const targetKey = firstRuntimeDataKey()

        if (targetKey) {
            runtimeData[targetKey].push({
                id: `MOCK-${Date.now()}`,
                ...JSON.parse(JSON.stringify(formState)),
            })
        }

        message.value = action.successMessage || 'Mock 操作已完成。'
        return
    }

    message.value = action.successMessage || `已触发动作：${action.type}`
}

function firstRuntimeDataKey(): string | null {
    const keys = Object.keys(runtimeData)
    return keys.length > 0 ? keys[0] : null
}
</script>

<style scoped>
.ui-contract-shell {
    display: grid;
    grid-template-columns: 220px minmax(0, 1fr) 260px;
    gap: 18px;
    padding: 20px 24px 32px;
}

.page-list,
.contract-inspector,
.mock-screen {
    border: 1px solid #e2e8f0;
    border-radius: 18px;
    background: #ffffff;
}

.page-list,
.contract-inspector {
    padding: 16px;
    height: fit-content;
}

.page-list h3,
.contract-inspector h3 {
    margin: 0 0 12px;
    font-size: 14px;
    color: #0f172a;
}

.page-list button {
    width: 100%;
    margin-bottom: 10px;
    padding: 10px 12px;
    border: 1px solid #e2e8f0;
    border-radius: 12px;
    background: #f8fafc;
    text-align: left;
    cursor: pointer;
}

.page-list button.active {
    border-color: #2563eb;
    background: #eff6ff;
}

.page-list strong,
.page-list span {
    display: block;
}

.page-list span {
    margin-top: 4px;
    font-size: 12px;
    color: #64748b;
}

.mock-screen {
    min-height: 620px;
    padding: 18px;
}

.screen-toolbar {
    display: flex;
    justify-content: space-between;
    gap: 18px;
    padding-bottom: 14px;
    border-bottom: 1px solid #e2e8f0;
}

.screen-toolbar h3 {
    margin: 0 0 4px;
    font-size: 18px;
    color: #0f172a;
}

.screen-toolbar p {
    margin: 0;
    font-size: 13px;
    color: #64748b;
}

.page-canvas {
    display: grid;
    gap: 16px;
    padding-top: 18px;
}

.contract-card {
    padding: 18px;
    border: 1px solid #e2e8f0;
    border-radius: 16px;
    background: #ffffff;
}

.contract-card h4 {
    margin: 0 0 14px;
    font-size: 15px;
    color: #0f172a;
}

.form-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
}

.form-grid label {
    display: grid;
    gap: 6px;
    font-size: 13px;
    font-weight: 600;
    color: #334155;
}

.required {
    color: #dc2626;
}

input,
select,
textarea {
    width: 100%;
    box-sizing: border-box;
    border: 1px solid #cbd5e1;
    border-radius: 10px;
    padding: 9px 10px;
    font: inherit;
}

textarea {
    min-height: 78px;
    resize: vertical;
}

.action-row {
    display: flex;
    gap: 10px;
    margin-top: 16px;
}

.primary-button,
.secondary-button {
    border: none;
    border-radius: 999px;
    padding: 9px 14px;
    font-weight: 700;
    cursor: pointer;
}

.primary-button {
    background: #2563eb;
    color: #ffffff;
}

.secondary-button {
    border: 1px solid #cbd5e1;
    background: #ffffff;
    color: #334155;
}

table {
    width: 100%;
    border-collapse: collapse;
    font-size: 13px;
}

th,
td {
    border-bottom: 1px solid #e2e8f0;
    padding: 10px;
    text-align: left;
}

th {
    background: #f8fafc;
    color: #334155;
}

.empty-cell {
    text-align: center;
    color: #94a3b8;
}

.mock-message {
    position: sticky;
    bottom: 14px;
    margin-top: 16px;
    padding: 12px 14px;
    border-radius: 12px;
    background: #dcfce7;
    color: #166534;
    font-size: 13px;
    font-weight: 700;
}

.binding-item {
    padding: 10px 0;
    border-top: 1px solid #e2e8f0;
}

.binding-item strong,
.binding-item span {
    display: block;
}

.binding-item span {
    margin-top: 4px;
    font-size: 12px;
    color: #64748b;
}

.contract-inspector dl {
    margin: 0 0 16px;
}

.contract-inspector dt {
    margin-top: 8px;
    font-size: 12px;
    color: #64748b;
}

.contract-inspector dd {
    margin: 2px 0 0;
    font-size: 13px;
    color: #0f172a;
}
</style>