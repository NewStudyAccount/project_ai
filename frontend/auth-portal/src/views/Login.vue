<template>
  <div class="login-page">
    <el-card class="login-card">
      <template #header>
        <div class="card-header">
          <span>统一认证中心</span>
        </div>
      </template>
      <p class="subtitle">请使用账号密码登录</p>
      <el-form :model="form" label-position="top" @submit.prevent="onSubmit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" name="username" autocomplete="username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            name="password"
            type="password"
            autocomplete="current-password"
            show-password
            placeholder="请输入密码"
          />
        </el-form-item>
        <el-button class="login-btn" type="primary" native-type="submit" :loading="auth.loading" @click="onSubmit">
          登录
        </el-button>
      </el-form>
      <el-button class="logout-link" link type="info" @click="onUnifiedLogout">退出统一登录</el-button>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { resolveReturnUrl, afterLoginRedirect } from '@/utils/redirect'

const route = useRoute()
const auth = useAuthStore()
const returnUrl = resolveReturnUrl(route.query.return_url ? String(route.query.return_url) : null)

const form = reactive({ username: '', password: '' })

async function onSubmit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  const outcome = await auth.login(form.username, form.password)
  if (outcome.ok) {
    afterLoginRedirect(returnUrl)
    return
  }
  ElMessage.error(outcome.message || '用户名或密码错误')
}

/** 统一登出入口：经 IdP /connect/logout，禁止仅清本地。 */
function onUnifiedLogout() {
  const postLogout = `${window.location.origin}/`
  window.location.assign(`/connect/logout?post_logout_redirect_uri=${encodeURIComponent(postLogout)}`)
}
</script>

<style scoped>
.login-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
}

.login-card {
  width: 380px;
}

.card-header {
  font-size: 18px;
  font-weight: 600;
  text-align: center;
}

.subtitle {
  margin: 0 0 16px;
  color: #909399;
  text-align: center;
}

.login-btn {
  width: 100%;
}

.logout-link {
  width: 100%;
  margin-top: 12px;
}
</style>
