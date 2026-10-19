<script setup lang="ts">
/**
 * md-editor-v3 薄封装（adopt-markdown-editor / 图片与链接完善）
 * - v-model = Markdown 源文（禁止把预览 HTML 提交给父组件）
 * - 图片：上传到 /api/v1/files（ARTICLE_IMAGE），插入稳定 URL `/api/v1/public/files/{id}`
 * - 链接：仅允许 http(s)/相对站内路径；过滤 javascript: 等
 */
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { MdEditor } from 'md-editor-v3'
import type { ToolbarNames, UploadImgCallBack } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { uploadFile } from '@/api/file'

const props = withDefaults(
  defineProps<{
    modelValue: string
    placeholder?: string
    height?: string
    /** 正文插图业务 id（articleId） */
    bizId?: string
  }>(),
  {
    placeholder: 'Markdown 正文源码…',
    height: '520px',
    bizId: '',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const uploading = ref(false)

/** 安全写作用工具栏；图片支持「链接」与「上传」 */
const toolbars: ToolbarNames[] = [
  'bold',
  'underline',
  'italic',
  'strikeThrough',
  'title',
  'quote',
  'unorderedList',
  'orderedList',
  'task',
  'codeRow',
  'code',
  'link',
  'image',
  'table',
  'revoke',
  'next',
  'catalog',
  '=',
  'preview',
  'htmlPreview',
  'pageFullscreen',
  'fullscreen',
]

/** 链接/图片 URL 协议白名单 */
function isSafeUrl(url: string): boolean {
  if (!url) return false
  const trimmed = url.trim()
  if (/^javascript:/i.test(trimmed) || /^data:/i.test(trimmed) || /^vbscript:/i.test(trimmed)) {
    return false
  }
  // 允许 http(s)、站内相对路径、锚点
  return /^(https?:\/\/|\/(?!\/)|#|\.\.?\/)/i.test(trimmed) || /^[a-z0-9_-]+(\.[a-z0-9_-]+)*\//i.test(trimmed)
}

function sanitize(value: string): string {
  if (!value) return ''
  // 危险协议链接 → 占位
  return value
    .replace(/\]\(\s*javascript:[^)]*\)/gi, '](#)')
    .replace(/\]\(\s*data:[^)]*\)/gi, '](#)')
    .replace(/src=["']\s*javascript:[^"']*["']/gi, 'src=""')
}

const value = computed({
  get: () => props.modelValue,
  set: (v: string) => emit('update:modelValue', sanitize(v)),
})

/** 公开稳定图片地址（不随预签名过期） */
function publicFileUrl(id: string): string {
  return `/api/v1/public/files/${id}`
}

/**
 * md-editor 上传回调：multipart → /api/v1/files → 插入公开 URL
 */
async function onUploadImg(files: File[], callback: UploadImgCallBack) {
  if (!files?.length) return
  const imageTypes = ['image/png', 'image/jpeg', 'image/gif', 'image/webp']
  const accepted = files.filter((f) => imageTypes.includes(f.type))
  if (!accepted.length) {
    ElMessage.error('仅支持 PNG / JPEG / GIF / WebP 图片')
    callback([])
    return
  }
  if (accepted.length < files.length) {
    ElMessage.warning('已忽略非图片文件')
  }
  uploading.value = true
  try {
    const results: Array<{ url: string; alt: string; title: string }> = []
    for (const file of accepted) {
      if (file.size > 10 * 1024 * 1024) {
        ElMessage.error(`「${file.name}」超过 10MB`)
        continue
      }
      const meta = await uploadFile(file, 'ARTICLE_IMAGE', props.bizId || undefined)
      if (!isSafeUrl(publicFileUrl(meta.id))) {
        continue
      }
      const alt = meta.originalName || file.name
      results.push({
        url: publicFileUrl(meta.id),
        alt,
        title: alt,
      })
    }
    if (results.length) {
      ElMessage.success(`已插入 ${results.length} 张图片`)
      callback(results)
    } else {
      callback([])
    }
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '图片上传失败')
    callback([])
  } finally {
    uploading.value = false
  }
}

/** 链接/图片弹窗标题与字段中文（完善交互） */
const languageUserDefined = {
  linkModalTips: {
    linkTitle: '插入链接',
    imageTitle: '插入图片',
    descLabel: '描述',
    descLabelPlaceHolder: '请输入链接/图片描述',
    urlLabel: '地址',
    urlLabelPlaceHolder: 'https:// 或站内相对路径 /xxx',
    buttonOK: '确定',
  },
  imgTitleItem: {
    link: '外链图片',
    upload: '上传图片',
    clip2upload: '剪贴板上传',
  },
}
</script>

<template>
  <div class="md-editor-wrap">
    <el-text v-if="uploading" size="small" type="primary" class="uploading-tip">图片上传中…</el-text>
    <MdEditor
      v-model="value"
      language="zh-CN"
      :placeholder="placeholder"
      :style="{ height }"
      :toolbars="toolbars"
      :no-upload-img="false"
      :on-upload-img="onUploadImg"
      :language-user-defined="languageUserDefined"
      preview-theme="default"
    />
  </div>
</template>

<style scoped>
.md-editor-wrap :deep(.md-editor) {
  border-radius: 6px;
}
.uploading-tip {
  display: block;
  margin-bottom: 6px;
}
</style>
