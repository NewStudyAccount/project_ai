<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { PostSummary } from '@/api/public'
import { listPublicPosts } from '@/api/public'

const posts = ref<PostSummary[]>([])
const tip = ref('加载中…')

onMounted(async () => {
  try {
    const page = await listPublicPosts()
    posts.value = page.records
    tip.value = posts.value.length ? '' : '暂无文章（content-modules 落地后展示）'
  } catch {
    tip.value = '公开文章接口尚未就绪（见 add-blog-content-modules）'
  }
})
</script>

<template>
  <section>
    <h1>匿名阅读</h1>
    <p v-if="tip" class="tip">{{ tip }}</p>
    <article v-for="p in posts" :key="p.id" class="post">
      <h2>{{ p.title }}</h2>
      <p>{{ p.summary }}</p>
    </article>
  </section>
</template>

<style scoped>
.tip {
  color: #999;
}
.post {
  padding: 12px 0;
  border-bottom: 1px solid #eee;
}
</style>
