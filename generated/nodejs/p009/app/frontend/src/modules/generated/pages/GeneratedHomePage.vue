<template>
  <section class="generated-home">
    <div class="generated-hero">
      <small>CodeMind Generated System</small>
      <h1>智能仓储管理系统</h1>
      <p>当前 MODULE 是正式开发切片。门禁会检查模块本地核心资产，并聚合 5 个下属 PAGE 的页面规范、交互原型和确认快照。</p>
    </div>

    <div class="generated-grid">
      <article v-for="item in resources" :key="item.api" class="generated-card">
        <small>{ item.api }</small>
        <strong>{ item.title }</strong>
        <span>{ item.fields } 个字段已接入 Node.js API</span>
      </article>
    </div>
  </section>
</template>

<script setup>
const resources = [
  {
  "title": "入库单创建页",
  "api": "/api/inbound-orders",
  "fields": 18
},
  {
  "title": "入库单列表页",
  "api": "/api/inbound-orders-2",
  "fields": 9
},
  {
  "title": "入库单详情页",
  "api": "/api/inbound-orders-3",
  "fields": 0
},
  {
  "title": "入库单数量编辑页",
  "api": "/api/inbound-orders-4",
  "fields": 0
},
  {
  "title": "入库单审核页",
  "api": "/api/inbound-orders-5",
  "fields": 0
}
]
</script>
