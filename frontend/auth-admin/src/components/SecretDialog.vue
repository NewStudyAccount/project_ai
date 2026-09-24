<template>
  <el-dialog :model-value="visible" title="客户端密钥" width="520px" @close="emit('close')">
    <el-alert type="warning" :closable="false" show-icon title="明文仅本次可见，请立即复制保存" class="mt12" />
    <p>客户端 ID：{{ secret.clientId }}</p>
    <div class="secret-box">{{ secret.clientSecret }}</div>
    <template #footer>
      <el-button type="primary" @click="copySecret">复制密钥</el-button>
      <el-button @click="emit('close')">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import type { ClientSecretVO } from '@/types'

const props = defineProps<{
  visible: boolean
  secret: ClientSecretVO
}>()

const emit = defineEmits<{
  close: []
}>()

async function copySecret() {
  try {
    await navigator.clipboard.writeText(props.secret.clientSecret)
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败，请手动选择文本复制')
  }
}
</script>
