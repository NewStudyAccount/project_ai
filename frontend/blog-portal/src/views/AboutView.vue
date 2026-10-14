<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchSiteConfig, type SiteConfigVO } from '@/api/public'

const site = ref<SiteConfigVO>({})

onMounted(async () => {
  site.value = await fetchSiteConfig()
})
</script>

<template>
  <div class="card">
    <h3>关于</h3>
    <p class="about">{{ site.about || site.description || '暂无介绍' }}</p>
  </div>
</template>

<style scoped>
.about {
  white-space: pre-wrap;
  line-height: 1.75;
}
</style>
