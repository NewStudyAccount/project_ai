<template>
  <div class="page-card">
    <div class="page-header">
      <div class="page-title">用户中心</div>
      <div class="page-desc">账号管理、RBAC 和管理审计。</div>
    </div>

    <!-- 统计卡片：现有 api 无计数类接口（userApi/auditApi/rbacApi 均为查询/分页），按约定降级不渲染 -->
    <div class="section-title">快捷入口</div>
    <div class="quick-grid">
      <div v-for="item in shortcuts" :key="item.path" class="quick-item" @click="go(item.path)">
        <div class="quick-name">{{ item.name }}</div>
        <div class="quick-desc">{{ item.desc }}</div>
      </div>
    </div>

    <div class="section-title">当前操作员菜单</div>
    <el-table :data="auth.menus" row-key="id" default-expand-all border>
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="type" label="类型" width="90" />
      <el-table-column prop="path" label="路由" width="180" />
      <el-table-column prop="permission" label="权限标识" min-width="180" />
    </el-table>

    <div class="section-title">当前操作员权限</div>
    <el-tag v-for="perm in auth.permissions" :key="perm" class="perm-tag">{{ perm }}</el-tag>
    <el-empty v-if="!auth.permissions.length" description="暂无权限标识" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { hasPermission } from '@/utils/permission'

const auth = useAuthStore()
const router = useRouter()

/** 快捷入口：path 取 router/index.ts 真实路由；无权限整钮不渲染（hasPermission） */
const allShortcuts = [
  { name: '账号管理', desc: '账号列表、新建与启停', path: '/users', permission: 'user:user:list' },
  { name: '审计日志', desc: '操作留痕查询与详情', path: '/audits', permission: 'user:audit:list' },
  { name: '菜单管理', desc: '菜单树维护', path: '/menus', permission: 'user:menu:list' },
  { name: '角色管理', desc: '角色维护与授权', path: '/roles', permission: 'user:role:list' },
]

const shortcuts = computed(() => allShortcuts.filter((item) => hasPermission(item.permission)))

function go(path: string) {
  router.push(path)
}
</script>

<style scoped>
.page-header {
  margin-bottom: 16px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.page-desc {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  margin: 12px 0;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 8px;
}

.quick-item {
  border: 1px solid var(--border);
  border-radius: var(--page-radius);
  padding: 16px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.quick-item:hover {
  border-color: var(--primary);
  background: var(--primary-light);
}

.quick-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.quick-desc {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.perm-tag {
  margin: 0 8px 8px 0;
}
</style>
