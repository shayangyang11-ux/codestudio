<template>
  <section class="auth-dashboard">
    <p class="eyebrow">Dashboard</p>
    <h1>用户首页</h1>
    <p class="subtle">集中展示当前用户的核心信息、快捷入口和登录状态。</p>

    <div class="dashboard-grid">
      <article class="info-tile"><span>当前账号</span><strong>{{ user.account || '-' }}</strong></article>
      <article class="info-tile"><span>用户昵称</span><strong>{{ user.nickname || '-' }}</strong></article>
      <article class="info-tile"><span>登录状态</span><strong>{{ authenticated ? '已登录' : '未登录' }}</strong></article>
    </div>

    <form class="form-grid" @submit.prevent="handleRefresh">
      <button class="primary-action" type="submit" :disabled="loading">{{ loading ? '刷新中...' : '刷新用户信息' }}</button>
      <button class="secondary-link" type="button" @click="handleLogout">退出登录</button>
    </form>

    <p v-if="message" class="status-message">{{ message }}</p>
    <p v-if="error" class="error-message">{{ error }}</p>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { getCurrentUser, logout } from '../api/authApi.js'

const loading = ref(false)
const authenticated = ref(false)
const message = ref('')
const error = ref('')
const user = reactive({ account: '', nickname: '' })

onMounted(() => { handleRefresh() })

async function handleRefresh() {
  loading.value = true
  error.value = ''
  message.value = ''
  try {
    const result = await getCurrentUser()
    const current = result.user || result
    user.account = current.account || 'demo'
    user.nickname = current.nickname || '演示用户'
    authenticated.value = true
    message.value = '用户信息已刷新。'
  } catch (err) {
    const cached = JSON.parse(localStorage.getItem('codemind.auth.user') || '{}')
    user.account = cached.account || 'demo'
    user.nickname = cached.nickname || '演示用户'
    authenticated.value = Boolean(user.account)
    error.value = err?.message || ''
  } finally {
    loading.value = false
  }
}

async function handleLogout() {
  try {
    await logout()
  } finally {
    localStorage.removeItem('codemind.auth.user')
    window.location.href = '/auth/login'
  }
}
</script>
