<script setup lang="ts">
import { ref, watch } from 'vue'
import type { ArticleVO } from '@/api/public'

const props = withDefaults(
  defineProps<{
    article: ArticleVO
    variant?: 'row' | 'main' | 'side'
    showSummary?: boolean
    showTags?: boolean
  }>(),
  { variant: 'row', showSummary: true, showTags: true },
)

const coverUrl = ref('')
const coverFailed = ref(false)

watch(
  () => props.article.coverFileId,
  (id) => {
    coverFailed.value = false
    coverUrl.value = id && id !== '0' ? `/api/v1/public/files/${id}` : ''
  },
  { immediate: true },
)

const detailPath = `/articles/${props.article.slug || props.article.id}`
</script>

<template>
  <router-link :to="detailPath" class="article-card" :class="`v-${variant}`">
    <div class="cover">
      <img
        v-if="coverUrl && !coverFailed"
        :src="coverUrl"
        :alt="article.title"
        loading="lazy"
        @error="coverFailed = true"
      />
    </div>
    <div class="body">
      <h3 v-if="variant !== 'side'">
        <span v-if="article.isTop === 1" class="badge-top">置顶</span>
        <span class="title">{{ article.title }}</span>
      </h3>
      <h4 v-else>{{ article.title }}</h4>
      <p v-if="showSummary && article.summary && variant !== 'side'" class="summary">
        {{ article.summary }}
      </p>
      <div class="meta-row">
        <span v-if="article.authorName">{{ article.authorName }}</span>
        <span v-if="article.authorName" class="dot">·</span>
        <span>{{ article.publishTime || '未发布' }}</span>
        <template v-if="variant !== 'side'">
          <span class="dot">·</span>
          <router-link
            v-if="article.categoryId"
            class="cat-link"
            :to="{ path: '/articles', query: { category: article.categoryId } }"
            @click.stop
          >
            {{ article.categoryName }}
          </router-link>
          <span v-else>{{ article.categoryName || '未分类' }}</span>
        </template>
        <template v-if="showTags && article.tags?.length && variant === 'row'">
          <router-link
            v-for="t in article.tags"
            :key="t.id"
            class="chip"
            :to="{ path: '/articles', query: { tag: t.id } }"
            @click.stop
          >
            {{ t.name }}
          </router-link>
        </template>
      </div>
    </div>
  </router-link>
</template>

<style scoped>
.article-card {
  display: flex;
  gap: 18px;
  background: var(--bg-surface);
  border-radius: var(--page-radius);
  box-shadow: var(--shadow-card);
  padding: 18px;
  margin-bottom: 14px;
  transition: box-shadow 0.18s, transform 0.18s;
  text-decoration: none;
}
.article-card:hover {
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}
.cover {
  width: 168px;
  height: 108px;
  flex: none;
  border-radius: 12px;
  overflow: hidden;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--primary) 16%, var(--bg-surface)),
    color-mix(in srgb, var(--accent-bar) 22%, var(--bg-surface))
  );
}
.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.body {
  min-width: 0;
  flex: 1;
}
h3 {
  margin: 2px 0 7px;
  font-size: 16.5px;
  line-height: 1.5;
  color: var(--text-primary);
}
h3 .title {
  color: var(--text-primary);
}
.article-card:hover h3 .title {
  color: var(--primary);
}
h4 {
  margin: 0 0 6px;
  font-size: 14.5px;
  line-height: 1.5;
  color: var(--text-primary);
}
.summary {
  color: var(--text-regular);
  font-size: 13px;
  margin: 0 0 10px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.meta-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  color: var(--text-secondary);
  font-size: 12px;
}
.meta-row .dot {
  opacity: 0.5;
}
.cat-link {
  color: var(--primary);
}
.badge-top {
  display: inline-block;
  background: var(--accent-gradient);
  color: var(--bg-surface);
  font-size: 10.5px;
  padding: 2px 9px;
  border-radius: var(--button-radius);
  margin-right: 8px;
  vertical-align: 2px;
}

/* 主推大卡 */
.v-main {
  display: block;
  padding: 0;
  overflow: hidden;
}
.v-main .cover {
  width: 100%;
  height: 218px;
  border-radius: 0;
}
.v-main .body {
  padding: 18px 20px 20px;
}
.v-main h3 {
  font-size: 20px;
  margin: 0 0 8px;
}

/* 置顶侧卡（横排小卡） */
.v-side {
  padding: 0;
  gap: 0;
  overflow: hidden;
}
.v-side .cover {
  width: 118px;
  height: 100%;
  min-height: 84px;
  border-radius: 0;
}
.v-side .body {
  padding: 14px 16px;
}
.v-side .meta-row {
  gap: 8px;
}

@media (max-width: 768px) {
  .article-card {
    flex-direction: column;
    gap: 12px;
  }
  .cover {
    width: 100%;
    height: 150px;
  }
  .v-side {
    flex-direction: row;
  }
  .v-side .cover {
    width: 108px;
    height: 100%;
  }
}
</style>
