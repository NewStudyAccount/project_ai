<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  discardDraft,
  getArticle,
  publishArticle,
  saveArticleContent,
  type ArticleDetailVO,
} from '@/api/content'
import MdEditor from '@/components/markdown/MdEditor.vue'

const route = useRoute()
const router = useRouter()
const articleId = String(route.params.id || '')

const detail = ref<ArticleDetailVO | null>(null)
const content = ref('')
const seoTitle = ref('')
const seoDescription = ref('')
const saving = ref(false)

const hasDraft = computed(() => !!detail.value?.hasDraft || Number(detail.value?.draftFileId) > 0)
const tip = computed(() => {
  if (detail.value?.status === 1 && hasDraft.value) return '有未发布草稿 · 线上仍是旧版正文'
  if (detail.value?.status === 1) return '已发布：保存草稿不会下线线上正文'
  return '未发布：保存草稿后可点「发布」'
})
const byteSize = computed(() => new TextEncoder().encode(content.value || '').length)
const overSize = computed(() => byteSize.value > 500 * 1024)

async function load() {
  detail.value = await getArticle(articleId)
  content.value = detail.value.content || ''
  seoTitle.value = detail.value.seoTitle || ''
  seoDescription.value = detail.value.seoDescription || ''
}

function guardSize(): boolean {
  if (overSize.value) {
    ElMessage.error('正文超过 500KB，请精简后再保存')
    return false
  }
  return true
}

async function saveDraft() {
  if (!guardSize()) return
  saving.value = true
  try {
    await saveArticleContent(articleId, {
      mode: 'draft',
      content: content.value,
      seoTitle: seoTitle.value,
      seoDescription: seoDescription.value,
    })
    ElMessage.success('草稿已保存（线上未变）')
    await load()
  } finally {
    saving.value = false
  }
}

async function saveAndPublish() {
  if (!guardSize()) return
  saving.value = true
  try {
    await saveArticleContent(articleId, {
      mode: 'publish',
      content: content.value,
      seoTitle: seoTitle.value,
      seoDescription: seoDescription.value,
    })
    ElMessage.success('已发布')
    await load()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '发布失败')
  } finally {
    saving.value = false
  }
}

async function publishOnly() {
  try {
    await publishArticle(articleId)
    ElMessage.success('已发布')
    await load()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '发布失败')
  }
}

async function dropDraft() {
  await ElMessageBox.confirm('确认放弃未发布草稿？', '提示', { type: 'warning' })
  try {
    await discardDraft(articleId)
    ElMessage.success('草稿已放弃')
    await load()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="header">
      <div>
        <strong>{{ detail?.title }}</strong>
        <el-tag v-if="detail?.status === 1" type="success" size="small" style="margin-left: 8px">已发布</el-tag>
        <el-tag v-else-if="detail?.status === 2" type="warning" size="small" style="margin-left: 8px">已下架</el-tag>
        <el-tag v-else type="info" size="small" style="margin-left: 8px">草稿</el-tag>
      </div>
      <el-button @click="router.push(`/articles/${articleId}/main`)">编辑主表</el-button>
    </div>
    <el-alert type="warning" :closable="false" :title="tip" show-icon style="margin: 12px 0" />

    <div class="editor-bar">
      <span class="label">Markdown（md-editor-v3）</span>
      <span class="muted size" :class="{ danger: overSize }">
        {{ (byteSize / 1024).toFixed(1) }}KB / 500KB · 保存 Markdown 源文 · 图片上传后为 /api/v1/public/files/{id}
      </span>
    </div>
    <MdEditor v-model="content" placeholder="Markdown 正文源码…" height="560px" :biz-id="articleId" />

    <div class="seo">
      <el-input v-model="seoTitle" placeholder="SEO 标题（存 draft_seo_*，发布时覆盖）" maxlength="200" />
      <el-input v-model="seoDescription" placeholder="SEO 描述" maxlength="300" />
    </div>
    <div class="actions">
      <el-button :loading="saving" :disabled="overSize" @click="saveDraft">保存草稿（线上不动）</el-button>
      <el-button type="primary" :loading="saving" :disabled="overSize" @click="saveAndPublish">保存并发布</el-button>
      <el-button v-if="hasDraft" type="warning" @click="publishOnly">仅发布现有草稿</el-button>
      <el-button v-if="hasDraft" type="danger" plain @click="dropDraft">放弃草稿</el-button>
    </div>
  </div>
</template>

<style scoped>
.page {
  padding: 16px;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.editor-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 8px;
}
.label {
  font-size: 12px;
  color: #606266;
  font-weight: 600;
}
.size {
  margin-left: auto;
}
.size.danger {
  color: #f56c6c;
}
.seo {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}
.actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}
.muted {
  color: #909399;
  font-size: 12px;
}
</style>
