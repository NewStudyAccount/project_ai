<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchArticles, type ArticleVO } from '@/api/public'

const topList = ref<ArticleVO[]>([])
const latest = ref<ArticleVO[]>([])

onMounted(async () => {
  const top = await fetchArticles({ current: 1, size: 5, orderBy: 'is_top' })
  topList.value = top.records
  const recent = await fetchArticles({ current: 1, size: 10 })
  latest.value = recent.records
})
</script>

<template>
  <div>
    <section class="card">
      <h3>置顶</h3>
      <ul class="list">
        <li v-for="a in topList" :key="a.id">
          <router-link :to="`/articles/${a.slug || a.id}`">{{ a.title }}</router-link>
          <span class="meta">{{ a.publishTime }}</span>
        </li>
        <li v-if="!topList.length" class="empty">暂无置顶文章</li>
      </ul>
    </section>
    <section class="card">
      <h3>最新</h3>
      <article v-for="a in latest" :key="a.id" class="item">
        <h4>
          <router-link :to="`/articles/${a.slug || a.id}`">{{ a.title }}</router-link>
        </h4>
        <p class="summary">{{ a.summary }}</p>
        <div class="meta">{{ a.categoryName }} · {{ a.publishTime }} · {{ a.authorName }}</div>
      </article>
      <p v-if="!latest.length" class="empty">暂无文章</p>
    </section>
  </div>
</template>

<style scoped>
.list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.list li {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid #f2f2f2;
}
.item h4 {
  margin: 0 0 8px;
}
.summary {
  color: #606266;
  margin: 0 0 8px;
}
.meta {
  color: #909399;
  font-size: 12px;
}
.empty {
  color: #909399;
}
</style>
