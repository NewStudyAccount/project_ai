<script setup lang="ts">
import { BlogPermissions } from '@/types'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
</script>

<template>
  <el-card>
    <h3>博客管理端骨架</h3>
    <p>本变更范围：布局 + RBAC 管理页。文章/评论 UI 见后续 content-modules。</p>
    <p>
      权限码常量示例：
      <code>{{ BlogPermissions.ROLE_MANAGE }}</code>
    </p>
    <p>当前用户：{{ userStore.username || '—' }}</p>
  </el-card>
</template>
