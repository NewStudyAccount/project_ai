<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2>用户中心管理端</h2>
      <p>壳阶段登录本地放行，真实 OIDC 随认证中心接线。</p>
      <el-button type="primary" @click="enter">进入管理端</el-button>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
function enter() {
  void router.push('/dashboard')
}
</script>

<style scoped>
.login-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
}

.login-card {
  width: 360px;
  text-align: center;
}
</style>
