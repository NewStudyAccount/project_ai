<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    current: number
    total: number
    size?: number
  }>(),
  { size: 10 },
)

const emit = defineEmits<{ change: [page: number] }>()

const pageCount = computed(() => Math.max(1, Math.ceil(props.total / props.size)))

const pages = computed<number[]>(() => {
  const n = pageCount.value
  if (n <= 7) return Array.from({ length: n }, (_, i) => i + 1)
  const cur = props.current
  const start = Math.min(Math.max(cur - 3, 1), n - 6)
  return Array.from({ length: 7 }, (_, i) => start + i)
})

function go(page: number) {
  if (page >= 1 && page <= pageCount.value && page !== props.current) {
    emit('change', page)
  }
}
</script>

<template>
  <nav v-if="pageCount > 1" class="pager">
    <a :class="{ disabled: current <= 1 }" @click="go(current - 1)">上一页</a>
    <a
      v-for="p in pages"
      :key="p"
      :class="{ on: p === current }"
      @click="go(p)"
    >
      {{ p }}
    </a>
    <a :class="{ disabled: current >= pageCount }" @click="go(current + 1)">下一页</a>
  </nav>
</template>

<style scoped>
.pager {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin: 26px 0 8px;
  flex-wrap: wrap;
}
a {
  min-width: 32px;
  height: 32px;
  border-radius: 9px;
  border: 1px solid var(--border);
  background: var(--bg-surface);
  color: var(--text-regular);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12.5px;
  padding: 0 8px;
  cursor: pointer;
  user-select: none;
}
a.on {
  background: var(--primary);
  border-color: var(--primary);
  color: var(--bg-surface);
  font-weight: 600;
}
a:hover:not(.on):not(.disabled) {
  border-color: var(--primary);
  color: var(--primary);
}
a.disabled {
  opacity: 0.45;
  cursor: default;
}
</style>
