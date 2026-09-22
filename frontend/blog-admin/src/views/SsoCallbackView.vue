<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { exchangeSsoCode } from '@/api/sso'
import { syncUserRef } from '@/api/rbac'
import { useUserStore } from '@/stores/user'
import { redirectToSso } from '@/utils/sso'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const message = ref('统一认证登录中…')

onMounted(async () => {
  const code = typeof route.query.code === 'string' ? route.query.code : ''
  if (!code) {
    redirectToSso()
    return
  }
  try {
    const token = await exchangeSsoCode(code)
    userStore.setUser(String(token.userId ?? ''), token.username)
    try {
      await syncUserRef(token.username)
    } catch {
      // 投影失败不影响进入系统
    }
    const redirect = (route.query.redirect as string) || '/'
    router.replace(redirect)
  } catch (e) {
    message.value = e instanceof Error ? e.message : 'SSO 登录失败'
    ElMessage.error(message.value)
  }
})
</script>

<template>
  <div class="sso-page">
    <el-card class="sso-card">
      <h2>统一认证</h2>
      <p>{{ message }}</p>
      <el-button @click="redirectToSso">重新发起 SSO</el-button>
    </el-card>
  </div>
</template>

<style scoped>
.sso-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}
.sso-card {
  width: 360px;
  text-align: center;
}
</style>
