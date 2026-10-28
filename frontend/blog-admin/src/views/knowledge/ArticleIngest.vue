<template>
  <div class="page">
    <div class="summary-row">
      <div class="card"><div class="num">{{ summary.total }}</div><div class="label">已发布文章</div></div>
      <div class="card"><div class="num">{{ summary.uningested }}</div><div class="label">未入库</div></div>
      <div class="card"><div class="num">{{ summary.ingested }}</div><div class="label">已入库</div></div>
      <div class="card"><div class="num warn">{{ summary.stale }}</div><div class="label">待更新</div></div>
      <div class="card"><div class="num danger">{{ summary.invalid }}</div><div class="label">已失效</div></div>
      <span class="spacer" />
      <el-button
        v-if="hasPermission('blog:knowledge:ingest')"
        type="primary"
        :loading="syncing"
        @click="onSync"
      >同步文章索引</el-button>
      <el-button
        v-if="hasPermission('blog:knowledge:ingest')"
        type="success"
        :disabled="!selected.length"
        @click="onIngest"
      >入库勾选文章（{{ selected.length }}）</el-button>
    </div>

    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="标题搜索" clearable style="width: 220px" @keyup.enter="load" />
      <el-select v-model="query.ingestState" placeholder="入库状态" clearable style="width: 150px">
        <el-option label="未入库" value="UNINGESTED" />
        <el-option label="已入库" value="INGESTED" />
        <el-option label="待更新" value="STALE" />
        <el-option label="已失效" value="INVALID" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
    </div>

    <el-alert v-if="!loading && total === 0" type="info" :closable="false"
      title="投影表为空：请先点击「同步文章索引」拉取已发布文章" />

    <el-table :data="rows" v-loading="loading" border stripe @selection-change="onSelect">
      <el-table-column type="selection" width="42" :selectable="(row: Candidate) => canSelect(row)" />
      <el-table-column label="文章" min-width="240">
        <template #default="{ row }">
          <div class="title">{{ row.title }}</div>
          <div class="key">{{ row.categoryName || '未分类' }} · {{ row.publishTime || '—' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="入库状态" width="110">
        <template #default="{ row }">
          <span :class="['state-pill', 'state-' + row.ingestState]">{{ STATE_LABEL[row.ingestState] }}</span>
        </template>
      </el-table-column>
      <el-table-column label="向量化进度" width="130">
        <template #default="{ row }">
          <span v-if="row.docId" class="key">{{ DOC_STATUS[row.docStatus ?? -1] || '—' }}</span>
          <span v-else class="key">—</span>
        </template>
      </el-table-column>
      <el-table-column label="内容指纹" width="150">
        <template #default="{ row }">
          <span class="key">{{ row.contentVersion === '0' ? '无正文' : row.contentVersion }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="lastIngestTime" label="最近入库" width="170" />
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
import {
  knowledgeApi,
  type ArticleIndexSyncResult,
  type Candidate,
  type CandidateSummary,
  type FromArticlesResult,
} from '@/api/knowledge'
import { hasPermission } from '@/utils/permission'

const STATE_LABEL: Record<string, string> = {
  UNINGESTED: '未入库',
  INGESTED: '已入库',
  STALE: '待更新',
  INVALID: '已失效',
}
const DOC_STATUS: Record<number, string> = {
  0: '待向量化', 1: '向量化中', 2: '可用', 3: '失败', 4: '已失效',
}

const loading = ref(false)
const syncing = ref(false)
const rows = ref<Candidate[]>([])
const summary = ref<CandidateSummary>({ total: 0, uningested: 0, ingested: 0, stale: 0, invalid: 0 })
const total = ref(0)
const selected = ref<Candidate[]>([])
const query = reactive({ current: 1, size: 10, keyword: '', ingestState: '' })

async function load() {
  loading.value = true
  try {
    const [page, s] = await Promise.all([
      knowledgeApi.candidates({ ...query }),
      knowledgeApi.candidatesSummary(),
    ])
    rows.value = page.records
    total.value = page.total
    summary.value = s
  } finally {
    loading.value = false
  }
}

function canSelect(row: Candidate): boolean {
  // 无正文禁选（提交时后端 203092）；已入库且未变化可重选触发跳过明细
  return row.contentVersion !== '0'
}

function onSelect(list: Candidate[]) {
  selected.value = list
}

async function onSync() {
  syncing.value = true
  try {
    const r: ArticleIndexSyncResult = await knowledgeApi.syncArticleIndex()
    ElMessage.success(
      `同步完成：拉取 ${r.pulled}，新增 ${r.inserted}，更新 ${r.updated}，移出 ${r.removed}，失效 ${r.invalidated}`,
    )
    load()
  } finally {
    syncing.value = false
  }
}

async function onIngest() {
  await ElMessageBox.confirm(
    `入库勾选的 ${selected.value.length} 篇文章？受理后异步切块/向量化。`,
    '确认入库',
  )
  const r: FromArticlesResult = await knowledgeApi.fromArticles(selected.value.map((c) => c.articleId))
  const lines = [`受理 ${r.submitted}，跳过 ${r.skipped}，拒绝 ${r.rejected}`]
  r.skippedItems.forEach((s) => lines.push(`跳过 #${s.articleId}：${s.reason === 'ALREADY_INGESTED' ? '已入库且内容未变' : '任务进行中'}`))
  r.rejectedItems.forEach((s) => lines.push(`拒绝 #${s.articleId}：${s.reason === 'NOT_PUBLISHED' ? '未发布' : s.reason === 'NO_CONTENT' ? '无正文' : '不存在'}`))
  ElMessageBox.alert(lines.join('<br/>'), '入库结果', { dangerouslyUseHTMLString: true })
  load()
}

onMounted(load)
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.summary-row { display: flex; gap: 12px; align-items: stretch; }
.card { border: 1px solid var(--el-border-color-light); border-radius: 8px; padding: 10px 18px; min-width: 90px; }
.num { font-size: 20px; font-weight: 600; }
.num.warn { color: var(--el-color-warning); }
.num.danger { color: var(--el-color-danger); }
.label { color: var(--el-text-color-secondary); font-size: 12px; }
.toolbar { display: flex; gap: 8px; }
.spacer { flex: 1; }
.pager { justify-content: flex-end; }
.title { font-weight: 500; }
.key { color: var(--el-text-color-secondary); font-size: 12px; }
.state-pill { border-radius: 10px; padding: 1px 8px; font-size: 12px; }
.state-UNINGESTED { background: var(--el-color-info-light-8); color: var(--el-color-info); }
.state-INGESTED { background: var(--el-color-success-light-8); color: var(--el-color-success); }
.state-STALE { background: var(--el-color-warning-light-8); color: var(--el-color-warning); }
.state-INVALID { background: var(--el-color-danger-light-8); color: var(--el-color-danger); }
</style>
