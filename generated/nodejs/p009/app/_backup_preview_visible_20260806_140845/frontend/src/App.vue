<template>
  <main class="app-shell">
    <aside class="app-sidebar">
      <strong>CodeMind Generated App</strong>
      <span>Node.js Fullstack</span>
      <nav>
        <button
          v-for="route in navigationRoutes"
          :key="route.path"
          type="button"
          :class="{ active: currentPath === route.path }"
          @click="go(route.path)"
        >
          {{ route.title || route.name }}
        </button>
      </nav>
    </aside>

    <section class="app-content">
      <RouterView />
    </section>
  </main>
</template>

<script setup>
import { computed } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { navigationRoutes } from './router/routes.js'

const route = useRoute()
const router = useRouter()
const currentPath = computed(() => route.path)

function go(path) {
  router.push(path)
}
</script>
