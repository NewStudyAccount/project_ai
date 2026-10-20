<template>
  <CrudPage ref="crudRef" :schema="schema" @custom-action="onCustomAction">
    <template #col-code="{ row }">
      <div class="name">{{ row.code }}</div>
      <div class="key">{{ row.name }}</div>
    </template>
    <template #col-providerType="{ row }">
      <span class="pill pill-type">{{ row.providerType }}</span>
    </template>
    <template #col-endpoint="{ row }">
      <div class="ellipsis" :title="row.endpoint">{{ row.endpoint || '—' }}</div>
      <div class="key">
        bucket: {{ row.bucket || '—' }}
        <template v-if="row.region"> · region: {{ row.region }}</template>
        · {{ row.pathStyle === 1 ? 'path-style' : 'virtual-hosted' }}
      </div>
    </template>
    <template #col-secretKeyMasked="{ row }">
      <span class="masked">{{ row.secretKeyMasked || '—' }}</span>
    </template>
    <template #col-status="{ row }">
      <span :class="['status-pill', row.status === 1 ? 'is-on' : 'is-off']">
        {{ row.status === 1 ? '启用' : '停用' }}
      </span>
      <span v-if="row.isDefault === 1" class="pill pill-default">默认</span>
    </template>
  </CrudPage>

  <el-dialog v-model="dialogVisible" :title="editing ? '编辑服务商' : '新建服务商'" width="640px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" class="provider-form">
      <el-form-item label="编码 code" prop="code">
        <el-input v-model="form.code" :disabled="editing" placeholder="小写连字符，如 minio-local" />
      </el-form-item>
      <el-form-item label="名称 name" prop="name">
        <el-input v-model="form.name" placeholder="本地 MinIO" />
      </el-form-item>
      <el-form-item label="类型" prop="providerType">
        <el-select v-model="form.providerType" style="width: 100%" @change="onTypeChange">
          <el-option v-for="t in PROVIDER_TYPES" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="region" prop="region">
        <el-input
          v-model="form.region"
          :placeholder="regionRequired ? '必填，如 ap-southeast-1' : '可空（如 MinIO）'"
        />
      </el-form-item>
      <el-form-item label="endpoint" prop="endpoint">
        <el-input v-model="form.endpoint" placeholder="https://… 或 http://ip:port" />
      </el-form-item>
      <el-form-item label="public_base_url" prop="publicBaseUrl">
        <el-input v-model="form.publicBaseUrl" placeholder="http://192.168.1.10:9000/blog" />
        <div class="field-hint">公开读基址，无尾斜杠</div>
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
      <el-form-item label="secret_key" prop="secretKey">
        <el-input
          v-model="form.secretKey"
          type="password"
          autocomplete="new-password"
          :placeholder="editing ? '留空不修改' : '必填'"
        />
        <div class="field-hint">只写 / 留空不改；列表仅回显掩码，不回显明文</div>
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
      <el-form-item label="备注">
        <el-input v-model="form.description" type="textarea" :rows="2" placeholder="可选" />
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-foot">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import CrudPage from '@/components/common/CrudPage.vue'
import { storageProviderApi } from '@/api/storageProvider'
import { buildStorageProviderSchema } from './storage-provider.config'
import { PROVIDER_TYPES, providerTypeDefaults, type StorageProviderInput, type StorageProviderVO } from '@/types'

type CrudPageExpose = { reload: () => Promise<void>; search: () => void }

const schema = buildStorageProviderSchema()
const crudRef = ref<CrudPageExpose | null>(null)

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
    ElMessage.success({ message: editing.value ? '已更新（SK 加密落库）' : '已新建（SK 加密落库）', duration: 2000 })
    dialogVisible.value = false
    await crudRef.value?.reload()
  } finally {
    saving.value = false
  }
}

async function testConn(row: StorageProviderVO) {
  try {
    await storageProviderApi.testConnection(row.id)
    ElMessage.success({ message: '测试连通成功', duration: 2000 })
  } catch {
    // request.ts 已统一 ElMessage.error(result.msg)，失败原因由后端 msg 返回
  }
}

/** 新建/编辑与测试连通经 custom-action 扩展点接线，请求参数与结果处理保持现状 */
function onCustomAction(key: string, row?: StorageProviderVO) {
  if (key === 'create') {
    openCreate()
    return
  }
  if (!row) return
  if (key === 'update') {
    openEdit(row)
  } else if (key === 'test') {
    void testConn(row)
  }
}
</script>

<style scoped>
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
  background: var(--pill-cyan-bg);
  color: var(--pill-cyan-fg);
}
.pill-default {
  background: var(--primary-light);
  color: var(--primary-hover);
  border: 1px solid var(--primary);
  margin-left: 4px;
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
.field-hint {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
}
</style>
