<template>
  <div class="page-card">
    <div class="page-head">
      <h2 class="title">存储服务商</h2>
      <span class="sub">配置页 · CRUD / 连通性 / 启停 / 默认项</span>
      <span class="spacer"></span>
      <el-button
        v-if="hasPermission('blog:storage-provider:create')"
        type="primary"
        @click="openCreate"
      >新建服务商</el-button>
    </div>

    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="编码 / 名称关键字"
        clearable
        style="width: 200px"
        @keyup.enter="onSearch"
      />
      <el-select
        v-model="query.providerType"
        placeholder="类型：全部"
        clearable
        style="width: 160px"
        @change="onSearch"
      >
        <el-option
          v-for="t in PROVIDER_TYPES"
          :key="t.value"
          :label="t.label"
          :value="t.value"
        />
      </el-select>
      <el-select
        v-model="query.status"
        placeholder="状态：全部"
        clearable
        style="width: 140px"
        @change="onSearch"
      >
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-button type="primary" plain @click="onSearch">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <el-table v-if="records.length" :data="records" border>
      <el-table-column label="编码 / 名称" min-width="180">
        <template #default="{ row }">
          <div class="name">{{ row.code }}</div>
          <div class="key">{{ row.name }}</div>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="120">
        <template #default="{ row }">
          <span class="pill pill-type">{{ row.providerType }}</span>
        </template>
      </el-table-column>
      <el-table-column label="endpoint / 桶" min-width="240">
        <template #default="{ row }">
          <div class="ellipsis" :title="row.endpoint">{{ row.endpoint || '—' }}</div>
          <div class="key">
            bucket: {{ row.bucket || '—' }}
            <template v-if="row.region"> · region: {{ row.region }}</template>
            · {{ row.pathStyle === 1 ? 'path-style' : 'virtual-hosted' }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="公开基址" min-width="200">
        <template #default="{ row }">
          <div class="ellipsis" :title="row.publicBaseUrl">{{ row.publicBaseUrl || '—' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="密钥" width="120">
        <template #default="{ row }">
          <span class="masked">{{ row.secretKeyMasked || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="140">
        <template #default="{ row }">
          <span :class="['status-pill', row.status === 1 ? 'is-on' : 'is-off']">
            {{ row.status === 1 ? '启用' : '停用' }}
          </span>
          <span v-if="row.isDefault === 1" class="pill pill-default">默认</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="hasPermission('blog:storage-provider:test')"
            link
            type="primary"
            @click="testConn(row)"
          >测试</el-button>
          <el-button
            v-if="hasPermission('blog:storage-provider:update')"
            link
            type="primary"
            @click="openEdit(row)"
          >编辑</el-button>
          <el-button
            v-if="hasPermission('blog:storage-provider:update') && row.isDefault !== 1"
            link
            type="primary"
            @click="setDefault(row)"
          >设默认</el-button>
          <el-button
            v-if="hasPermission('blog:storage-provider:update')"
            link
            :type="row.status === 1 ? 'warning' : 'success'"
            @click="toggleStatus(row)"
          >{{ row.status === 1 ? '停用' : '启用' }}</el-button>
          <el-button
            v-if="hasPermission('blog:storage-provider:delete')"
            link
            type="danger"
            @click="remove(row)"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else description="暂无服务商" />

    <el-pagination
      v-model:current-page="query.current"
      v-model:page-size="query.size"
      :total="total"
      layout="total, sizes, prev, pager, next"
      @current-change="load"
      @size-change="load"
    />

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑服务商' : '新建服务商'"
      width="620px"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="provider-form"
      >
        <div class="form-grid">
          <el-form-item label="编码 code" prop="code">
            <el-input
              v-model="form.code"
              :disabled="editing"
              placeholder="小写连字符，如 minio-local"
            />
          </el-form-item>
          <el-form-item label="名称 name" prop="name">
            <el-input v-model="form.name" placeholder="本地 MinIO" />
          </el-form-item>
          <el-form-item label="类型 provider_type" prop="providerType">
            <el-select
              v-model="form.providerType"
              style="width: 100%"
              @change="onTypeChange"
            >
              <el-option
                v-for="t in PROVIDER_TYPES"
                :key="t.value"
                :label="t.label"
                :value="t.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="region" prop="region">
            <el-input
              v-model="form.region"
              :placeholder="regionRequired ? '必填，如 ap-southeast-1' : '可空（如 MinIO）'"
            />
          </el-form-item>
          <el-form-item label="endpoint" prop="endpoint" class="full">
            <el-input v-model="form.endpoint" placeholder="https://… 或 http://ip:port" />
          </el-form-item>
          <el-form-item label="public_base_url（公开读基址，无尾斜杠）" prop="publicBaseUrl" class="full">
            <el-input
              v-model="form.publicBaseUrl"
              placeholder="http://192.168.1.10:9000/blog"
            />
          </el-form-item>
          <el-form-item label="bucket" prop="bucket">
            <el-input v-model="form.bucket" placeholder="blog" />
          </el-form-item>
          <el-form-item label="path_style">
            <div class="switch-line">
              <el-switch v-model="pathStyleBool" />
              <span class="switch-hint">{{ pathStyleBool ? 'path-style' : 'virtual-hosted' }}</span>
            </div>
          </el-form-item>
          <el-form-item label="access_key" prop="accessKey">
            <el-input v-model="form.accessKey" placeholder="AK" autocomplete="off" />
          </el-form-item>
          <el-form-item label="secret_key（只写 / 留空不改）" prop="secretKey">
            <el-input
              v-model="form.secretKey"
              type="password"
              autocomplete="new-password"
              :placeholder="editing ? '留空不修改' : '必填'"
            />
          </el-form-item>
          <el-form-item label="状态">
            <div class="switch-line">
              <el-switch v-model="statusBool" />
              <span class="switch-hint">{{ statusBool ? '启用' : '停用' }}</span>
            </div>
          </el-form-item>
          <el-form-item label="默认服务商">
            <div class="switch-line">
              <el-switch v-model="isDefaultBool" @change="onDefaultChange" />
              <span class="switch-hint">仅影响新上传</span>
            </div>
          </el-form-item>
          <el-form-item label="备注" class="full">
            <el-input v-model="form.description" type="textarea" :rows="2" placeholder="可选" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { storageProviderApi } from '@/api/storageProvider'
import { hasPermission } from '@/utils/permission'
import { PROVIDER_TYPES, providerTypeDefaults, type StorageProviderInput, type StorageProviderVO } from '@/types'

const records = ref<StorageProviderVO[]>([])
const total = ref(0)
const query = reactive<{ current: number; size: number; keyword: string; providerType: string; status: number | '' }>({
  current: 1,
  size: 10,
  keyword: '',
  providerType: '',
  status: '',
})

const dialogVisible = ref(false)
const editing = ref(false)
const editingId = ref('')
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<StorageProviderInput>(emptyForm())

const pathStyleBool = computed({
  get: () => form.pathStyle === 1,
  set: (v: boolean) => (form.pathStyle = v ? 1 : 0),
})
const statusBool = computed({
  get: () => form.status === 1,
  set: (v: boolean) => (form.status = v ? 1 : 0),
})
const isDefaultBool = computed({
  get: () => form.isDefault === 1,
  set: (v: boolean) => (form.isDefault = v ? 1 : 0),
})

const regionRequired = computed(() => providerTypeDefaults(form.providerType).regionRequired)

const rules: FormRules = {
  code: [
    { required: true, message: '请输入编码', trigger: 'blur' },
    { pattern: /^[a-z0-9]+(-[a-z0-9]+)*$/, message: '小写字母/数字/连字符', trigger: 'blur' },
  ],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  providerType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  endpoint: [
    { required: true, message: '请输入 endpoint', trigger: 'blur' },
    { pattern: /^https?:\/\//, message: '需以 http:// 或 https:// 开头', trigger: 'blur' },
  ],
  region: [
    {
      validator: (_rule, _value, callback) => {
        if (regionRequired.value && !form.region) {
          callback(new Error('该类型 region 必填'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  bucket: [{ required: true, message: '请输入 bucket', trigger: 'blur' }],
  accessKey: [{ required: true, message: '请输入 access_key', trigger: 'blur' }],
  publicBaseUrl: [
    { required: true, message: '请输入公开读基址', trigger: 'blur' },
    { pattern: /^https?:\/\//, message: '需以 http:// 或 https:// 开头', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value && value.endsWith('/')) callback(new Error('请去掉尾斜杠'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
  secretKey: [
    {
      validator: (_rule, _value, callback) => {
        if (!editing.value && !form.secretKey) callback(new Error('新建时 secret_key 必填'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

function emptyForm(): StorageProviderInput {
  return {
    code: '',
    name: '',
    providerType: 'MINIO',
    endpoint: '',
    region: '',
    bucket: '',
    accessKey: '',
    secretKey: '',
    pathStyle: 1,
    publicBaseUrl: '',
    status: 1,
    isDefault: 0,
    description: '',
  }
}

async function load() {
  const page = await storageProviderApi.page({
    current: query.current,
    size: query.size,
    keyword: query.keyword || undefined,
    providerType: query.providerType || undefined,
    status: query.status === '' ? undefined : query.status,
  })
  records.value = page.records
  total.value = page.total
}

function onSearch() {
  query.current = 1
  load()
}

function reset() {
  query.keyword = ''
  query.providerType = ''
  query.status = ''
  query.current = 1
  load()
}

function onTypeChange(value: string) {
  const def = providerTypeDefaults(value)
  form.pathStyle = def.pathStyle
}

async function onDefaultChange(checked: boolean | string | number) {
  if (!checked) return
  try {
    await ElMessageBox.confirm(
      '设为默认仅影响新上传，历史文件仍按其 storage_code 回源原服务商。是否继续？',
      '设为默认',
      { type: 'warning' },
    )
  } catch {
    isDefaultBool.value = false
  }
}

function openCreate() {
  editing.value = false
  editingId.value = ''
  Object.assign(form, emptyForm())
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openEdit(row: StorageProviderVO) {
  editing.value = true
  editingId.value = row.id
  Object.assign(form, {
    code: row.code,
    name: row.name,
    providerType: row.providerType,
    endpoint: row.endpoint,
    region: row.region,
    bucket: row.bucket,
    accessKey: row.accessKey,
    secretKey: '',
    pathStyle: row.pathStyle,
    publicBaseUrl: row.publicBaseUrl,
    status: row.status,
    isDefault: row.isDefault,
    description: row.description,
  })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function save() {
  if (!formRef.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    const payload: StorageProviderInput = { ...form }
    if (editing.value && !payload.secretKey) {
      delete payload.secretKey
    }
    if (editing.value) {
      await storageProviderApi.update(editingId.value, payload)
    } else {
      await storageProviderApi.create(payload)
    }
    ElMessage.success(editing.value ? '已更新（SK 加密落库）' : '已新建（SK 加密落库）')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function testConn(row: StorageProviderVO) {
  try {
    await storageProviderApi.testConnection(row.id)
    ElMessage.success('测试连通成功')
  } catch {
    // request.ts 已统一 ElMessage.error(result.msg)，失败原因由后端 msg 返回
  }
}

async function setDefault(row: StorageProviderVO) {
  try {
    await ElMessageBox.confirm(
      '设为默认仅影响新上传，历史文件仍按其 storage_code 回源原服务商。是否继续？',
      '设为默认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  await storageProviderApi.setDefault(row.id)
  ElMessage.success('已设为默认')
  await load()
}

async function toggleStatus(row: StorageProviderVO) {
  const next = row.status === 1 ? 0 : 1
  if (next === 0) {
    try {
      await ElMessageBox.confirm(
        '停用后禁止新上传，存量文件仍可读 URL/逻辑删除。是否继续？',
        '停用服务商',
        { type: 'warning' },
      )
    } catch {
      return
    }
  }
  await storageProviderApi.updateStatus(row.id, { status: next })
  ElMessage.success(next === 1 ? '已启用' : '已停用')
  await load()
}

async function remove(row: StorageProviderVO) {
  try {
    await ElMessageBox.confirm(
      `确认删除服务商「${row.code}」？无活跃文件引用时才允许删除。`,
      '删除服务商',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await storageProviderApi.delete(row.id)
    ElMessage.success('已删除')
    await load()
  } catch {
    // 后端删除保护（203084）由 request.ts 统一 ElMessage.error(msg) 展示建议停用
  }
}

import { onMounted } from 'vue'
onMounted(load)
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}
.page-head .title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}
.page-head .sub {
  font-size: 12px;
  color: var(--text-secondary);
}
.page-head .spacer {
  flex: 1;
}
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  flex-wrap: wrap;
  align-items: center;
}
.name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}
.key {
  font-size: 11px;
  color: var(--text-secondary);
  margin-top: 2px;
}
.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--text-regular);
}
.masked {
  font-size: 12px;
  color: var(--text-secondary);
  letter-spacing: 0.05em;
}
.pill {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
}
.pill-type {
  background: #e0f2fe;
  color: #0369a1;
}
.pill-default {
  background: #f0f9ff;
  color: #0c4a6e;
  border: 1px solid var(--primary);
  margin-left: 4px;
}
.provider-form .form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}
.provider-form .full {
  grid-column: 1 / -1;
}
.switch-line {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 32px;
}
.switch-hint {
  font-size: 12px;
  color: var(--text-secondary);
}
</style>
