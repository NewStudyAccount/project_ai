<template>
  <div class="page">
    <div class="toolbar">
      <el-select v-model="query.taskType" placeholder="任务类型" clearable style="width: 160px">
        <el-option v-for="(label, code) in TASK_TYPE" :key="code" :label="label" :value="code" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 150px">
        <el-option v-for="(label, code) in TASK_STATUS" :key="code" :label="label" :value="Number(code)" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
      <span class="spacer" />
      <el-button @click="$router.push('/knowledge/docs')">返回文档列表</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column label="任务" min-width="160">
        <template #default="{ row }">
          <div class="title">{{ TASK_TYPE[row.taskType] || row.taskType }}</div>
          <div class="key">{{ row.id }}</div>
        </template>
      </el-table-column>
      <el-table-column label="目标文档" min-width="180">
        <template #default="{ row }">
          <span v-if="row.docTitle" class="title-sm">{{ row.docTitle }}</span>
          <span v-else class="key">全局任务</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <span :class="['status-pill', taskClass(row.status)]">{{ TASK_STATUS[row.status] }}</span>
        </template>
      </el-table-column>
      <el-table-column label="重试" width="80">
        <template #default="{ row }">{{ row.retryCount }}/{{ row.maxRetry }}</template>
      </el-table-column>
      <el-table-column prop="failReason" label="失败原因 / 摘要" min-width="200" show-overflow-tooltip />
      <el-table-column prop="startTime" label="开始" width="170" />
      <el-table-column prop="endTime" label="结束" width="170" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 3 || row.status === 4"
            link
            type="primary"
            @click="onRerun(row)"
          >重跑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      layout="total, prev, pager, next, sizes"
      :total="total"
      :current-page="query.current"
      :page-size="query.size"
      @current-change="(p: number) => { query.current = p; load() }"
      @size-change="(s: number) => { query.size = s; query.current = 1; load() }"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { knowledgeApi, type KnowledgeTask } from '@/api/knowledge'

const TASK_TYPE: Record<string, string> = {
  INGEST: '入库+向量化',
  REVECTORIZE: '单文档重建',
  INVALIDATE: '失效同步',
  FULL_REINDEX: '全量重建',
  SYNC_ARTICLES: '对账补偿',
}
const TASK_STATUS: Record<number, string> = {
  0: '待执行', 1: '执行中', 2: '成功', 3: '失败', 4: '已超限',
}

const loading = ref(false)
const rows = ref<KnowledgeTask[]>([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, taskType: '', status: undefined as number | undefined })

async function load() {
  loading.value = true
  try {
    const page = await knowledgeApi.tasksPage({ ...query })
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function taskClass(status: number): string {
  if (status === 2) return 'is-on'
  if (status === 3 || status === 4) return 'is-off'
  return 'is-pending'
}

async function onRerun(row: KnowledgeTask) {
  await ElMessageBox.confirm(`重跑任务 ${row.id}（${TASK_TYPE[row.taskType]}）？`, '确认重跑')
  await knowledgeApi.rerunTask(row.id)
  ElMessage.success('已重置并重新投递')
  load()
}

onMounted(load)
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.toolbar { display: flex; gap: 8px; align-items: center; }
.spacer { flex: 1; }
.pager { justify-content: flex-end; }
.title { font-weight: 500; }
.title-sm { font-weight: 500; }
.key { color: var(--el-text-color-secondary); font-size: 12px; }
.status-pill { border-radius: 10px; padding: 1px 8px; font-size: 12px; }
.status-pill.is-on { background: var(--el-color-success-light-8); color: var(--el-color-success); }
.status-pill.is-off { background: var(--el-color-danger-light-8); color: var(--el-color-danger); }
.status-pill.is-pending { background: var(--el-color-warning-light-8); color: var(--el-color-warning); }
</style>
