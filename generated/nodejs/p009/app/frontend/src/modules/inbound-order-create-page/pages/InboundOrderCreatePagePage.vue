<template>
  <section class="page-card">
    <p class="eyebrow">Generated Page</p>
    <h1>入库单创建页</h1>
    <p>入库单创建页交互原型 / UI_CONTRACT 1. 页面定位 Page ID： 51d24519 5f5c 4518 8a12 0974d7bca9eb Page Key： inbound_order_create_page Route： /inbound/orders/create Page </p>

    <form class="form-grid" @submit.prevent="submitPrimaryAction">
      <label>
        标题
        <input v-model="form.title" placeholder="请输入标题" />
      </label>

      <label>
        说明
        <textarea v-model="form.description" placeholder="请输入说明"></textarea>
      </label>

      <button class="primary-action" type="submit" :disabled="loading">
        {{ loading ? '处理中...' : '提交' }}
      </button>
    </form>

    <p v-if="message" class="status-message">{{ message }}</p>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { createInboundOrderCreatePageRecord } from '../api/inbound-order-create-pageApi.js'

const loading = ref(false)
const message = ref('')
const form = reactive({ title: '', description: '' })

async function submitPrimaryAction() {
  loading.value = true
  message.value = ''

  try {
    await createInboundOrderCreatePageRecord({ ...form })
    message.value = '提交成功，已通过 Node.js 后端写入本地数据层。'
  } catch (error) {
    message.value = error?.message || '提交失败'
  } finally {
    loading.value = false
  }
}
</script>
