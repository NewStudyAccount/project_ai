<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchCategories, type CategoryVO } from '@/api/public'

const tree = ref<CategoryVO[]>([])

onMounted(async () => {
  tree.value = await fetchCategories()
})
</script>

<template>
  <div class="page-body">
    <div class="page-title-row">
      <h1>分类</h1>
      <span class="sub">最多 2 级 · 仅启用分类</span>
    </div>

    <div class="cat-grid">
      <router-link
        v-for="c in tree"
        :key="c.id"
        class="cat-card"
        :to="{ path: '/articles', query: { category: c.id } }"
      >
        <h3>
          {{ c.name }}
          <span v-if="c.articleCount != null" class="count-pill">{{ c.articleCount }} 篇</span>
        </h3>
        <p>{{ c.description || '暂无描述' }}</p>
        <div v-if="c.children?.length" class="sub-cat-row">
          <router-link
            v-for="child in c.children"
            :key="child.id"
            class="chip"
            :to="{ path: '/articles', query: { category: child.id } }"
            @click.stop
          >
            {{ child.name }}<span v-if="child.articleCount != null"> · {{ child.articleCount }}</span>
          </router-link>
        </div>
        <div v-else class="sub-cat-row">
          <span class="chip plain">无子分类</span>
        </div>
      </router-link>
    </div>

    <p v-if="!tree.length" class="empty-state">暂无分类</p>
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
.cat-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 18px;
}
.cat-card {
  background: var(--bg-surface);
  border-radius: var(--page-radius);
  box-shadow: var(--shadow-card);
  padding: 22px 24px;
  text-decoration: none;
  display: block;
  transition: box-shadow 0.18s, transform 0.18s;
}
.cat-card:hover {
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}
.cat-card h3 {
  margin: 0 0 6px;
  font-size: 18px;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 10px;
}
.count-pill {
  background: var(--primary-light);
  color: var(--primary);
  font-size: 11.5px;
  padding: 2px 11px;
  border-radius: var(--button-radius);
}
.cat-card p {
  margin: 0 0 14px;
  color: var(--text-secondary);
  font-size: 12.5px;
}
.sub-cat-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.sub-cat-row .chip {
  margin: 0;
}
@media (max-width: 768px) {
  .cat-grid {
    grid-template-columns: 1fr;
  }
}
</style>
