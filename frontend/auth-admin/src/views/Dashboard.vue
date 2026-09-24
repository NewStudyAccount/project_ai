<template>
  <div class="page-card">
    <h2>工作台</h2>
    <p>统一认证中心运营后台：Client、授权审计与 RBAC。</p>
    <h3>当前操作员菜单</h3>
    <el-table :data="auth.menus" row-key="id" default-expand-all border>
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="type" label="类型" width="90" />
      <el-table-column prop="path" label="路由" width="180" />
      <el-table-column prop="permission" label="权限标识" min-width="180" />
    </el-table>
    <h3 class="mt12">当前操作员权限</h3>
    <el-tag v-for="perm in auth.permissions" :key="perm" class="perm-tag">{{ perm }}</el-tag>
    <el-empty v-if="!auth.permissions.length" description="暂无权限标识" />
  </div>
</template>

<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
</script>

<style scoped>
.perm-tag {
  margin: 0 8px 8px 0;
}
</style>
