<template>
  <el-container class="admin-layout">
    <el-aside width="220px">
      <el-menu :default-active="route.path" router>
        <el-menu-item index="/clients">客户端列表</el-menu-item>
        <el-menu-item index="/grants">令牌与会话</el-menu-item>
        <el-menu-item index="/audit">登录审计</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header>认证客户端管理</el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
const route = useRoute()
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
}
</style>
