<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchArticles, type ArticleVO } from '@/api/public'

const route = useRoute()
const router = useRouter()
const rows = ref<ArticleVO[]>([])
const total = ref(0)
const current = ref(1)
const keyword = ref('')

async function load() {
  const page = await fetchArticles({
    current: current.value,
    size: 10,
    keyword: keyword.value || undefined,
    categoryId: (route.query.category as string) || undefined,
    tagId: (route.query.tag as string) || undefined,
  })
  rows.value = page.records
  total.value = Number(page.total)
}

function search() {
  current.value = 1
  router.replace({
    query: {
      ...route.query,
      keyword: keyword.value || undefined,
    },
  })
  load()
}

watch(
  () => route.query,
  () => {
    current.value = 1
    load()
  },
)

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索标题" style="width: 220px" clearable @keyup.enter="search" />
      <el-button @click="search">搜索</el-button>
    </div>
    <article v-for="a in rows" :key="a.id" class="item">
      <h3>
        <router-link :to="`/articles/${a.slug || a.id}`">{{ a.title }}</router-link>
      </h3>
      <p>{{ a.summary }}</p>
      <div class="meta">{{ a.categoryName }} · {{ a.publishTime }}</div>
    </article>
    <p v-if="!rows.length" class="empty">没有符合条件的文章</p>
    <el-pagination
      v-model:current-page="current"
      layout="prev, pager, next"
      :total="total"
      :page-size="10"
      style="margin-top: 12px"
      @current-change="load"
    />
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.item {
  padding: 12px 0;
  border-bottom: 1px solid #f2f2f2;
}
.item h3 {
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
