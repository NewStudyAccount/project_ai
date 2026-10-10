<template>
  <div class="logged-out-page">
    <el-card class="card">
      <h2>已退出登录</h2>
      <p>统一登录会话已结束。</p>
      <el-button type="primary" @click="goLogin">重新登录</el-button>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { redirectForLogin } from '@/utils/oidc'

function goLogin() {
  void redirectForLogin('/')
}
</script>

<style scoped>
.logged-out-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
}

.card {
  width: 360px;
  text-align: center;
}
</style>
