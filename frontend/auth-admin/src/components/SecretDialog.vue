<template>
  <el-dialog :model-value="visible" title="客户端密钥（仅本次可见）" width="520px" @close="emit('close')">
    <el-alert type="warning" :closable="false" show-icon title="明文仅本次可见，请立即复制保存" class="mt12" />
    <p>客户端 ID：{{ secret.clientId }}</p>
    <div class="secret-box">
      <span class="secret-value">{{ shown ? secret.clientSecret : masked }}</span>
      <el-button link type="primary" @click="shown = !shown">{{ shown ? '隐藏' : '显示' }}</el-button>
      <el-button link type="primary" @click="copySecret">复制</el-button>
    </div>
    <template #footer>
      <el-button type="primary" @click="emit('close')">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { ClientSecretVO } from '@/types'

const props = defineProps<{
  visible: boolean
  secret: ClientSecretVO
}>()

const emit = defineEmits<{
  close: []
}>()

const masked = '••••••••••••••••••••••••'
const shown = ref(false)

// 每次打开默认打码，防肩窥（密钥不入 localStorage / console）
watch(
  () => props.visible,
  (open) => {
    if (open) shown.value = false
  },
)

async function copySecret() {
  try {
    await navigator.clipboard.writeText(props.secret.clientSecret)
    ElMessage.success({ message: '已复制', duration: 2000 })
  } catch {
    ElMessage.error('复制失败，请手动选择文本复制')
  }
}
</script>

<style scoped>
.secret-box {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--border);
  border-radius: var(--control-radius);
  padding: 8px 12px;
  background: #fafafa;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 13px;
  word-break: break-all;
}

.secret-value {
  flex: 1;
}
</style>
