<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'

/** 目录由注入后的渲染 HTML 扫 h2/h3 生成（呈现增强，不解析 Markdown、不改注入语义） */
const props = defineProps<{ html: string }>()

interface TocItem {
  id: string
  text: string
  level: number
}

const items = ref<TocItem[]>([])

function slugify(text: string, index: number): string {
  const base = text
    .trim()
    .toLowerCase()
    .replace(/\s+/g, '-')
    .replace(/[^\w\u4e00-\u9fa5-]/g, '')
  return `sec-${index}-${base || 'h'}`.slice(0, 64)
}

async function build() {
  await nextTick()
  const root = document.getElementById('article-content')
  if (!root) {
    items.value = []
    return
  }
  const list: TocItem[] = []
  root.querySelectorAll<HTMLHeadingElement>('h2, h3').forEach((h, i) => {
    if (!h.id) h.id = slugify(h.textContent || '', i)
    list.push({ id: h.id, text: (h.textContent || '').trim(), level: h.tagName === 'H3' ? 3 : 2 })
  })
  items.value = list
}

function jump(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

watch(() => props.html, build)
onMounted(build)
</script>

<template>
  <section v-if="items.length" class="rail-card">
    <h4>目录</h4>
    <ul class="rail-toc">
      <li v-for="item in items" :key="item.id" :class="{ l3: item.level === 3 }">
        <a @click="jump(item.id)">{{ item.text }}</a>
      </li>
    </ul>
  </section>
</template>

<style scoped>
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
.rail-toc {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 46vh;
  overflow: auto;
}
.rail-toc li {
  margin: 6px 0;
  font-size: 12.5px;
}
.rail-toc .l3 {
  padding-left: 14px;
}
.rail-toc .l3 a {
  color: var(--text-secondary);
}
.rail-toc a {
  color: var(--text-regular);
  cursor: pointer;
}
.rail-toc a:hover {
  color: var(--primary);
}
</style>
