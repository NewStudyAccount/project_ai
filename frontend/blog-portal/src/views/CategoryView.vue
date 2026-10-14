<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchCategories, type CategoryVO } from '@/api/public'

const tree = ref<CategoryVO[]>([])

onMounted(async () => {
  tree.value = await fetchCategories()
})
</script>

<template>
  <div class="card">
    <h3>分类</h3>
    <ul class="list">
      <li v-for="c in tree" :key="c.id">
        <router-link :to="{ path: '/articles', query: { category: c.id } }">{{ c.name }}</router-link>
        <ul v-if="c.children?.length" class="sub">
          <li v-for="child in c.children" :key="child.id">
            <router-link :to="{ path: '/articles', query: { category: child.id } }">{{ child.name }}</router-link>
          </li>
        </ul>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.list,
.sub {
  list-style: none;
  padding: 0;
}
.sub {
  margin-left: 16px;
  color: #606266;
}
li {
  padding: 6px 0;
}
</style>
