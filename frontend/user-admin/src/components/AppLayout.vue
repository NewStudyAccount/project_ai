<template>
  <el-container class="layout">
    <el-aside width="220px">
      <el-menu router :default-active="route.path">
        <el-menu-item index="/dashboard">首页</el-menu-item>
        <el-menu-item v-for="menu in auth.menus.filter((item) => item.type === 2)" :key="menu.id" :index="menu.path">
          {{ menu.name }}
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span>用户中心管理端</span>
        <span>{{ auth.operatorName }}</span>
      </el-header>
      <el-main>
        <router-view v-if="route.path !== '/'" />
        <Dashboard v-else />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import Dashboard from '@/views/Dashboard.vue'

const route = useRoute()
const auth = useAuthStore()
</script>

<style scoped>
.layout {
  min-height: 100vh;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}
</style>
