<script setup lang="ts">
import { useRouter } from 'vue-router'
import { clearTokens } from '@/utils/token'
import { useUserStore } from '@/stores/user'
import { BlogPermissions } from '@/types'

const router = useRouter()
const userStore = useUserStore()

function logout() {
  clearTokens()
  userStore.reset()
  router.replace('/login')
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">Blog Admin</div>
      <el-menu router :default-active="$route.path">
        <el-menu-item index="/">首页</el-menu-item>
        <el-sub-menu index="rbac">
          <template #title>RBAC</template>
          <el-menu-item index="/rbac/roles">角色管理</el-menu-item>
          <el-menu-item index="/rbac/permissions">权限码</el-menu-item>
          <el-menu-item index="/rbac/user-roles">用户授权</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span>{{ $route.meta.title }}</span>
        <span class="user">
          {{ userStore.username || '未登录' }}
          <el-button size="small" @click="logout">退出</el-button>
        </span>
      </el-header>
      <el-main>
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  min-height: 100vh;
}
.aside {
  background: #fff;
  border-right: 1px solid #ebeef5;
}
.logo {
  font-weight: 600;
  padding: 16px;
  border-bottom: 1px solid #ebeef5;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
  background: #fff;
}
.user {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
