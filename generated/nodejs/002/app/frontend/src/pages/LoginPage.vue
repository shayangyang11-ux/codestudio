<template>
  <section class="auth-card">
    <p class="eyebrow">Welcome Back</p>
    <h1>欢迎登录</h1>
    <p class="subtle">请输入账号与密码进入业务系统。演示账号 demo，密码 demo123。</p>

    <form class="form-grid" @submit.prevent="handleLogin">
      <label>账号<input v-model.trim="form.account" autocomplete="username" placeholder="请输入账号" /></label>
      <label>密码<input v-model="form.password" autocomplete="current-password" type="password" placeholder="请输入密码" /></label>
      <button class="primary-action" type="submit" :disabled="loading">{{ loading ? '登录中...' : '登录' }}</button>
    </form>

    <p v-if="message" class="status-message">{{ message }}</p>
    <p v-if="error" class="error-message">{{ error }}</p>
    <p class="status-message">没有账号？ <button class="secondary-link" type="button" @click="goRegister">去注册</button></p>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { login } from '../api/authApi.js'

const loading = ref(false)
const message = ref('')
const error = ref('')
const form = reactive({ account: 'demo', password: 'demo123' })

async function handleLogin() {
  loading.value = true
  error.value = ''
  message.value = ''
  try {
    const result = await login({ account: form.account || 'demo', password: form.password || 'demo123' })
    localStorage.setItem('codemind.auth.user', JSON.stringify(result.user || result))
    message.value = '登录成功，正在进入用户首页。'
    window.location.href = '/user/home'
  } catch (err) {
    error.value = err?.message || '登录失败'
  } finally {
    loading.value = false
  }
}

function goRegister() {
  window.location.href = '/auth/register'
}
</script>
