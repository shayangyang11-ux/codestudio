<template>
  <div class="placeholder-page">
    <h1>Agent 运维控制台</h1>
    <p>该页面后续用于系统管理员查看平台级 Agent Runtime、队列健康、失败率和异常项目。</p>

    <button @click="backToWorkspaces">
      返回工作区
    </button>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

async function backToWorkspaces() {
  await router.push({
    name: 'workspaces',
  })
}
</script>