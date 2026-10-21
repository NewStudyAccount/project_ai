<script setup lang="ts">
import { computed } from 'vue'
import type { TagVO } from '@/api/public'

const props = withDefaults(
  defineProps<{
    tags: TagVO[]
    weighted?: boolean
    showCount?: boolean
  }>(),
  { weighted: false, showCount: true },
)

const maxCount = computed(() =>
  props.tags.reduce((m, t) => Math.max(m, Number(t.articleCount) || 0), 0),
)

function weightClass(t: TagVO): string {
  if (!props.weighted) return ''
  const n = Number(t.articleCount) || 0
  if (maxCount.value <= 0) return ''
  const ratio = n / maxCount.value
  return ratio > 0.66 ? 'w-l' : ratio > 0.33 ? 'w-m' : 'w-s'
}
</script>

<template>
  <div class="tag-cloud">
    <router-link
      v-for="t in tags"
      :key="t.id"
      class="chip"
      :class="weightClass(t)"
      :to="{ path: '/articles', query: { tag: t.id } }"
    >
      {{ t.name }}<span v-if="showCount && t.articleCount != null"> · {{ t.articleCount }}</span>
    </router-link>
  </div>
</template>

<style scoped>
.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.chip {
  margin: 0;
}
.w-l {
  font-size: 13.5px;
  padding: 4px 14px;
}
.w-m {
  font-size: 12px;
}
.w-s {
  font-size: 10.5px;
  opacity: 0.85;
}
</style>
