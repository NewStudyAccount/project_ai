<template>
  <el-container class="layout">
    <el-aside width="240px" class="aside">
      <div class="brand">认证运营后台</div>
      <el-menu router :default-active="route.path" class="menu">
        <el-menu-item index="/dashboard">工作台</el-menu-item>
        <template v-for="group in visibleMenus" :key="group.id">
          <el-sub-menu v-if="group.children.length" :index="group.path || group.id">
            <template #title>{{ group.name }}</template>
            <el-menu-item v-for="item in group.children" :key="item.id" :index="normalizePath(item.path)">
              {{ item.name }}
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else-if="group.path" :index="normalizePath(group.path)">
            {{ group.name }}
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span>统一认证中心</span>
        <span class="operator">
          {{ auth.operatorName }}
          <el-button link type="primary" @click="onLogout">退出</el-button>
        </span>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { logout } from '@/utils/oidc'
import type { MenuVO } from '@/types'

const route = useRoute()
const auth = useAuthStore()

function onLogout() {
  logout()
}

const visibleMenus = computed(() => flattenVisible(auth.menus))

function flattenVisible(menus: MenuVO[]): MenuVO[] {
  return menus
    .filter((menu) => menu.hidden !== 1 && menu.status !== 0)
    .map((menu) => ({
      ...menu,
      children: flattenVisible(menu.children || []).filter((child) => child.type === 2 || child.children.length > 0),
    }))
    .filter((menu) => menu.type === 1 || menu.type === 2)
}

function normalizePath(path: string): string {
  if (!path) return ''
  return path.startsWith('/') ? path : '/' + path
}
</script>

<style scoped>
.layout {
  min-height: 100vh;
}

/* 侧栏：深靛紫极光渐变（纯色兜底）+ 顶部极光光晕 */
.aside {
  background-color: var(--bg-sidebar);
  background-image: var(--bg-sidebar-gradient);
  position: relative;
  overflow: hidden;
}

.aside::before {
  content: "";
  position: absolute;
  inset: 0;
  background: var(--sidebar-glow);
  pointer-events: none;
}

.brand {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  color: var(--text-sidebar);
  border-bottom: 1px solid var(--bg-sidebar-active);
}

.menu {
  border-right: none;
  background: transparent;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border);
}

.operator {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-regular);
}

.main {
  background: var(--bg-page);
  padding: 0;
}
</style>
