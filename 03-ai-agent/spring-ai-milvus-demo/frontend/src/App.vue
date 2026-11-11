<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { NAV_ITEMS, type NavGroup } from '@/router'

const route = useRoute()
const currentTitle = computed(() =>
  typeof route.meta.title === 'string' ? route.meta.title : '控制台',
)

const groups = computed(() =>
  (['分析', '治理', '运维'] as NavGroup[]).map((name) => ({
    name,
    items: NAV_ITEMS.filter((item) => item.group === name),
  })),
)
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand__mark" aria-hidden="true">
          <svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="#ffffff"
            stroke-width="1.7" stroke-linejoin="round" stroke-linecap="round">
            <path d="M12 2.5l8 4.5v10l-8 4.5-8-4.5V7z" />
            <path d="M12 12l8-4.5M12 12v9.5M12 12L4 7.5" />
          </svg>
        </span>
        <div>
          <p class="brand__name">AI Cost Governance</p>
          <p class="brand__sub">大模型成本控制与治理平台</p>
        </div>
      </div>

      <nav class="nav" aria-label="主导航">
        <div v-for="group in groups" :key="group.name" class="nav__group">
          <p class="nav__group-title">{{ group.name }}</p>
          <RouterLink
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            class="nav__item"
            active-class="is-active"
          >
            <span class="nav__dot" />
            <span>{{ item.label }}</span>
          </RouterLink>
        </div>
      </nav>

      <div class="sidebar__foot">
        <p>API 契约 v2 · 统一响应</p>
        <p>后端 http://localhost:8091</p>
        <p>价格版本均标记为 DEMO 示例价</p>
        <p>成本口径：ACTUAL / ALLOCATED / SAVED 分列</p>
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="topbar__crumb">
          <span>控制台</span>
          <span class="topbar__sep">/</span>
          <strong>{{ currentTitle }}</strong>
        </div>
        <div class="topbar__env">
          <span class="topbar__pulse" />
          <span>DEMO 数据环境 · 价格不代表供应商真实报价</span>
        </div>      </header>

      <main class="content">
        <div class="content__inner">
          <RouterView />
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  display: grid;
  grid-template-columns: var(--sidebar-width) minmax(0, 1fr);
  min-height: 100vh;
}

/* ---------- 侧栏 ---------- */
.sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  border-right: 1px solid var(--color-border);
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 17px var(--space-5);
  border-bottom: 1px solid var(--color-border);
}
.brand__mark {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  background: linear-gradient(135deg, var(--color-primary), #7c3aed);
  box-shadow: 0 4px 10px rgba(79, 70, 229, 0.28);
  flex-shrink: 0;
}
.brand__name { font-size: var(--font-md); font-weight: var(--font-weight-semibold); letter-spacing: -0.01em; }
.brand__sub { font-size: var(--font-xs); color: var(--color-text-muted); }

.nav { flex: 1; min-height: 0; overflow-y: auto; padding: var(--space-4) var(--space-3); }
.nav__group + .nav__group { margin-top: var(--space-5); }
.nav__group-title {
  padding: 0 10px;
  margin-bottom: 8px;
  font-size: var(--font-xs);
  font-weight: var(--font-weight-semibold);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}
.nav__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  margin-bottom: 2px;
  border-radius: var(--radius-sm);
  font-size: var(--font-base);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-secondary);
  transition: background var(--transition), color var(--transition);
}
.nav__item:hover { background: var(--color-surface-hover); }
.nav__item.is-active {
  background: var(--color-primary-soft);
  color: var(--color-primary-hover);
  font-weight: var(--font-weight-semibold);
}
.nav__dot { width: 6px; height: 6px; border-radius: 50%; background: #cbd5e1; flex-shrink: 0; }
.nav__item.is-active .nav__dot { background: var(--color-primary); }

.sidebar__foot {
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--color-border);
  font-size: var(--font-xs);
  line-height: 1.8;
  color: var(--color-text-muted);
}

/* ---------- 主区 ---------- */
.main { display: flex; flex-direction: column; min-width: 0; }
.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  height: var(--header-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: 0 var(--space-6);
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}
.topbar__crumb { display: flex; align-items: center; gap: 8px; font-size: var(--font-base); color: var(--color-text-muted); }
.topbar__crumb strong { color: var(--color-text); font-weight: var(--font-weight-semibold); }
.topbar__sep { color: #d1d5db; }
.topbar__env {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 4px 11px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-full);
  background: var(--color-surface-muted);
  font-size: var(--font-xs);
  color: var(--color-text-secondary);
}
.topbar__pulse { width: 6px; height: 6px; border-radius: 50%; background: var(--color-warning); flex-shrink: 0; }

.content { flex: 1; min-width: 0; padding: var(--space-6); }
.content__inner { width: 100%; max-width: var(--content-max-width); margin: 0 auto; }

/* ---------- 移动端 ---------- */
@media (max-width: 900px) {
  .app-shell { grid-template-columns: 1fr; }
  .sidebar {
    position: static;
    height: auto;
    border-right: none;
    border-bottom: 1px solid var(--color-border);
  }
  .brand { padding: 10px var(--space-4); }
  .nav { display: flex; flex-wrap: wrap; gap: var(--space-3); padding: 8px var(--space-4); }
  .nav__group { display: flex; align-items: center; gap: 4px; flex-wrap: wrap; }
  .nav__group + .nav__group { margin-top: 0; }
  .nav__group-title, .sidebar__foot { display: none; }
  .nav__item { margin: 0; padding: 6px 10px; }
  .content { padding: var(--space-4); }
  .topbar { padding: 0 var(--space-4); }
}
@media (max-width: 560px) {
  .topbar__env { display: none; }
}
</style>
