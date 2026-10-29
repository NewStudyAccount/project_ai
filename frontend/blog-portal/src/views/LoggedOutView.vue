<template>
  <div class="logged-out-page">
    <h2>已退出登录</h2>
    <p>统一登录会话已结束。</p>
    <el-button type="primary" @click="goHome">返回首页</el-button>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goHome() {
  void router.push('/')
}
</script>

<style scoped>
.logged-out-page {
  display: grid;
  min-height: 60vh;
  place-content: center;
  justify-items: center;
  gap: 12px;
}
</style>
