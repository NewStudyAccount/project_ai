<script setup lang="ts">
import type { ArticleDetailVO } from '@/api/public'
import ArticleToc from '@/components/ArticleToc.vue'

defineProps<{
  article: ArticleDetailVO
  /** 注入的渲染 HTML，作为目录重扫时机触发 */
  html: string
}>()
</script>

<template>
  <aside class="detail-rail">
    <section v-if="article.categoryName || article.tags?.length" class="rail-card">
      <h4>分类 / 标签</h4>
      <div v-if="article.categoryName" class="rail-cat">
        <router-link
          v-if="article.categoryId"
          :to="{ path: '/articles', query: { category: article.categoryId } }"
        >
          {{ article.categoryName }}
        </router-link>
        <span v-else>{{ article.categoryName }}</span>
      </div>
      <div v-if="article.tags?.length" class="rail-tags">
        <router-link
          v-for="t in article.tags"
          :key="t.id"
          class="chip"
          :to="{ path: '/articles', query: { tag: t.id } }"
        >
          {{ t.name }}
        </router-link>
      </div>
    </section>

    <ArticleToc :html="html" />

    <section class="rail-card">
      <router-link class="btn-ghost rail-back" to="/articles">← 返回文章列表</router-link>
    </section>
  </aside>
</template>

<style scoped>
.detail-rail {
  position: sticky;
  top: 84px;
  display: grid;
  gap: 14px;
  max-height: calc(100vh - 110px);
  overflow-y: auto;
}
@media (max-width: 1023px) {
  .detail-rail {
    display: none;
  }
}
.rail-card {
  background: var(--bg-surface);
  border-radius: var(--page-radius);
  box-shadow: var(--shadow-card);
  padding: 16px 18px;
}
h4 {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--text-primary);
}
h4::before {
  content: '';
  display: inline-block;
  width: 4px;
  height: 13px;
  border-radius: 2px;
  background: var(--accent-gradient);
  margin-right: 8px;
  vertical-align: -2px;
}
.rail-cat {
  font-size: 13px;
}
.rail-tags {
  margin-top: 10px;
}
.rail-tags .chip {
  margin-bottom: 6px;
}
.rail-back {
  display: block;
  text-align: center;
}
</style>
