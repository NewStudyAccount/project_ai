<script setup lang="ts">
defineProps<{ title: string }>()
</script>

<template>
  <section class="side-card">
    <h4>{{ title }}</h4>
    <slot />
  </section>
</template>

<style scoped>
.side-card {
  background: var(--bg-surface);
  border-radius: var(--page-radius);
  box-shadow: var(--shadow-card);
  padding: 18px 20px;
  margin-bottom: 16px;
}
h4 {
  margin: 0 0 13px;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-primary);
}
h4::before {
  content: '';
  width: 4px;
  height: 14px;
  border-radius: 2px;
  background: var(--accent-gradient);
}
</style>
