<script setup lang="ts">
import type { CategoryVO } from '@/api/public'

defineProps<{
  categories: CategoryVO[]
  compact?: boolean
}>()
</script>

<template>
  <ul class="cat-list">
    <li v-for="c in categories" :key="c.id">
      <router-link class="row" :to="{ path: '/articles', query: { category: c.id } }">
        <span class="name">{{ c.name }}</span>
        <span v-if="c.articleCount != null" class="count">{{ c.articleCount }}</span>
      </router-link>
      <ul v-if="!compact && c.children?.length" class="children">
        <li v-for="child in c.children" :key="child.id">
          <router-link class="row child" :to="{ path: '/articles', query: { category: child.id } }">
            <span class="name">{{ child.name }}</span>
            <span v-if="child.articleCount != null" class="count">{{ child.articleCount }}</span>
          </router-link>
        </li>
      </ul>
    </li>
  </ul>
</template>

<style scoped>
.cat-list,
.children {
  list-style: none;
  margin: 0;
  padding: 0;
}
.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 7px 8px;
  border-radius: 8px;
  font-size: 13px;
  color: var(--text-regular);
  text-decoration: none;
}
.row:hover {
  background: var(--primary-light);
}
.row.child {
  padding-left: 22px;
  color: var(--text-secondary);
}
.count {
  color: var(--text-secondary);
  font-size: 11.5px;
}
</style>
