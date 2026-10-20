<template>
  <CrudPage :schema="schema" @custom-action="onCustomAction">
    <template #col-status="{ row }">
      <span :class="['status-pill', pillClassOf(statusOptionOf(row.status))]">
        {{ statusOptionOf(row.status).label }}
      </span>
      <span v-if="row.hasDraft" class="draft-pill status-pill is-off">草稿</span>
    </template>
  </CrudPage>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CrudPage from '@/components/common/CrudPage.vue'
import { listCategories, type ArticleVO, type CategoryVO } from '@/api/content'
import { ARTICLE_STATUS_OPTIONS, buildArticleSchema } from './article.config'
import type { OptionItem } from '@/types/crud'

const router = useRouter()

const categories = ref<OptionItem[]>([])
const schema = computed(() => buildArticleSchema(categories.value))

/** 分类筛选项来自既有分类树接口（扁平化为下拉选项） */
function flattenCategories(nodes: CategoryVO[], out: OptionItem[] = []): OptionItem[] {
  for (const node of nodes) {
    out.push({ value: node.id, label: node.name })
    if (node.children?.length) flattenCategories(node.children, out)
  }
  return out
}

/** 状态映射后端枚举 desc；缺省按草稿（与原 statusText 回退一致） */
function statusOptionOf(status?: number): OptionItem {
  return ARTICLE_STATUS_OPTIONS.find((o) => o.value === status) ?? ARTICLE_STATUS_OPTIONS[0]
}

function pillClassOf(option: OptionItem): string {
  return option.pillTone === 'danger' ? 'is-bad' : option.pillTone === 'warning' ? 'is-off' : 'is-on'
}

/** 主表/正文跳转链路保持现状（adopt-markdown-editor owns），仅形态层接线 */
function onCustomAction(key: string, row?: ArticleVO) {
  if (key === 'create') {
    router.push('/articles/new')
    return
  }
  if (!row) return
  if (key === 'main') {
    router.push(`/articles/${row.id}/main`)
  } else if (key === 'content') {
    router.push(`/articles/${row.id}/content`)
  }
}

onMounted(async () => {
  categories.value = flattenCategories(await listCategories())
})
</script>

<style scoped>
.draft-pill {
  margin-left: 4px;
  height: 18px;
  padding: 0 8px;
  font-size: 11px;
}
</style>
