<template>
  <section class="ucg-page">
    <header class="ucg-page-header">
      <p class="ucg-eyebrow">Node.js Fullstack Page</p>
      <h1>入库单审核页</h1>
      <p>该页面已接入真实 Node.js API：<code>/api/inbound-order-approval-page</code></p>
    </header>

    <section class="ucg-card">
      <div class="ucg-card-header">
        <div>
          <h2>查询与操作</h2>
          <p>前端会请求 Express 后端接口，后端读写本地数据层。</p>
        </div>
        <div class="ucg-actions">
          <button class="ucg-btn" type="button" @click="loadRows">查询</button>
          <button class="ucg-btn primary" type="button" @click="createRow">新增样例</button>
        </div>
      </div>

      <div class="ucg-form-grid">
        <label>
          <span>ID</span>
          <input v-model="draft['id']" placeholder="请输入ID" />
        </label>
      </div>
    </section>

    <section class="ucg-card">
      <div class="ucg-card-header">
        <div>
          <h2>业务数据</h2>
          <p>共 {{ rows.length }} 条记录。</p>
        </div>
      </div>

      <div class="ucg-table-wrap">
        <table class="ucg-table">
          <thead>
          <tr>
            <th>ID</th>
            <th>操作</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="row in rows" :key="row.id || row.name">
            <td>{{ displayValue(row["id"]) }}</td>
            <td>
              <button class="ucg-link" type="button" @click="removeRow(row)">删除</button>
            </td>
          </tr>
          </tbody>
        </table>
      </div>

      <p v-if="message" class="ucg-message">{{ message }}</p>
      <p v-if="error" class="ucg-error">{{ error }}</p>
    </section>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'

const API_BASE = '/api/inbound-order-approval-page'
const rows = ref([])
const loading = ref(false)
const message = ref('')
const error = ref('')
const draft = reactive({})

onMounted(() => {
  loadRows()
})

async function request(path = '', options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  const body = await response.json().catch(() => ({}))
  if (!response.ok || body.success === false) {
    throw new Error(body?.error?.message || '请求失败')
  }
  return body.data
}

async function loadRows() {
  loading.value = true
  error.value = ''
  try {
    const data = await request()
    rows.value = Array.isArray(data?.items) ? data.items : []
    message.value = '数据已刷新。'
  } catch (err) {
    error.value = err?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function createRow() {
  error.value = ''
  try {
    await request('', { method: 'POST', body: JSON.stringify(draft) })
    message.value = '新增成功。'
    await loadRows()
  } catch (err) {
    error.value = err?.message || '新增失败'
  }
}

async function removeRow(row) {
  if (!row?.id) return
  error.value = ''
  try {
    await request(`/${encodeURIComponent(row.id)}`, { method: 'DELETE' })
    message.value = '删除成功。'
    await loadRows()
  } catch (err) {
    error.value = err?.message || '删除失败'
  }
}

function displayValue(value) {
  return value === undefined || value === null || value === '' ? '-' : value
}
</script>

<style scoped>
/* A9.10.14.4.3.13.2.12.1_TABLE_OVERFLOW_ACTION_COLUMN_FIX */
.table-wrap,
.table-wrapper,
.table-scroll,
.runtime-table,
.ucg-table-wrap {
  width: 100% !important;
  max-width: 100% !important;
  overflow-x: auto !important;
  overflow-y: visible !important;
  padding-bottom: 8px !important;
  -webkit-overflow-scrolling: touch;
}

.table-wrap table,
.table-wrapper table,
.table-scroll table,
.runtime-table table,
.ucg-table-wrap table,
table {
  width: max-content !important;
  min-width: max(100%, 1280px) !important;
  max-width: none !important;
  table-layout: auto !important;
}

th,
td {
  white-space: nowrap !important;
}

th.row-action-header,
td.row-actions,
th.ucg-actions,
td.ucg-actions {
  width: 320px !important;
  min-width: 320px !important;
  max-width: none !important;
  background: rgba(255,255,255,0.98) !important;
}

td.row-actions,
td.ucg-actions {
  display: table-cell !important;
  text-align: left !important;
}

td.row-actions button,
td.ucg-actions button {
  white-space: nowrap !important;
  margin-right: 8px !important;
  padding: 6px 10px !important;
  min-width: 72px !important;
}
</style>
