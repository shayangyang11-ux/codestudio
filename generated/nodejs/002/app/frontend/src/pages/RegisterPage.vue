<template>
  <section class="auth-card">
    <p class="eyebrow">Create Account</p>
    <h1>注册账号</h1>
    <p class="subtle">填写账号、昵称和密码完成注册，注册成功后返回登录页。</p>

    <form class="form-grid" @submit.prevent="handleRegister">
      <label>账号<input v-model.trim="form.account" autocomplete="username" placeholder="请输入账号" /></label>
      <label>昵称<input v-model.trim="form.nickname" placeholder="请输入昵称" /></label>
      <label>密码<input v-model="form.password" type="password" autocomplete="new-password" placeholder="请输入密码" /></label>
      <label>确认密码<input v-model="form.confirmPassword" type="password" autocomplete="new-password" placeholder="请再次输入密码" /></label>
      <button class="primary-action" type="submit" :disabled="loading">{{ loading ? '注册中...' : '注册' }}</button>
    </form>

    <p v-if="message" class="status-message">{{ message }}</p>
    <p v-if="error" class="error-message">{{ error }}</p>
    <p class="status-message">已有账号？ <button class="secondary-link" type="button" @click="goLogin">返回登录</button></p>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { register } from '../api/authApi.js'

const loading = ref(false)
const message = ref('')
const error = ref('')
const form = reactive({ account: '', nickname: '', password: '', confirmPassword: '' })

async function handleRegister() {
  loading.value = true
  error.value = ''
  message.value = ''
  try {
    if (!form.account) throw new Error('账号不能为空')
    if (!form.password) throw new Error('密码不能为空')
    if (form.password !== form.confirmPassword) throw new Error('两次输入的密码不一致')
    await register({ ...form, nickname: form.nickname || form.account })
    message.value = '注册成功，正在返回登录页。'
    window.location.href = '/auth/login'
  } catch (err) {
    error.value = err?.message || '注册失败'
  } finally {
    loading.value = false
  }
}

function goLogin() {
  window.location.href = '/auth/login'
}
</script>
