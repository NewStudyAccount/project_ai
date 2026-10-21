<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  fetchArticles,
  fetchCategories,
  fetchTags,
  type ArticleVO,
  type CategoryVO,
  type TagVO,
} from '@/api/public'
import ArticleCard from '@/components/ArticleCard.vue'
import SideCard from '@/components/SideCard.vue'
import CategoryList from '@/components/CategoryList.vue'
import TagCloud from '@/components/TagCloud.vue'
import Pager from '@/components/Pager.vue'
import FilterChips from '@/components/FilterChips.vue'

const route = useRoute()
const router = useRouter()

const rows = ref<ArticleVO[]>([])
const total = ref(0)
const current = ref(1)
const size = 10
const categories = ref<CategoryVO[]>([])
const tags = ref<TagVO[]>([])

function flattenCats(list: CategoryVO[], out: Record<string, string> = {}): Record<string, string> {
  for (const c of list) {
    out[c.id] = c.name
    if (c.children?.length) flattenCats(c.children, out)
  }
  return out
}

const chips = computed(() => {
  const q = route.query
  const catMap = flattenCats(categories.value)
  const tagMap = Object.fromEntries(tags.value.map((t) => [t.id, t.name]))
  const list: { key: string; label: string }[] = []
  if (q.category) list.push({ key: 'category', label: `分类：${catMap[String(q.category)] || q.category}` })
  if (q.tag) list.push({ key: 'tag', label: `标签：${tagMap[String(q.tag)] || q.tag}` })
  if (q.keyword) list.push({ key: 'keyword', label: `关键词：${q.keyword}` })
  return list
})

async function load() {
  const page = await fetchArticles({
    current: current.value,
    size,
    keyword: (route.query.keyword as string) || undefined,
    categoryId: (route.query.category as string) || undefined,
    tagId: (route.query.tag as string) || undefined,
  })
  rows.value = page.records
  total.value = Number(page.total)
}

function onPageChange(page: number) {
  current.value = page
  load()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function clearFilter(key: string) {
  const query: Record<string, string> = {}
  for (const [k, v] of Object.entries(route.query)) {
    if (k !== key && typeof v === 'string') query[k] = v
  }
  router.replace({ path: '/articles', query })
}

function clearAll() {
  router.replace({ path: '/articles' })
}

watch(
  () => route.query,
  () => {
    current.value = 1
    load()
  },
)

onMounted(async () => {
  const [cats, tagList] = await Promise.all([
    fetchCategories().catch(() => [] as CategoryVO[]),
    fetchTags().catch(() => [] as TagVO[]),
  ])
  categories.value = cats
  tags.value = tagList
  load()
})
</script>

<template>
  <div class="page-body">
    <div class="page-title-row">
      <h1>文章</h1>
      <span class="sub">共 {{ total }} 篇 · is_top DESC, publish_time DESC</span>
    </div>

    <FilterChips :chips="chips" @clear="clearFilter" @clear-all="clearAll" />

    <div class="layout-2">
      <div>
        <ArticleCard v-for="a in rows" :key="a.id" :article="a" />
        <div v-if="!rows.length" class="empty-state">
          没有符合条件的文章
          <a v-if="chips.length" @click="clearAll">清除筛选</a>
        </div>
        <Pager :current="current" :size="size" :total="total" @change="onPageChange" />
      </div>

      <aside>
        <SideCard title="快速筛选">
          <CategoryList :categories="categories" />
          <p v-if="!categories.length" class="empty-inline">暂无分类</p>
        </SideCard>
        <SideCard title="热门标签">
          <TagCloud :tags="tags" weighted />
          <p v-if="!tags.length" class="empty-inline">暂无标签</p>
        </SideCard>
      </aside>
    </div>
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
.empty-inline {
  color: var(--text-secondary);
  font-size: 12px;
  margin: 0;
}
.empty-state a {
  margin-left: 8px;
  cursor: pointer;
}
</style>
