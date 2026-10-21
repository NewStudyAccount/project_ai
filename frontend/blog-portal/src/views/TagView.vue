<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchTags, type TagVO } from '@/api/public'
import TagCloud from '@/components/TagCloud.vue'

const tags = ref<TagVO[]>([])

onMounted(async () => {
  tags.value = await fetchTags()
})
</script>

<template>
  <div class="page-body">
    <div class="page-title-row">
      <h1>标签</h1>
      <span class="sub">字号随文章数权重 · 仅已发布计数</span>
    </div>

    <div class="tagwall">
      <TagCloud :tags="tags" weighted />
      <p v-if="!tags.length" class="empty-inline">暂无标签</p>
    </div>
  </div>
</template>

<style scoped>
.page-title-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin: 0 0 16px;
}
.page-title-row h1 {
  margin: 0;
  font-size: 23px;
}
.page-title-row .sub {
  color: var(--text-secondary);
  font-size: 12.5px;
}
.tagwall {
  background: var(--bg-surface);
  border-radius: var(--page-radius);
  box-shadow: var(--shadow-card);
  padding: 30px 32px;
}
.empty-inline {
  color: var(--text-secondary);
  font-size: 12px;
  margin: 0;
}
</style>
