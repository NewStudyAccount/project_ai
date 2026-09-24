<template>
  <div class="login-page">
    <el-card class="login-card">
      <template #header>
        <div class="card-header">认证运营后台</div>
      </template>
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
        <el-button class="login-btn" type="primary" native-type="submit" :loading="loading" @click="onSubmit">
          登录
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/login'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)

const form = reactive({ username: '', password: '' })

async function onSubmit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const outcome = await login(form.username, form.password)
    if (!outcome.ok) {
      ElMessage.error(outcome.message || '用户名或密码错误')
      return
    }
    auth.reset()
    const redirect = route.query.redirect ? String(route.query.redirect) : '/dashboard'
    await router.push(redirect.startsWith('/') ? redirect : '/dashboard')
  } finally {
    loading.value = false
  }
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

.login-btn {
  width: 100%;
}
</style>
