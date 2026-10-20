<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { fetchArticle, type ArticleDetailVO } from '@/api/public'
import { renderMarkdown } from '@/utils/markdown'

const route = useRoute()
const article = ref<ArticleDetailVO | null>(null)
const notFound = ref(false)

const html = computed(() => renderMarkdown(article.value?.content || ''))

async function load() {
  notFound.value = false
  article.value = null
  try {
    article.value = await fetchArticle(String(route.params.idOrSlug || ''))
  } catch {
    notFound.value = true
  }
}

watch(() => route.params.idOrSlug, load)
onMounted(load)
</script>

<template>
  <div class="card">
    <div v-if="notFound" class="empty">文章不存在或未发布</div>
    <template v-else-if="article">
      <h1>{{ article.title }}</h1>
      <div class="meta">
        {{ article.categoryName }} · {{ article.publishTime }} · {{ article.authorName }}
      </div>
      <div class="tags">
        <router-link
          v-for="t in article.tags || []"
          :key="t.id"
          class="tag"
          :to="{ path: '/articles', query: { tag: t.id } }"
        >
          #{{ t.name }}
        </router-link>
      </div>
      <!-- eslint-disable-next-line vue/no-v-html -- 正文经 renderMarkdown 先转义再生成 HTML -->
      <div class="markdown" v-html="html"></div>
    </template>
  </div>
</template>

<style scoped>
h1 {
  margin: 0 0 8px;
  font-size: 28px;
}
.meta {
  color: var(--text-secondary);
  font-size: 13px;
  margin-bottom: 8px;
}
.tags {
  margin-bottom: 16px;
}
.tag {
  margin-right: 8px;
  font-size: 12px;
}
.empty {
  color: var(--text-secondary);
  padding: 40px 0;
  text-align: center;
}
</style>
