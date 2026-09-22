<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ssoToken } from '@/api/auth'

const route = useRoute()
const status = ref<'loading' | 'ok' | 'fail'>('loading')
const message = ref('')

onMounted(async () => {
  const code = typeof route.query.code === 'string' ? route.query.code : ''
  const clientId = typeof route.query.client_id === 'string' ? route.query.client_id : ''
  if (!code || !clientId) {
    status.value = 'fail'
    message.value = '缺少 code 或 client_id'
    ElMessage.error(message.value)
    return
  }
  try {
    const token = await ssoToken(code, clientId)
    status.value = 'ok'
    message.value = `已为 ${clientId} 换取令牌（用户 ${token.username || token.userId}）`
    ElMessage.success('SSO 授权成功')
  } catch (e) {
    status.value = 'fail'
    message.value = e instanceof Error ? e.message : '换取令牌失败'
    ElMessage.error(message.value)
  }
})
</script>

<template>
  <div class="page">
    <el-card class="card">
      <template #header>SSO 回调</template>
      <el-result
        v-if="status === 'ok'"
        icon="success"
        title="授权成功"
        :sub-title="message"
      />
      <el-result
        v-else-if="status === 'fail'"
        icon="error"
        title="授权失败"
        :sub-title="message"
      />
      <div v-else class="loading">正在换取令牌…</div>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
.card {
  width: 440px;
}
.loading {
  text-align: center;
  color: #606266;
}
</style>
