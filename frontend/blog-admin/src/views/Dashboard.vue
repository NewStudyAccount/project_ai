<template>
  <div class="page-card">
    <div class="page-header">
      <div class="page-title">博客管理台</div>
      <div class="page-desc">文章、分类、标签、文件与对象存储的运营后台。菜单与按钮权限由后端下发，前端按 permission 标识渲染。</div>
    </div>

    <div class="section-title">快捷入口</div>
    <div class="quick-grid">
      <div v-for="item in shortcuts" :key="item.path" class="quick-item" @click="go(item.path)">
        <div class="quick-name">{{ item.name }}</div>
        <div class="quick-desc">{{ item.desc }}</div>
      </div>
    </div>

    <!-- 统计卡片仅当现有接口可提供数据时展示；当前无统计接口，按 D5 降级为说明卡 + 快捷入口 -->
    <div class="stat-note">统计卡片随现有接口就绪后展示，当前无可用统计接口，故仅保留说明卡与快捷入口。</div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const shortcuts = [
  { name: '文章管理', desc: '文章主表、正文与发布/下架', path: '/articles' },
  { name: '分类管理', desc: '分类树维护与引用约束', path: '/categories' },
  { name: '标签管理', desc: '标签维护与引用计数', path: '/tags' },
  { name: '文件管理', desc: '上传、公开 URL 预览下载、逻辑删除', path: '/files' },
  { name: '存储服务商', desc: '多服务商配置、连通性与默认项', path: '/storage/providers' },
]

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
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
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

.stat-note {
  margin-top: 12px;
  font-size: 12px;
  color: var(--text-secondary);
}
</style>
