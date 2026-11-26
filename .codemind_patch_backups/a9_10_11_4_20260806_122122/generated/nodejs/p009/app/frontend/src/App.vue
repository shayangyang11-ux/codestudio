<template>
  <div class="app-shell">
    <aside class="app-sidebar">
      <h1>CodeMind Generated<br />App</h1>
      <p>Node.js Fullstack</p>

      <nav class="app-nav">
        <RouterLink to="/inbound/orders">入库单列表</RouterLink>
        <RouterLink to="/inbound/orders/create">入库单创建</RouterLink>
        <RouterLink to="/inbound/orders/1001">入库单详情</RouterLink>
        <RouterLink to="/inbound/orders/1001/approval">入库单审核</RouterLink>
        <RouterLink to="/inbound/orders/1001/quantity-edit">数量编辑</RouterLink>
      </nav>
    </aside>

    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  background: #f3f6fb;
  color: #0f172a;
}

.app-sidebar {
  width: 240px;
  flex-shrink: 0;
  padding: 28px 20px;
  background: #111827;
  color: #fff;
}

.app-sidebar h1 {
  margin: 0 0 10px;
  font-size: 22px;
  line-height: 1.25;
}

.app-sidebar p {
  margin: 0 0 28px;
  color: #bfdbfe;
}

.app-nav {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.app-nav a {
  color: #dbeafe;
  text-decoration: none;
  padding: 10px 12px;
  border-radius: 10px;
}

.app-nav a.router-link-active {
  background: #2563eb;
  color: #fff;
}

.app-main {
  flex: 1;
  min-width: 0;
  padding: 24px;
}
</style>
