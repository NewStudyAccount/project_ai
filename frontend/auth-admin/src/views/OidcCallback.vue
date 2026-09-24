<template>
  <div class="callback-page">
    <p>{{ message }}</p>
    <el-button v-if="canRetry" type="primary" @click="onRetry">重新登录</el-button>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { consumeReturnTo, exchangeCode, redirectForLogin } from '@/utils/oidc'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const message = ref('正在完成登录…')
const canRetry = ref(false)
const retried = ref(sessionStorage.getItem('oidc:callback_retried') === '1')

onMounted(async () => {
  try {
    await exchangeCode(new URLSearchParams(window.location.search))
    sessionStorage.removeItem('oidc:callback_retried')
    auth.reset()
    await auth.init()
    const returnTo = consumeReturnTo()
    await router.replace(returnTo)
  } catch (error) {
    const text = error instanceof Error ? error.message : '登录失败'
    ElMessage.error(text)
    if (!retried.value) {
      sessionStorage.setItem('oidc:callback_retried', '1')
      message.value = '登录失败，正在重新跳转统一登录…'
      await redirectForLogin(consumeReturnTo())
      return
    }
    sessionStorage.removeItem('oidc:callback_retried')
    message.value = `登录失败：${text}。可点击下方按钮重试。`
    canRetry.value = true
  }
})

function onRetry() {
  sessionStorage.removeItem('oidc:callback_retried')
  void redirectForLogin(consumeReturnTo())
}
</script>

<style scoped>
.callback-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  gap: 12px;
  color: #606266;
}
</style>
