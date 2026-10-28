<template>
  <div class="page">
    <el-card>
      <template #header>手动导入知识文档（Markdown）</template>
      <el-upload
        drag
        multiple
        :auto-upload="false"
        :on-change="onChange"
        :file-list="fileList"
        accept=".md"
      >
        <el-icon size="40"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽 .md 文件到此处，或 <em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">仅 .md；单文件 ≤ 10MB；一次最多 20 个；一文件一知识文档</div>
        </template>
      </el-upload>
      <el-form label-width="80px" class="upload-form">
        <el-form-item label="标签">
          <el-input v-model="tags" placeholder="逗号分隔，写入全部文档，如: 内部,规范" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="uploading" :disabled="!fileList.length" @click="onUpload">
            上传并入库（{{ fileList.length }}）
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="result">
      <template #header>本次导入结果</template>
      <p class="summary">受理 {{ result.submitted }}，拒绝 {{ result.rejected.length }}</p>
      <div v-for="a in result.accepted" :key="a.docId" class="line ok">
        ✓ {{ a.originalName }} → 文档 {{ a.docId }}（任务 {{ a.taskId || '—' }}）
      </div>
      <div v-for="r in result.rejected" :key="r.originalName" class="line fail">
        ✗ {{ r.originalName }}：{{ REJECT_REASON[r.reason] || r.reason }}
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadUserFile } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { knowledgeApi, type ImportDocsResult } from '@/api/knowledge'

const REJECT_REASON: Record<string, string> = {
  TYPE_NOT_ALLOWED: '类型不允许（仅 .md）',
  TOO_LARGE: '超过 10MB',
  EMPTY_CONTENT: '内容为空',
}

const fileList = ref<UploadUserFile[]>([])
const tags = ref('')
const uploading = ref(false)
const result = ref<ImportDocsResult | null>(null)

function onChange(_file: UploadFile, list: UploadUserFile[]) {
  fileList.value = list
}

async function onUpload() {
  const files = fileList.value.map((f) => f.raw as File)
  for (const f of files) {
    if (!f.name.toLowerCase().endsWith('.md')) {
      ElMessage.error(`仅允许 .md 文件：${f.name}`)
      return
    }
    if (f.size > 10 * 1024 * 1024) {
      ElMessage.error(`单文件不能超过 10MB：${f.name}`)
      return
    }
  }
  uploading.value = true
  try {
    result.value = await knowledgeApi.importDocs(files, tags.value)
    ElMessage.success('导入受理完成')
    fileList.value = []
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.upload-form { margin-top: 16px; max-width: 480px; }
.summary { color: var(--el-text-color-secondary); }
.line { padding: 2px 0; font-size: 13px; }
.line.ok { color: var(--el-color-success); }
.line.fail { color: var(--el-color-danger); }
</style>
