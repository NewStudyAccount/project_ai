<template>
  <div class="page">
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="标题搜索"
        clearable
        style="width: 220px"
        @keyup.enter="load"
      />
      <el-select v-model="query.source" placeholder="来源" clearable style="width: 140px">
        <el-option label="文章" :value="1" />
        <el-option label="手动导入" :value="2" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 150px">
        <el-option v-for="(label, code) in DOC_STATUS" :key="code" :label="label" :value="Number(code)" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
      <span class="spacer" />
      <el-button
        v-if="hasPermission('blog:knowledge:manage')"
        @click="$router.push('/knowledge/tasks')"
      >任务列表</el-button>
      <el-button
        v-if="hasPermission('blog:knowledge:manage')"
        @click="$router.push('/knowledge/stats')"
      >统计</el-button>
      <el-button
        v-if="hasPermission('blog:knowledge:reindex')"
        type="warning"
        @click="onReindex"
      >全量重建索引</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column label="标题" min-width="220">
        <template #default="{ row }">
          <div class="title">{{ row.title }}</div>
          <div class="key">
            <span v-if="row.docUrl" class="link" @click="openDoc(row.docUrl)">{{ row.docUrl }}</span>
            <span v-else-if="row.source === 2" class="pill pill-manual">站内知识</span>
            <span v-else class="key">文章 #{{ row.sourceId }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="来源" width="100">
        <template #default="{ row }">
          <span class="pill">{{ row.source === 1 ? '文章' : '手动导入' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <span :class="['status-pill', statusClass(row.status)]">
            {{ DOC_STATUS[row.status] || row.status }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="向量健康度" width="140">
        <template #default="{ row }">
          <span class="key">{{ row.readyChunkCount }}/{{ row.chunkCount }} 块</span>
        </template>
      </el-table-column>
      <el-table-column prop="embedModel" label="Embedding" width="160" show-overflow-tooltip />
      <el-table-column prop="failReason" label="失败原因" min-width="140" show-overflow-tooltip />
      <el-table-column prop="updateTime" label="更新时间" width="170" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="hasPermission('blog:knowledge:revectorize') && row.status !== 4"
            link
            type="primary"
            @click="onRevectorize(row)"
          >重建</el-button>
          <el-button
            v-if="hasPermission('blog:knowledge:delete') && row.source === 2"
            link
            type="danger"
            @click="onDelete(row)"
          >删除</el-button>
          <span v-if="row.status === 4" class="key">已失效（重新入库可恢复）</span>
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
import { knowledgeApi, type KnowledgeDoc } from '@/api/knowledge'
import { hasPermission } from '@/utils/permission'

const DOC_STATUS: Record<number, string> = {
  0: '待向量化',
  1: '向量化中',
  2: '可用',
  3: '失败',
  4: '已失效',
}

const loading = ref(false)
const rows = ref<KnowledgeDoc[]>([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, keyword: '', source: undefined as number | undefined, status: undefined as number | undefined })

async function load() {
  loading.value = true
  try {
    const page = await knowledgeApi.docsPage({ ...query })
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function statusClass(status: number): string {
  if (status === 2) return 'is-on'
  if (status === 3 || status === 4) return 'is-off'
  return 'is-pending'
}

function openDoc(url: string) {
  window.open(url, '_blank')
}

async function onRevectorize(row: KnowledgeDoc) {
  await ElMessageBox.confirm(`重建「${row.title}」？将重切+重向量化`, '确认重建')
  await knowledgeApi.revectorize(row.id)
  ElMessage.success('已受理，任务异步执行中')
  load()
}

async function onDelete(row: KnowledgeDoc) {
  await ElMessageBox.confirm(`删除「${row.title}」？逻辑删除并清除 ES 文档`, '确认删除', { type: 'warning' })
  await knowledgeApi.deleteDoc(row.id)
  ElMessage.success('已删除，ES 清理任务异步执行')
  load()
}

async function onReindex() {
  await ElMessageBox.confirm(
    '全量重建将删除 ES index 重建并重置全部向量状态，重建窗口内检索不可用。确认执行？',
    '高风险操作',
    { type: 'warning', confirmButtonText: '确认重建' },
  )
  await knowledgeApi.reindex()
  ElMessage.success('重建任务已受理（异步执行，可到任务列表查看进度）')
}

onMounted(load)
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.toolbar { display: flex; gap: 8px; align-items: center; }
.spacer { flex: 1; }
.pager { justify-content: flex-end; }
.title { font-weight: 500; }
.key { color: var(--el-text-color-secondary); font-size: 12px; }
.link { color: var(--el-color-primary); cursor: pointer; }
.pill { border: 1px solid var(--el-border-color); border-radius: 10px; padding: 1px 8px; font-size: 12px; }
.pill-manual { color: var(--el-color-success); }
.status-pill { border-radius: 10px; padding: 1px 8px; font-size: 12px; }
.status-pill.is-on { background: var(--el-color-success-light-8); color: var(--el-color-success); }
.status-pill.is-off { background: var(--el-color-danger-light-8); color: var(--el-color-danger); }
.status-pill.is-pending { background: var(--el-color-warning-light-8); color: var(--el-color-warning); }
</style>
