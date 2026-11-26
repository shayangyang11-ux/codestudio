<template>
  <main class="auth-app-shell">
    <aside class="auth-sidebar">
      <strong>CodeMind Generated App</strong>
      <span>Node.js Fullstack</span>
      <nav class="auth-nav">
        <a href="/auth/login">登录</a>
        <a href="/auth/register">注册</a>
        <a href="/user/home">用户首页</a>
      </nav>
    </aside>
    <section class="auth-app-content">
      <component :is="activeComponent" />
    </section>
  </main>
</template>

<script setup>
import { computed } from 'vue'
import { generatedRoutes } from './router/routes.js'

const activeComponent = computed(() => {
  const pathname = window.location.pathname || '/'
  const matched = generatedRoutes.find((route) => route.path === pathname)

  if (matched?.redirect) {
    window.location.replace(matched.redirect)
    return generatedRoutes.find((route) => route.path === matched.redirect)?.component || generatedRoutes[1].component
  }

  return matched?.component || generatedRoutes.find((route) => route.path === '/auth/login')?.component
})
</script>
