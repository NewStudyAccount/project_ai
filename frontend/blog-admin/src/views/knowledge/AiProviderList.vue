<template>
  <div class="page">
    <div class="toolbar">
      <el-select v-model="query.kind" placeholder="类型" clearable style="width: 150px">
        <el-option label="Embedding" :value="1" />
        <el-option label="LLM" :value="2" />
        <el-option label="兼容" :value="3" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
      <span class="spacer" />
      <el-button
        v-if="hasPermission('blog:ai-provider:create')"
        type="primary"
        @click="openDialog()"
      >新建服务商</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column label="编码 / 名称" min-width="180">
        <template #default="{ row }">
          <div class="title">{{ row.code }}</div>
          <div class="key">{{ row.name }}</div>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <span class="pill">{{ KIND[row.kind] || row.kind }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="type" label="服务商" width="120" />
      <el-table-column prop="model" label="模型" min-width="150" show-overflow-tooltip />
      <el-table-column label="维度" width="80">
        <template #default="{ row }">{{ row.dim || '—' }}</template>
      </el-table-column>
      <el-table-column label="API Key" width="140">
        <template #default="{ row }">
          <span class="masked">{{ row.apiKeyMasked || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <span :class="['status-pill', row.status === 1 ? 'is-on' : 'is-off']">
            {{ row.status === 1 ? '启用' : '停用' }}
          </span>
          <span v-if="row.isDefault === 1" class="pill pill-default">默认</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="hasPermission('blog:ai-provider:test')"
            link
            type="primary"
            :loading="testing === row.id"
            @click="onTest(row)"
          >测试</el-button>
          <el-button
            v-if="hasPermission('blog:ai-provider:update')"
            link
            type="primary"
            @click="openDialog(row)"
          >编辑</el-button>
          <el-button
            v-if="hasPermission('blog:ai-provider:delete')"
            link
            type="danger"
            :disabled="row.isDefault === 1 && row.status === 1"
            @click="onDelete(row)"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      layout="total, prev, pager, next"
      :total="total"
      :current-page="query.current"
      :page-size="query.size"
      @current-change="(p: number) => { query.current = p; load() }"
    />

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑服务商' : '新建服务商'" width="560px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="编码 code">
          <el-input v-model="form.code" placeholder="如 siliconflow-embed" />
        </el-form-item>
        <el-form-item label="类型 kind">
          <el-select v-model="form.kind" style="width: 100%">
            <el-option label="Embedding" :value="1" />
            <el-option label="LLM" :value="2" />
            <el-option label="兼容" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务商 type">
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="t in ['SILICONFLOW', 'DASHSCOPE', 'ZHIPU', 'OPENAI']" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="https://api.siliconflow.cn/v1" />
        </el-form-item>
        <el-form-item label="模型 model">
          <el-input v-model="form.model" placeholder="BAAI/bge-m3 / Qwen/Qwen3-8B" />
        </el-form-item>
        <el-form-item label="向量维度 dim">
          <el-input-number v-model="form.dim" :min="0" :step="64" />
          <div class="field-hint">Embedding 必填且须等于 1024（与索引一致，否则 203114）</div>
        </el-form-item>
        <el-form-item label="api_key">
          <el-input v-model="form.apiKey" type="password" autocomplete="new-password"
            :placeholder="editing ? '留空不修改' : 'sk-…'" />
          <div class="field-hint">只写；列表仅回显掩码，明文永不回显</div>
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="defaultBool" />
          <span class="field-hint" style="margin-left: 8px">同类型（kind）仅一个生效默认</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="statusBool" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { knowledgeApi, type AiProvider, type AiProviderInput } from '@/api/knowledge'
import { hasPermission } from '@/utils/permission'

const KIND: Record<number, string> = { 1: 'Embedding', 2: 'LLM', 3: '兼容' }

const loading = ref(false)
const saving = ref(false)
const testing = ref('')
const rows = ref<AiProvider[]>([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, kind: undefined as number | undefined })

const dialogVisible = ref(false)
const editing = ref(false)
const editingId = ref('')
const form = reactive<AiProviderInput>({
  code: '', kind: 1, type: 'SILICONFLOW', name: '', baseUrl: '', model: '', dim: 0, apiKey: '', isDefault: 0, status: 1,
})
const defaultBool = computed({
  get: () => form.isDefault === 1,
  set: (v: boolean) => (form.isDefault = v ? 1 : 0),
})
const statusBool = computed({
  get: () => form.status === 1,
  set: (v: boolean) => (form.status = v ? 1 : 0),
})

async function load() {
  loading.value = true
  try {
    const page = await knowledgeApi.providersPage({ ...query })
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function openDialog(row?: AiProvider) {
  editing.value = !!row
  editingId.value = row?.id ?? ''
  Object.assign(form, {
    code: row?.code ?? '',
    kind: row?.kind ?? 1,
    type: row?.type ?? 'SILICONFLOW',
    name: row?.name ?? '',
    baseUrl: row?.baseUrl ?? '',
    model: row?.model ?? '',
    dim: row?.dim ?? 0,
    apiKey: '',
    isDefault: row?.isDefault ?? 0,
    status: row?.status ?? 1,
  })
  dialogVisible.value = true
}

async function save() {
  saving.value = true
  try {
    if (editing.value) {
      await knowledgeApi.updateProvider(editingId.value, { ...form })
      ElMessage.success('已保存')
    } else {
      await knowledgeApi.createProvider({ ...form })
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onTest(row: AiProvider) {
  testing.value = row.id
  try {
    const r = await knowledgeApi.testProvider(row.id)
    ElMessage.success(`连通成功：${r.latencyMs}ms`)
  } finally {
    testing.value = ''
  }
}

async function onDelete(row: AiProvider) {
  await ElMessageBox.confirm(`删除服务商「${row.code}」？`, '确认删除', { type: 'warning' })
  await knowledgeApi.deleteProvider(row.id)
  ElMessage.success('已删除')
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
.key { color: var(--el-text-color-secondary); font-size: 12px; }
.pill { border: 1px solid var(--el-border-color); border-radius: 10px; padding: 1px 8px; font-size: 12px; }
.pill-default { color: var(--el-color-primary); margin-left: 4px; }
.masked { font-family: monospace; }
.status-pill { border-radius: 10px; padding: 1px 8px; font-size: 12px; }
.status-pill.is-on { background: var(--el-color-success-light-8); color: var(--el-color-success); }
.status-pill.is-off { background: var(--el-color-danger-light-8); color: var(--el-color-danger); }
.field-hint { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
