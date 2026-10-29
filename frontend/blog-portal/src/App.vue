<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchSiteConfig, type SiteConfigVO } from '@/api/public'

const route = useRoute()
const router = useRouter()
const site = ref<SiteConfigVO>({})
const keywordInput = ref('')

const brand = computed(() => site.value.title || 'Blog')

const navItems = [
  { to: '/', label: '首页' },
  { to: '/articles', label: '文章' },
  { to: '/categories', label: '分类' },
  { to: '/tags', label: '标签' },
  { to: '/ask', label: '问知识库' },
  { to: '/about', label: '关于' },
]

function navActive(path: string): boolean {
  if (path === '/') return route.path === '/'
  return route.path === path || route.path.startsWith(`${path}/`)
}

function onSearch() {
  const keyword = keywordInput.value.trim()
  router.push({ path: '/articles', query: keyword ? { keyword } : {} })
}

onMounted(async () => {
  try {
    site.value = await fetchSiteConfig()
  } catch {
    site.value = {}
  }
})
</script>

<template>
  <div class="app-shell">
    <header class="site-header">
      <div class="site-header-inner">
        <router-link to="/" class="brand">
          <span class="brand-dot" aria-hidden="true" />
          <span class="brand-name">{{ brand }}</span>
        </router-link>
        <nav class="site-nav">
          <router-link
            v-for="item in navItems"
            :key="item.to"
            :to="item.to"
            :class="{ on: navActive(item.to) }"
          >
            {{ item.label }}
          </router-link>
        </nav>
        <form class="site-search" @submit.prevent="onSearch">
          <input v-model="keywordInput" type="search" placeholder="搜索文章标题…" />
        </form>
      </div>
    </header>

    <main>
      <router-view />
    </main>

    <footer class="site-footer">
      {{ site.copyright || site.description || '' }}
    </footer>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
main {
  flex: 1;
}
.site-header {
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  z-index: 20;
}
.site-header-inner {
  max-width: 1120px;
  margin: 0 auto;
  padding: 0 24px;
  height: 60px;
  display: flex;
  align-items: center;
  gap: 28px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 19px;
  font-weight: 700;
  letter-spacing: 0.3px;
  color: var(--text-primary);
}
.brand-dot {
  width: 26px;
  height: 26px;
  border-radius: 9px;
  background: var(--accent-gradient);
  box-shadow: 0 4px 12px color-mix(in srgb, var(--primary) 35%, transparent);
  flex: none;
}
.brand-name {
  white-space: nowrap;
}
.site-nav {
  display: flex;
  gap: 4px;
}
.site-nav a {
  color: var(--text-regular);
  padding: 7px 13px;
  border-radius: var(--button-radius);
  font-size: 13.5px;
}
.site-nav a:hover {
  background: var(--primary-light);
  color: var(--primary);
}
.site-nav a.on {
  background: var(--primary-light);
  color: var(--primary);
  font-weight: 600;
}
.site-search {
  margin-left: auto;
  width: 210px;
}
.site-search input {
  width: 100%;
  background: var(--bg-page);
  border: 1px solid var(--border);
  border-radius: var(--button-radius);
  padding: 7px 15px;
  color: var(--text-primary);
  font-size: 12.5px;
  outline: none;
}
.site-search input:focus {
  border-color: var(--primary);
}
.site-footer {
  border-top: 1px solid var(--border);
  padding: 22px 0;
  text-align: center;
  color: var(--text-secondary);
  font-size: 12px;
}
@media (max-width: 768px) {
  .site-header-inner {
    height: auto;
    padding: 10px 14px;
    flex-wrap: wrap;
    gap: 10px;
  }
  .site-nav {
    order: 3;
    width: 100%;
    overflow-x: auto;
  }
  .site-search {
    width: 150px;
  }
}
</style>
