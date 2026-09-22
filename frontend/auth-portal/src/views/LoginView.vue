<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { login, logout } from '@/api/auth'
import { getAccessToken } from '@/utils/token'

const route = useRoute()
const router = useRouter()

const formRef = ref<FormInstance>()
const loading = ref(false)
const loggedIn = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, min: 6, message: '请输入至少 6 位密码', trigger: 'blur' }],
}

function queryStr(key: string): string {
  const v = route.query[key]
  return typeof v === 'string' ? v : ''
}

onMounted(() => {
  loggedIn.value = Boolean(getAccessToken())
})

async function onSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  loading.value = true
  try {
    await login(form.username, form.password)
    form.password = ''
    loggedIn.value = true
    ElMessage.success('登录成功')
    await afterLogin()
  } catch (e) {
    const msg = e instanceof Error ? e.message : '登录失败'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}

async function afterLogin() {
  const returnUrl = queryStr('return_url')
  const clientId = queryStr('client_id')
  if (returnUrl && clientId) {
    const authorize = `/auth/sso/authorize?client_id=${encodeURIComponent(clientId)}&return_url=${encodeURIComponent(returnUrl)}`
    window.location.href = authorize
    return
  }
  await router.replace({ name: 'login', query: { ok: '1' } })
}

async function onLogout() {
  await logout()
  loggedIn.value = false
  ElMessage.success('已登出')
}
</script>

<template>
  <div class="page">
    <el-card class="card">
      <template #header>
        <div class="card-header">
          <span>统一认证</span>
          <el-button v-if="loggedIn" text type="primary" @click="onLogout">登出</el-button>
        </div>
      </template>
      <el-form
        v-if="!loggedIn"
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="72px"
        @submit.prevent="onSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" autocomplete="username" placeholder="admin" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="请输入密码"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%">
            登录
          </el-button>
        </el-form-item>
      </el-form>
      <div v-else class="ok-box">
        <p>已登录，可进行 SSO 跳转或登出。</p>
        <p v-if="queryStr('return_url')">目标：{{ queryStr('return_url') }}</p>
      </div>
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
  width: 400px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.ok-box {
  line-height: 1.6;
}
</style>
