<template>
  <div>
    <el-alert type="info" title="正在通过 OIDC PKCE 登录…" :closable="false" />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { handleCallback } from '../utils/oidc'
import { useTokenStore } from '../stores/token'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const tokenStore = useTokenStore()

onMounted(async () => {
  const code = route.query.code as string
  const state = route.query.state as string
  try {
    await handleCallback(code, state)
    tokenStore.setToken(sessionStorage.getItem('auth_admin_at') || '')
    router.replace('/clients')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '登录失败')
    router.replace('/clients')
  }
})
</script>
