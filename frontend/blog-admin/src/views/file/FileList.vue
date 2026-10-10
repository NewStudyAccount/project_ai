<template>
  <div class="page-card">
    <div class="stat-row">
      <el-card shadow="never" class="stat">
        <div class="stat-label">有效文件</div>
        <div class="stat-value">{{ total }}</div>
      </el-card>
      <el-card shadow="never" class="stat">
        <div class="stat-label">策略</div>
        <div class="stat-sub">单文件 ≤10MB · png/jpeg/gif/pdf · 预签名 10min</div>
      </el-card>
    </div>

    <div class="toolbar">
      <el-button type="primary" @click="openUpload">上传</el-button>
      <el-input
        v-model="query.keyword"
        placeholder="原始文件名关键字"
        clearable
        style="width: 200px"
        @keyup.enter="load"
      />
      <el-select v-model="query.contentType" placeholder="全部类型" clearable style="width: 160px">
        <el-option label="PNG" value="image/png" />
        <el-option label="JPEG" value="image/jpeg" />
        <el-option label="GIF" value="image/gif" />
        <el-option label="PDF" value="application/pdf" />
      </el-select>
      <el-button @click="load">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <el-table v-if="records.length" :data="records" border>
      <el-table-column label="文件名" min-width="220">
        <template #default="{ row }">
          <div class="file-name">
            <div class="name" :title="row.originalName">{{ row.originalName }}</div>
            <div class="key" :title="row.objectKey">{{ shortKey(row.objectKey) }}</div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="contentType" label="类型" width="120" />
      <el-table-column label="大小" width="100">
        <template #default="{ row }">{{ formatBytes(row.sizeBytes) }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="上传时间" width="180" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="preview(row, false)">预览</el-button>
          <el-button link type="primary" @click="preview(row, true)">下载</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else description="暂无文件" />

    <el-pagination
      v-model:current-page="query.current"
      v-model:page-size="query.size"
      :total="total"
      layout="total, sizes, prev, pager, next"
      @current-change="load"
      @size-change="load"
    />

    <el-dialog v-model="uploadVisible" title="上传文件" width="480px">
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
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="doUpload">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import { deleteFile, pageFiles, presignFile, uploadFile } from '@/api/file'
import type { FileMeta } from '@/types'

const MAX_BYTES = 10 * 1024 * 1024
const ALLOW = ['image/png', 'image/jpeg', 'image/gif', 'application/pdf']

const records = ref<FileMeta[]>([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, keyword: '', contentType: '' })
const uploadVisible = ref(false)
const uploading = ref(false)
const selected = ref<File | null>(null)

function formatBytes(n: number) {
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(2) + ' MB'
}

function shortKey(key: string) {
  return key.length > 20 ? key.slice(0, 20) + '…' : key
}

async function load() {
  const page = await pageFiles({
    current: query.current,
    size: query.size,
    keyword: query.keyword || undefined,
    contentType: query.contentType || undefined,
  })
  records.value = page.records
  total.value = page.total
}

function reset() {
  query.keyword = ''
  query.contentType = ''
  query.current = 1
  load()
}

function openUpload() {
  selected.value = null
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
  if (raw.size > MAX_BYTES) {
    ElMessage.error('超过单文件上限 10MB')
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
  uploading.value = true
  try {
    await uploadFile(selected.value)
    ElMessage.success('上传成功')
    uploadVisible.value = false
    query.current = 1
    await load()
  } finally {
    uploading.value = false
  }
}

async function preview(row: FileMeta, asDownload: boolean) {
  const res = await presignFile(row.id)
  if (asDownload) {
    const a = document.createElement('a')
    a.href = res.url
    a.download = row.originalName
    a.click()
  } else {
    window.open(res.url, '_blank')
  }
}

async function remove(row: FileMeta) {
  await ElMessageBox.confirm(
    `确认删除「${row.originalName}」？这是逻辑删除，MinIO 对象延后清理。`,
    '删除文件',
    { type: 'warning' },
  )
  await deleteFile(row.id)
  ElMessage.success('已逻辑删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.stat-row {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}
.stat {
  flex: 1;
}
.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.stat-value {
  font-size: 22px;
  font-weight: 600;
}
.stat-sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}
.file-name .name {
  font-weight: 500;
}
.file-name .key {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
  align-items: center;
}
</style>
