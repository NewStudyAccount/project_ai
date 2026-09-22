<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <div class="portal">
    <header class="header">
      <strong>Blog Portal</strong>
    </header>
    <main class="main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.portal {
  min-height: 100vh;
  background: #fafafa;
}
.header {
  padding: 16px 24px;
  background: #fff;
  border-bottom: 1px solid #eee;
}
.main {
  max-width: 880px;
  margin: 0 auto;
  padding: 24px 16px;
}
</style>
