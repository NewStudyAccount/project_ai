<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <el-menu router :default-active="route.path" class="menu">
        <el-menu-item index="/dashboard">首页</el-menu-item>
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
        <span>用户中心管理端</span>
        <span class="operator">
          {{ auth.operatorName }}
          <el-button link type="primary" @click="onLogout">退出</el-button>
        </span>
      </el-header>
      <el-main>
        <router-view v-if="route.path !== '/'" />
        <Dashboard v-else />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { logout } from '@/utils/oidc'
import Dashboard from '@/views/Dashboard.vue'
import type { MenuVO } from '@/types'

const route = useRoute()
const auth = useAuthStore()

function onLogout() {
  logout()
}

/** 目录 + 菜单；目录下只展示 type=2，hidden/status 过滤 */
const visibleMenus = computed(() => flattenVisible(auth.menus))

function flattenVisible(menus: MenuVO[]): MenuVO[] {
  return menus
    .filter((menu) => menu.hidden !== 1 && menu.status !== 0)
    .map((menu) => ({
      ...menu,
      children: flattenVisible(menu.children || []).filter((child) => child.type === 2),
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

.aside {
  background: #fff;
  border-right: 1px solid #ebeef5;
}

.menu {
  border-right: none;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}

.operator {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #606266;
}
</style>
