<template>
  <div class="login-page">
    <section class="login-brand-panel">
      <div class="login-brand-card">
        <div class="brand-logo large">CM</div>
        <h1>CodeMind Studio</h1>
        <p>企业级 AI Agent 开发平台</p>

        <div class="login-feature-list">
          <div>
            <strong>需求 Agent</strong>
            <span>结构化生成模块树、PRD、工程资产。</span>
          </div>
          <div>
            <strong>开发 Agent</strong>
            <span>面向模块和契约生成协同代码。</span>
          </div>
          <div>
            <strong>测试 Agent</strong>
            <span>生成测试规格、用例和质量报告。</span>
          </div>
        </div>
      </div>
    </section>

    <section class="login-form-panel">
      <form
        class="login-card"
        @submit.prevent="handleSubmit"
      >
        <h2>账号登录</h2>
        <p>使用账号密码进入个人空间或团队空间。</p>

        <label>
          <span>账号</span>
          <input
            v-model="account"
            autocomplete="username"
            placeholder="local_user"
          />
        </label>

        <label>
          <span>密码</span>
          <input
            v-model="password"
            autocomplete="current-password"
            type="password"
            placeholder="codemind123"
          />
        </label>

        <div
          v-if="errorMessage"
          class="login-error"
        >
          {{ errorMessage }}
        </div>

        <button
          class="login-button"
          type="submit"
          :disabled="loading"
        >
          {{ loading ? '登录中...' : '登录' }}
        </button>

        <div class="login-dev-hint">
          <strong>本地开发账号</strong>
          <span>local_user / codemind123</span>
        </div>
      </form>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { login } from '../api/codemindApi'
import type { CurrentUser } from '../types/codemind'

const emit = defineEmits<{
  loginSuccess: [user: CurrentUser]
}>()

const account = ref('local_user')
const password = ref('codemind123')
const loading = ref(false)
const errorMessage = ref('')

async function handleSubmit() {
  errorMessage.value = ''

  if (!account.value.trim() || !password.value.trim()) {
    errorMessage.value = '请输入账号和密码。'
    return
  }

  loading.value = true

  try {
    const response = await login({
      account: account.value.trim(),
      password: password.value,
    })

    emit('loginSuccess', response.user)
  } catch (error) {
    console.error(error)
    errorMessage.value = '账号或密码错误，或服务暂时不可用。'
  } finally {
    loading.value = false
  }
}
</script>