<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string
    description?: string
  }>(),
  { title: 'Blog', description: '' },
)
</script>

<template>
  <section class="hero">
    <div class="hero-inner">
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
      <div class="hero-actions">
        <router-link class="btn-primary" to="/articles">开始阅读</router-link>
        <router-link class="btn-ghost" to="/categories">浏览分类</router-link>
      </div>
    </div>
  </section>
</template>

<style scoped>
.hero {
  background:
    radial-gradient(
      720px 220px at 12% -40%,
      color-mix(in srgb, var(--primary) 16%, transparent),
      transparent 62%
    ),
    radial-gradient(
      560px 200px at 88% -30%,
      color-mix(in srgb, var(--accent-bar) 13%, transparent),
      transparent 60%
    ),
    var(--bg-surface);
  border-bottom: 1px solid var(--border);
}
.hero-inner {
  max-width: 1120px;
  margin: 0 auto;
  padding: 46px 24px 40px;
}
h1 {
  margin: 0 0 12px;
  font-size: 32px;
  letter-spacing: 0.5px;
  background: var(--accent-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  display: inline-block;
}
p {
  margin: 0 0 22px;
  color: var(--text-regular);
  font-size: 15px;
  max-width: 560px;
}
.hero-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}
@media (max-width: 768px) {
  .hero-inner {
    padding: 30px 16px 26px;
  }
  h1 {
    font-size: 25px;
  }
}
</style>
