<template>
  <div class="page-card">
    <div class="page-head">
      <h2 class="title">文件管理</h2>
      <span class="sub">展示页 · 上传 / 公开 URL 预览下载 / 逻辑删除</span>
    </div>

    <div class="stat-row">
      <el-card shadow="never" class="stat" style="width: 180px">
        <div class="stat-label">有效文件</div>
        <div class="stat-value">{{ statTotal }}</div>
      </el-card>
      <el-card shadow="never" class="stat" style="width: 260px">
        <div class="stat-label">默认服务商</div>
        <div class="stat-sub">
          <template v-if="defaultProvider">
            {{ defaultProvider.code }} · 桶 {{ defaultProvider.bucket }}
          </template>
          <template v-else>未配置</template>
        </div>
      </el-card>
      <el-card shadow="never" class="stat" style="flex: 1">
        <div class="stat-label">策略</div>
        <div class="stat-sub">单文件 ≤10MB · png/jpeg/gif/pdf · 公开永久 URL</div>
      </el-card>
    </div>
  </div>

  <CrudPage ref="crudRef" :schema="schema" @custom-action="onCustomAction">
    <template #col-id="{ row }">
      <div class="thumb" :title="thumbTitle(row)">
        <img
          v-if="isImage(row.contentType) && thumbSrc(row)"
          :src="thumbSrc(row) || ''"
          :alt="row.originalName"
          @error="onThumbError($event, row.contentType)"
        />
        <span v-else class="thumb-placeholder">{{ typeAbbrev(row.contentType) }}</span>
      </div>
    </template>
    <template #col-originalName="{ row }">
      <div class="file-name">
        <div class="name" :title="row.originalName">{{ row.originalName }}</div>
        <div class="key" :title="row.objectKey">{{ shortKey(row.objectKey) }}</div>
      </div>
    </template>
    <template #col-storageCode="{ row }">
      <div class="provider-cell">
        <div class="provider">{{ row.storageCode || '—' }}</div>
        <div class="bucket">{{ row.bucket || '—' }}</div>
      </div>
    </template>
  </CrudPage>

  <el-dialog v-model="uploadVisible" title="上传文件" width="480px">
    <el-form label-position="top">
      <el-form-item label="目标服务商">
        <el-select
          v-model="uploadForm.storageCode"
          placeholder="默认服务商"
          style="width: 100%"
          :disabled="providers.length === 0"
        >
          <el-option
            v-for="p in providers"
            :key="p.code"
            :label="`${p.code}${p.isDefault === 1 ? '（默认）' : ''}`"
            :value="p.code"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="业务类型 bizType（可选）">
        <el-input v-model="uploadForm.bizType" placeholder="如 ARTICLE_IMAGE / FILE" />
      </el-form-item>
      <el-form-item label="文件">
        <el-upload
          drag
          :auto-upload="false"
          :limit="1"
          :on-change="onFileChange"
          :on-remove="() => (selected = null)"
          accept=".png,.jpg,.jpeg,.gif,.pdf,image/png,image/jpeg,image/gif,application/pdf"
        >
          <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
          <template #tip>
            <div class="el-upload__tip">支持 PNG / JPEG / GIF / PDF · 单文件 ≤ 10MB</div>
          </template>
        </el-upload>
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-foot">
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="uploading"
          :disabled="providers.length === 0"
          @click="doUpload"
        >上传</el-button>
      </span>
    </template>
    <div v-if="providers.length === 0" class="no-provider-tip">
      无可用服务商，请先在「存储服务商配置页」新增并启用。
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type UploadFile } from 'element-plus'
import CrudPage from '@/components/common/CrudPage.vue'
import { getFileUrl, uploadFile } from '@/api/file'
import { storageProviderApi } from '@/api/storageProvider'
import { buildFileSchema } from './file.config'
import type { FileMeta, PageResult, StorageProviderVO } from '@/types'

const ALLOW = ['image/png', 'image/jpeg', 'image/gif', 'application/pdf']

type CrudPageExpose = { reload: () => Promise<void>; search: () => void }

const router = useRouter()
const crudRef = ref<CrudPageExpose | null>(null)

const providers = ref<StorageProviderVO[]>([])
const statTotal = ref(0)

const uploadVisible = ref(false)
const uploading = ref(false)
const selected = ref<File | null>(null)
const uploadForm = reactive<{ storageCode: string; bizType: string }>({
  storageCode: '',
  bizType: '',
})

const defaultProvider = computed(
  () => providers.value.find((p) => p.isDefault === 1) || null,
)

function onPageLoaded(page: PageResult<FileMeta>) {
  statTotal.value = Number(page.total)
}

const schema = computed(() => buildFileSchema(providers.value, onPageLoaded))

function shortKey(key: string) {
  if (!key) return ''
  return key.length > 20 ? key.slice(0, 20) + '…' : key
}

function isImage(contentType: string) {
  return contentType?.startsWith('image/')
}

function typeAbbrev(contentType: string): string {
  if (!contentType) return '—'
  if (contentType === 'application/pdf') return 'PDF'
  if (contentType === 'text/markdown') return 'MD'
  if (contentType.startsWith('image/')) {
    return contentType.slice('image/'.length).toUpperCase().slice(0, 4)
  }
  return contentType.slice(0, 4).toUpperCase()
}

function thumbTitle(row: FileMeta): string {
  return isImage(row.contentType)
    ? `${row.originalName} 缩略图（稳定公开 URL）`
    : `非图片：${row.contentType}`
}

/** 缩略图 src：优先用行内 url（设计 §7.2 列表 VO 内嵌 url） */
function thumbSrc(row: FileMeta): string {
  return row.url || ''
}

/** 缩略图加载失败：降级为类型占位（不阻塞列表） */
function onThumbError(event: Event, contentType: string) {
  const target = event.target as HTMLImageElement
  const parent = target.parentElement
  if (parent) {
    const span = document.createElement('span')
    span.className = 'thumb-placeholder'
    span.textContent = typeAbbrev(contentType)
    parent.replaceChild(span, target)
  }
}

async function loadProviders() {
  try {
    providers.value = await storageProviderApi.listEnabled()
  } catch {
    providers.value = []
  }
}

function goProviders() {
  router.push('/storage/providers')
}

function openUpload() {
  selected.value = null
  uploadForm.storageCode = defaultProvider.value?.code || ''
  uploadForm.bizType = ''
  uploadVisible.value = true
}

function onFileChange(file: UploadFile) {
  const raw = file.raw
  if (!raw) return
  if (!ALLOW.includes(raw.type)) {
    ElMessage.error('类型不在白名单（png/jpeg/gif/pdf）')
    selected.value = null
    return
  }
  selected.value = raw
}

async function doUpload() {
  if (!selected.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  if (providers.value.length === 0) {
    ElMessage.error('无可用服务商')
    return
  }
  uploading.value = true
  try {
    await uploadFile(
      selected.value,
      uploadForm.bizType || undefined,
      undefined,
      uploadForm.storageCode || undefined,
    )
    ElMessage.success('上传成功')
    uploadVisible.value = false
    crudRef.value?.search()
  } finally {
    uploading.value = false
  }
}

/** 取稳定公开 url：优先行内 url，缺省回退 GET /files/{id}/url */
async function resolveUrl(row: FileMeta): Promise<string> {
  if (row.url) return row.url
  const res = await getFileUrl(row.id)
  return res.url
}

async function preview(row: FileMeta) {
  const url = await resolveUrl(row)
  window.open(url, '_blank')
}

async function download(row: FileMeta) {
  const url = await resolveUrl(row)
  const a = document.createElement('a')
  a.href = url
  a.download = row.originalName
  a.target = '_blank'
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

/** 上传/预览/下载经 custom-action 扩展点接线，请求与结果处理保持现状逐行 */
function onCustomAction(key: string, row?: FileMeta) {
  if (key === 'upload') {
    openUpload()
    return
  }
  if (key === 'providers') {
    goProviders()
    return
  }
  if (!row) return
  if (key === 'preview') {
    void preview(row)
  } else if (key === 'download') {
    void download(row)
  }
}

onMounted(loadProviders)
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}
.page-head .title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}
.page-head .sub {
  font-size: 12px;
  color: var(--text-secondary);
}
.stat-row {
  display: flex;
  gap: 12px;
}
.stat {
  flex: 1;
}
:deep(.stat .el-card__body) {
  padding: 12px 16px;
}
.stat-label {
  font-size: 11px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}
.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
}
.stat-sub {
  font-size: 12px;
  color: var(--text-regular);
  line-height: 1.6;
}
.thumb {
  width: 40px;
  height: 40px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid var(--border);
  background: #f4f4f5;
  display: flex;
  align-items: center;
  justify-content: center;
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.thumb-placeholder {
  font-size: 9px;
  color: var(--text-secondary);
  letter-spacing: 0.02em;
  background: #fafafa;
  text-align: center;
  line-height: 1.2;
  padding: 2px;
}
.file-name .name {
  font-weight: 500;
  font-size: 13px;
  color: var(--text-primary);
}
.file-name .key {
  font-size: 11px;
  color: var(--text-secondary);
  margin-top: 2px;
}
.provider-cell .provider {
  font-size: 12px;
  color: var(--text-primary);
}
.provider-cell .bucket {
  font-size: 11px;
  color: var(--text-secondary);
  margin-top: 2px;
}
.no-provider-tip {
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #fef3c7;
  color: #92400e;
  font-size: 12px;
}
</style>
