<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchTags, type TagVO } from '@/api/public'

const tags = ref<TagVO[]>([])

onMounted(async () => {
  tags.value = await fetchTags()
})
</script>

<template>
  <div class="card">
    <h3>标签</h3>
    <div class="cloud">
      <router-link
        v-for="t in tags"
        :key="t.id"
        class="tag"
        :to="{ path: '/articles', query: { tag: t.id } }"
      >
        {{ t.name }}{{ t.articleCount != null ? ` (${t.articleCount})` : '' }}
      </router-link>
    </div>
  </div>
</template>

<style scoped>
.cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.tag {
  background: #ecf5ff;
  color: #409eff;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 13px;
}
</style>
