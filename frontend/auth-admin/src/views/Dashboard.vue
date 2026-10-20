<template>
  <div class="page-card">
    <div class="page-header">
      <div class="page-title">统一认证中心 · 管理台</div>
      <div class="page-desc">Client、授权审计与 RBAC 运营后台。菜单与按钮权限由后端下发，前端按 permission 标识渲染。</div>
    </div>

    <div class="section-title">快捷入口</div>
    <div class="quick-grid">
      <div v-for="item in shortcuts" :key="item.path" class="quick-item" @click="go(item.path)">
        <div class="quick-name">{{ item.name }}</div>
        <div class="quick-desc">{{ item.desc }}</div>
      </div>
    </div>

    <h3 class="mt12">当前操作员菜单</h3>
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
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const shortcuts = [
  { name: 'Client 管理', desc: '客户端登记、密钥轮换与启停', path: '/clients' },
  { name: '授权管理', desc: '用户授权查看与吊销', path: '/grants' },
  { name: '操作审计', desc: '关键写操作留痕追溯', path: '/audit-logs' },
  { name: '登录尝试', desc: '登录成功/失败记录', path: '/login-attempts' },
]

function go(path: string) {
  router.push(path)
}
</script>

<style scoped>
.perm-tag {
  margin: 0 8px 8px 0;
}

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
</style>
