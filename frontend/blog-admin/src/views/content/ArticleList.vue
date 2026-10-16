<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteArticle,
  offlineArticle,
  pageArticles,
  publishArticle,
  type ArticleVO,
} from '@/api/content'
import { hasPermission } from '@/utils/permission'
import type { PageResult } from '@/types'

const router = useRouter()
const rows = ref<ArticleVO[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ current: 1, size: 10, keyword: '', status: undefined as number | undefined })

async function load() {
  loading.value = true
  try {
    const page: PageResult<ArticleVO> = await pageArticles({ ...query })
    rows.value = page.records
    total.value = Number(page.total)
  } finally {
    loading.value = false
  }
}

function statusText(status?: number) {
  if (status === 1) return '已发布'
  if (status === 2) return '已下架'
  return '草稿'
}

function goCreate() {
  router.push('/articles/new')
}

function goEdit(row: ArticleVO) {
  router.push(`/articles/${row.id}/main`)
}

function goContent(row: ArticleVO) {
  router.push(`/articles/${row.id}/content`)
}

async function doPublish(row: ArticleVO) {
  try {
    await publishArticle(row.id)
    ElMessage.success('已发布')
    await load()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '发布失败')
  }
}

async function doOffline(row: ArticleVO) {
  await offlineArticle(row.id)
  ElMessage.success('已下架')
  await load()
}

async function remove(row: ArticleVO) {
  await ElMessageBox.confirm(`确认删除「${row.title}」？`, '提示', { type: 'warning' })
  try {
    await deleteArticle(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="标题关键字" style="width: 200px" clearable @change="load" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="load">
        <el-option label="草稿" :value="0" />
        <el-option label="已发布" :value="1" />
        <el-option label="已下架" :value="2" />
      </el-select>
      <el-button v-if="hasPermission('blog:article:create')" type="primary" @click="goCreate">新建文章</el-button>
    </div>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="title" label="标题" min-width="200" />
      <el-table-column prop="categoryName" label="分类" width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'warning' : 'info'">
            {{ statusText(row.status) }}
          </el-tag>
          <el-tag v-if="row.hasDraft" type="warning" size="small" style="margin-left: 4px">草稿</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="publishTime" label="发布时间" width="170" />
      <el-table-column prop="authorName" label="作者" width="100" />
      <el-table-column label="操作" width="280">
        <template #default="{ row }">
          <el-button v-if="hasPermission('blog:article:update')" link @click="goEdit(row)">主表</el-button>
          <el-button v-if="hasPermission('blog:article:update')" link @click="goContent(row)">正文</el-button>
          <el-button v-if="hasPermission('blog:article:publish') && row.status !== 1" link type="success" @click="doPublish(row)">发布</el-button>
          <el-button v-if="hasPermission('blog:article:publish') && row.status === 1" link type="warning" @click="doOffline(row)">下架</el-button>
          <el-button v-if="hasPermission('blog:article:delete')" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="query.current"
      v-model:page-size="query.size"
      class="pager"
      layout="total, prev, pager, next"
      :total="total"
      @current-change="load"
    />
  </div>
</template>

<style scoped>
.page {
  padding: 16px;
}
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}
.pager {
  margin-top: 12px;
}
</style>
