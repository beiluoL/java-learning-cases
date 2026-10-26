<template>
  <div class="admin-layout">
    <aside class="side">
      <div class="side-title">后台管理</div>
      <router-link to="/admin/dashboard" class="side-item">数据看板</router-link>
      <router-link to="/admin/products" class="side-item">商品管理</router-link>
      <router-link to="/admin/orders" class="side-item">订单管理</router-link>
      <router-link to="/admin/seckill" class="side-item">秒杀管理</router-link>
    </aside>
    <section class="content">
      <router-view />
    </section>
  </div>
</template>

<script setup></script>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 70vh;
}
.side {
  width: 180px;
  background: #fff;
  border-right: 1px solid #eee;
  padding: 12px;
}
.side-title {
  font-weight: 700;
  margin-bottom: 12px;
  color: var(--brand);
}
.side-item {
  display: block;
  padding: 10px 12px;
  border-radius: 6px;
  color: #333;
}
.side-item.router-link-active {
  background: #e6f0ff;
  color: var(--brand);
  font-weight: 600;
}
.content {
  flex: 1;
  padding: 16px;
}
</style>
