<template>
  <div class="callback-page">
    <p>{{ message }}</p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { consumeReturnTo, exchangeCode, redirectForLogin } from '@/utils/oidc'

const router = useRouter()
const message = ref('正在完成登录…')

onMounted(async () => {
  try {
    await exchangeCode(new URLSearchParams(window.location.search))
    sessionStorage.removeItem('oidc:callback_retried')
    const returnTo = consumeReturnTo()
    await router.replace(returnTo)
  } catch (error) {
    const text = error instanceof Error ? error.message : '登录失败'
    const retried = sessionStorage.getItem('oidc:callback_retried') === '1'
    if (!retried) {
      sessionStorage.setItem('oidc:callback_retried', '1')
      message.value = '登录失败，正在重新跳转统一登录…'
      await redirectForLogin(consumeReturnTo())
      return
    }
    sessionStorage.removeItem('oidc:callback_retried')
    message.value = `登录失败：${text}`
  }
})
</script>

<style scoped>
.callback-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  color: var(--el-text-color-regular);
}
</style>
