<template>
  <LoginPage @login-success="handleSuccess" />
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import LoginPage from '../components/LoginPage.vue'
import { useAppSession } from '../composables/useAppSession'
import type { CurrentUser } from '../types/codemind'

const route = useRoute()
const router = useRouter()

const {
  handleLoginSuccess,
} = useAppSession()

async function handleSuccess(user: CurrentUser) {
  await handleLoginSuccess(user)

  const redirect = typeof route.query.redirect === 'string'
    ? route.query.redirect
    : '/workspaces'

  await router.replace(redirect)
}
</script>