<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  fetchArticles,
  fetchCategories,
  fetchSiteConfig,
  fetchTags,
  type ArticleVO,
  type CategoryVO,
  type SiteConfigVO,
  type TagVO,
} from '@/api/public'
import Hero from '@/components/Hero.vue'
import ArticleCard from '@/components/ArticleCard.vue'
import SideCard from '@/components/SideCard.vue'
import CategoryList from '@/components/CategoryList.vue'
import TagCloud from '@/components/TagCloud.vue'

const site = ref<SiteConfigVO>({})
const featuredMain = ref<ArticleVO | null>(null)
const featuredSide = ref<ArticleVO[]>([])
const latest = ref<ArticleVO[]>([])
const categories = ref<CategoryVO[]>([])
const tags = ref<TagVO[]>([])

onMounted(async () => {
  const [top, recent] = await Promise.all([
    fetchArticles({ current: 1, size: 5, orderBy: 'is_top' }),
    fetchArticles({ current: 1, size: 10 }),
  ])
  const pinned = top.records.filter((a) => a.isTop === 1)
  featuredMain.value = pinned[0] || null
  featuredSide.value = pinned.slice(1, 3)
  latest.value = recent.records

  const [cats, tagList, siteConfig] = await Promise.all([
    fetchCategories().catch(() => [] as CategoryVO[]),
    fetchTags().catch(() => [] as TagVO[]),
    fetchSiteConfig().catch(() => ({} as SiteConfigVO)),
  ])
  categories.value = cats
  tags.value = tagList
  site.value = siteConfig
})
</script>

<template>
  <div>
    <Hero :title="site.title || 'Blog'" :description="site.description || ''" />

    <div class="page-body">
      <template v-if="featuredMain">
        <div class="section-head">
          <h2>置顶精选</h2>
          <span class="sub">isTop</span>
          <router-link class="more" to="/articles">全部文章 →</router-link>
        </div>
        <div class="featured">
          <ArticleCard :article="featuredMain" variant="main" :show-tags="false" />
          <div v-if="featuredSide.length" class="feature-col">
            <ArticleCard
              v-for="a in featuredSide"
              :key="a.id"
              :article="a"
              variant="side"
              :show-summary="false"
              :show-tags="false"
            />
          </div>
        </div>
      </template>

      <div class="layout-2">
        <div>
          <div class="section-head">
            <h2>最新文章</h2>
            <span class="sub">publish_time DESC</span>
          </div>
          <ArticleCard v-for="a in latest" :key="a.id" :article="a" />
          <p v-if="!latest.length" class="empty-state">暂无文章</p>
        </div>

        <aside>
          <SideCard title="分类">
            <CategoryList :categories="categories" />
            <p v-if="!categories.length" class="empty-inline">暂无分类</p>
          </SideCard>
          <SideCard title="标签">
            <TagCloud :tags="tags" weighted />
            <p v-if="!tags.length" class="empty-inline">暂无标签</p>
          </SideCard>
          <SideCard title="关于本站">
            <p class="site-mini">
              {{ site.description || '暂无介绍' }}
              <router-link class="more-link" to="/about">了解更多 →</router-link>
            </p>
          </SideCard>
        </aside>
      </div>
    </div>
  </div>
</template>

<style scoped>
.section-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin: 4px 0 14px;
}
.section-head h2 {
  margin: 0;
  font-size: 19px;
}
.section-head .sub {
  color: var(--text-secondary);
  font-size: 12px;
}
.section-head .more {
  margin-left: auto;
  font-size: 12.5px;
}
.featured {
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 16px;
  margin-bottom: 26px;
}
.feature-col {
  display: grid;
  gap: 16px;
  align-content: start;
}
.site-mini {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.9;
  margin: 0;
}
.more-link {
  display: inline-block;
  margin-top: 6px;
}
.empty-inline {
  color: var(--text-secondary);
  font-size: 12px;
  margin: 0;
}
@media (max-width: 1200px) {
  .featured {
    grid-template-columns: 1fr;
  }
}
</style>
