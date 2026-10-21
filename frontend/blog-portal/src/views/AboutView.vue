<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchSiteConfig, type SiteConfigVO } from '@/api/public'

const site = ref<SiteConfigVO>({})

onMounted(async () => {
  site.value = await fetchSiteConfig()
})
</script>

<template>
  <div class="reading-wrap">
    <div class="about-card">
      <h2>关于本站</h2>
      <p class="about">{{ site.about || '暂无介绍' }}</p>
      <hr />
      <div class="meta">
        <span>{{ site.title || 'Blog' }}</span>
        <span class="dot">·</span>
        <span>{{ site.copyright || '' }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.about-card {
  background: var(--bg-surface);
  border-radius: var(--page-radius);
  box-shadow: var(--shadow-card);
  padding: 36px 42px;
}
h2 {
  margin: 0 0 18px;
  font-size: 22px;
}
.about {
  white-space: pre-wrap;
  line-height: 1.9;
  color: var(--text-regular);
  margin: 0;
}
hr {
  border: none;
  height: 1px;
  background: var(--border);
  margin: 24px 0;
}
.meta {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--text-secondary);
  font-size: 12px;
}
.meta .dot {
  opacity: 0.5;
}
</style>
