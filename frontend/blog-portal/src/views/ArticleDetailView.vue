<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { fetchArticle, type ArticleDetailVO } from '@/api/public'

const route = useRoute()
const article = ref<ArticleDetailVO | null>(null)
const notFound = ref(false)

// content 已是服务端渲染 HTML（转义 + URL 消毒，api-design §6.4），前端只注入
const html = computed(() => article.value?.content || '')

function decorateCodeBlocks() {
  const root = document.querySelector('.markdown')
  if (!root) return
  root.querySelectorAll('pre > code[class*="language-"]').forEach((code) => {
    const m = /language-([\w-]+)/.exec(code.className)
    const pre = code.parentElement
    if (m && pre) pre.setAttribute('data-lang', m[1])
  })
}

async function load() {
  notFound.value = false
  article.value = null
  try {
    article.value = await fetchArticle(String(route.params.idOrSlug || ''))
    await nextTick()
    decorateCodeBlocks()
  } catch {
    notFound.value = true
  }
}

watch(() => route.params.idOrSlug, load)
onMounted(load)
</script>

<template>
  <div class="reading-wrap">
    <template v-if="notFound">
      <div class="empty-state">文章不存在或未发布</div>
    </template>
    <template v-else-if="article">
      <div class="crumb">
        <router-link to="/">首页</router-link>
        <span class="sep">/</span>
        <router-link to="/articles">文章</router-link>
        <template v-if="article.categoryName">
          <span class="sep">/</span>
          <router-link
            v-if="article.categoryId"
            :to="{ path: '/articles', query: { category: article.categoryId } }"
          >
            {{ article.categoryName }}
          </router-link>
          <span v-else>{{ article.categoryName }}</span>
        </template>
      </div>

      <article class="article-head">
        <h1>{{ article.title }}</h1>
        <div class="article-meta">
          <span v-if="article.authorName">{{ article.authorName }}</span>
          <span v-if="article.authorName" class="dot">·</span>
          <span>{{ article.publishTime }}</span>
          <template v-if="article.categoryName">
            <span class="dot">·</span>
            <router-link
              v-if="article.categoryId"
              class="cat-link"
              :to="{ path: '/articles', query: { category: article.categoryId } }"
            >
              {{ article.categoryName }}
            </router-link>
            <span v-else>{{ article.categoryName }}</span>
          </template>
          <template v-if="article.wordCount">
            <span class="dot">·</span>
            <span>{{ article.wordCount }} 字</span>
          </template>
        </div>
      </article>

      <div v-if="article.tags?.length" class="article-tags">
        <router-link
          v-for="t in article.tags"
          :key="t.id"
          class="chip"
          :to="{ path: '/articles', query: { tag: t.id } }"
        >
          {{ t.name }}
        </router-link>
      </div>

      <!-- eslint-disable-next-line vue/no-v-html -- 正文为服务端渲染并消毒后的 HTML（api-design §6.4） -->
      <div class="markdown" v-html="html"></div>

      <div class="article-foot">
        <div v-if="article.tags?.length" class="foot-tags">
          <router-link
            v-for="t in article.tags"
            :key="t.id"
            class="chip"
            :to="{ path: '/articles', query: { tag: t.id } }"
          >
            {{ t.name }}
          </router-link>
        </div>
        <div class="back-row">
          <router-link class="btn-ghost" to="/articles">← 返回文章列表</router-link>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.crumb {
  color: var(--text-secondary);
  font-size: 12.5px;
  margin-bottom: 18px;
}
.crumb a {
  color: var(--text-secondary);
}
.crumb a:hover {
  color: var(--primary);
}
.crumb .sep {
  margin: 0 8px;
  opacity: 0.6;
}
.article-head h1 {
  margin: 0 0 14px;
  font-size: 31px;
  line-height: 1.42;
  letter-spacing: 0.3px;
}
.article-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  color: var(--text-secondary);
  font-size: 12.5px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--border);
}
.article-meta .dot {
  opacity: 0.5;
}
.cat-link {
  color: var(--primary);
}
.article-tags {
  padding: 16px 0 4px;
}
.article-foot {
  margin-top: 38px;
  padding-top: 22px;
  border-top: 1px solid var(--border);
}
.back-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 22px;
}
@media (max-width: 768px) {
  .article-head h1 {
    font-size: 24px;
  }
}
</style>
