<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchSiteConfig, type SiteConfigVO } from '@/api/public'

const site = ref<SiteConfigVO>({})

onMounted(async () => {
  try {
    site.value = await fetchSiteConfig()
  } catch {
    site.value = {}
  }
})
</script>

<template>
  <div>
    <header class="site-header">
      <div class="container header-inner">
        <router-link to="/" class="brand">{{ site.title || 'Blog' }}</router-link>
        <nav>
          <router-link to="/">首页</router-link>
          <router-link to="/articles">文章</router-link>
          <router-link to="/categories">分类</router-link>
          <router-link to="/tags">标签</router-link>
          <router-link to="/about">关于</router-link>
        </nav>
      </div>
    </header>
    <main class="container">
      <router-view />
    </main>
    <footer class="site-footer">
      <div class="container">{{ site.copyright || site.description || '' }}</div>
    </footer>
  </div>
</template>

<style scoped>
.site-header {
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border);
  margin-bottom: 16px;
}
.header-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
}
.brand {
  font-weight: 600;
  font-size: 18px;
  color: var(--text-primary);
}
nav {
  display: flex;
  gap: 16px;
}
.site-footer {
  margin-top: 32px;
  padding: 16px 0;
  color: var(--text-secondary);
  font-size: 12px;
  text-align: center;
}
</style>
