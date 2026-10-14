<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  createArticle,
  getArticle,
  listCategories,
  pageTags,
  updateArticle,
  type CategoryVO,
  type TagVO,
} from '@/api/content'
import { uploadFile } from '@/api/file'

const route = useRoute()
const router = useRouter()
const articleId = computedId()
const isEdit = !!articleId

const categories = ref<CategoryVO[]>([])
const tags = ref<TagVO[]>([])
const form = reactive({
  title: '',
  slug: '',
  categoryId: '0',
  summary: '',
  coverFileId: '0',
  tagIds: [] as string[],
  isTop: 0,
})
const uploading = ref(false)

function computedId() {
  const id = route.params.id
  return typeof id === 'string' && id && id !== 'new' ? id : null
}

async function loadOptions() {
  categories.value = await listCategories()
  const page = await pageTags({ current: 1, size: 200 })
  tags.value = page.records
}

async function loadArticle() {
  if (!articleId) return
  const detail = await getArticle(articleId)
  form.title = detail.title
  form.slug = detail.slug || ''
  form.categoryId = detail.categoryId || '0'
  form.summary = detail.summary || ''
  form.coverFileId = detail.coverFileId || '0'
  form.isTop = detail.isTop ?? 0
  form.tagIds = (detail.tags || []).map((t) => t.id)
}

async function onCoverChange(file: File) {
  uploading.value = true
  try {
    const meta = await uploadFile(file, 'ARTICLE_COVER')
    form.coverFileId = meta.id
    ElMessage.success('封面已上传')
  } finally {
    uploading.value = false
  }
}

async function save() {
  const body = {
    title: form.title,
    slug: form.slug,
    categoryId: form.categoryId,
    summary: form.summary,
    coverFileId: form.coverFileId,
    tagIds: form.tagIds,
    isTop: form.isTop,
  }
  if (articleId) {
    await updateArticle(articleId, body)
    ElMessage.success('主表已保存（正文未改动）')
    router.push('/articles')
  } else {
    const created = await createArticle(body)
    ElMessage.success('主表已创建，请继续写正文')
    router.push(`/articles/${created.id}/content`)
  }
}

onMounted(async () => {
  await loadOptions()
  await loadArticle()
})
</script>

<template>
  <div class="page">
    <el-alert type="info" :closable="false" show-icon title="仅保存主表元数据；正文请到「写正文」页，两页分离提交" />
    <el-form :model="form" label-width="100px" class="form">
      <el-form-item label="标题" required>
        <el-input v-model="form.title" maxlength="200" />
      </el-form-item>
      <el-form-item label="slug">
        <el-input v-model="form.slug" maxlength="120" placeholder="空则用 id 访问" />
      </el-form-item>
      <el-form-item label="分类">
        <el-select v-model="form.categoryId" clearable placeholder="未分类">
          <el-option label="未分类" value="0" />
          <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="标签">
        <el-select v-model="form.tagIds" multiple clearable>
          <el-option v-for="t in tags" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="摘要">
        <el-input v-model="form.summary" type="textarea" :rows="3" maxlength="500" />
      </el-form-item>
      <el-form-item label="封面">
        <input type="file" accept="image/*" @change="onCoverChange(($event.target as HTMLInputElement).files?.[0] as File)" />
        <span v-if="form.coverFileId && form.coverFileId !== '0'" class="muted">fileId: {{ form.coverFileId }}</span>
      </el-form-item>
      <el-form-item label="置顶">
        <el-switch v-model="form.isTop" :active-value="1" :inactive-value="0" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="uploading" @click="save">{{ isEdit ? '保存主表' : '创建并写正文' }}</el-button>
        <el-button @click="router.push('/articles')">返回</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped>
.page {
  padding: 16px;
}
.form {
  margin-top: 16px;
  max-width: 640px;
}
.muted {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
</style>
