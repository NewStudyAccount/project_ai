<template>
  <div class="page" v-loading="loading">
    <div class="cards">
      <div class="card">
        <div class="num">{{ stats?.docTotal ?? '—' }}</div>
        <div class="label">知识文档总数</div>
      </div>
      <div class="card">
        <div class="num">{{ stats?.chunkTotal ?? '—' }}</div>
        <div class="label">知识块总数</div>
      </div>
      <div class="card">
        <div class="num">{{ readyRatio }}</div>
        <div class="label">块 READY 占比</div>
      </div>
      <div class="card">
        <div class="num" :class="stats?.esHealthy ? 'ok' : 'danger'">
          {{ stats?.esHealthy ? (stats?.esDocCount ?? '—') : '不可用' }}
        </div>
        <div class="label">ES 文档数 / 健康度</div>
      </div>
      <div class="card">
        <div class="num" :class="(stats?.taskFailed ?? 0) > 0 ? 'danger' : ''">{{ stats?.taskFailed ?? '—' }}</div>
        <div class="label">失败任务数</div>
      </div>
    </div>

    <el-card>
      <template #header>文档状态分布</template>
      <div class="bars">
        <div v-for="(label, key) in STATUS_LABEL" :key="key" class="bar-row">
          <span class="bar-label">{{ label }}</span>
          <el-progress
            :percentage="percent(stats?.docByStatus?.[key] ?? 0)"
            :stroke-width="14"
            format=""
          />
          <span class="bar-num">{{ stats?.docByStatus?.[key] ?? 0 }}</span>
        </div>
      </div>
    </el-card>

    <el-card>
      <template #header>云 API 调用计数（进程内近似）</template>
      <p class="line">Embedding 调用：{{ stats?.providerCalls?.embed ?? 0 }}</p>
      <p class="line">LLM 对话调用：{{ stats?.providerCalls?.chat ?? 0 }}</p>
    </el-card>

    <div class="actions">
      <el-button @click="load">刷新</el-button>
      <el-button @click="$router.push('/knowledge/tasks')">任务列表</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { knowledgeApi, type KnowledgeStats } from '@/api/knowledge'

const STATUS_LABEL: Record<string, string> = {
  pending: '待向量化',
  vectorizing: '向量化中',
  ready: '可用',
  failed: '失败',
  invalid: '已失效',
}

const loading = ref(false)
const stats = ref<KnowledgeStats | null>(null)
const readyRatio = computed(() =>
  stats.value ? Math.round(stats.value.chunkReadyRatio * 100) + '%' : '—',
)

function percent(n: number): number {
  const total = stats.value?.docTotal ?? 0
  return total === 0 ? 0 : Math.round((n / total) * 100)
}

async function load() {
  loading.value = true
  try {
    stats.value = await knowledgeApi.stats()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.cards { display: flex; gap: 12px; }
.card { border: 1px solid var(--el-border-color-light); border-radius: 8px; padding: 14px 20px; min-width: 130px; }
.num { font-size: 22px; font-weight: 600; }
.num.ok { color: var(--el-color-success); }
.num.danger { color: var(--el-color-danger); }
.label { color: var(--el-text-color-secondary); font-size: 12px; }
.bars { display: flex; flex-direction: column; gap: 8px; }
.bar-row { display: flex; align-items: center; gap: 12px; }
.bar-label { width: 80px; }
.bar-num { width: 40px; text-align: right; color: var(--el-text-color-secondary); }
.line { color: var(--el-text-color-regular); }
.actions { display: flex; gap: 8px; }
</style>
