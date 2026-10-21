<script setup lang="ts">
defineProps<{
  chips: { key: string; label: string }[]
}>()

defineEmits<{
  clear: [key: string]
  'clear-all': []
}>()
</script>

<template>
  <div v-if="chips.length" class="filter-bar">
    <span class="label">筛选中：</span>
    <span v-for="c in chips" :key="c.key" class="chip">
      {{ c.label }}
      <a class="x" @click="$emit('clear', c.key)">✕</a>
    </span>
    <a v-if="chips.length > 1" class="chip plain clear-all" @click="$emit('clear-all')">
      清除全部
    </a>
  </div>
</template>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 18px;
  background: var(--bg-surface);
  border-radius: var(--page-radius);
  box-shadow: var(--shadow-card);
  padding: 13px 18px;
}
.label {
  color: var(--text-secondary);
  font-size: 12px;
}
.chip {
  margin: 0;
}
.x {
  margin-left: 6px;
  opacity: 0.55;
  font-size: 10px;
  cursor: pointer;
}
.x:hover {
  opacity: 1;
}
.clear-all {
  cursor: pointer;
}
</style>
