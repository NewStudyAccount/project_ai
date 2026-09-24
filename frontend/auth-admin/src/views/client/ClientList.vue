<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input v-model="query.clientId" placeholder="clientId" clearable style="width: 180px" />
      <el-input v-model="query.systemCode" placeholder="系统编码" clearable style="width: 160px" />
      <el-select v-model="query.enabled" placeholder="状态" clearable style="width: 120px">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
      <el-button v-if="hasPermission('auth:client:create')" type="primary" @click="openCreate">新建</el-button>
    </div>
    <el-table :data="records" border>
      <el-table-column prop="id" label="ID" width="180" />
      <el-table-column prop="clientId" label="Client ID" width="160" />
      <el-table-column prop="clientName" label="名称" width="140" />
      <el-table-column prop="clientType" label="类型" width="110" />
      <el-table-column prop="grantTypes" label="授权类型" min-width="160" />
      <el-table-column prop="scopes" label="Scopes" width="140" />
      <el-table-column prop="systemCode" label="系统" width="100" />
      <el-table-column prop="enabled" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button v-if="hasPermission('auth:client:update')" link @click="openEdit(row)">编辑</el-button>
          <el-button v-if="hasPermission('auth:client:update')" link @click="toggleStatus(row)">
            {{ row.enabled === 1 ? '停用' : '启用' }}
          </el-button>
          <el-button v-if="hasPermission('auth:client:secret')" link @click="resetSecret(row)">重置密钥</el-button>
          <el-button v-if="hasPermission('auth:client:delete')" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="query.current"
      v-model:page-size="query.size"
      :total="total"
      layout="total, prev, pager, next"
      class="mt12"
      @current-change="load"
    />

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑客户端' : '新建客户端'" width="720px">
      <el-form :model="form" label-width="140px">
        <el-form-item label="Client ID" required>
          <el-input v-model="form.clientId" :disabled="editing" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.clientName" />
        </el-form-item>
        <el-form-item label="客户端类型">
          <el-select v-model="form.clientType">
            <el-option label="PUBLIC" value="PUBLIC" />
            <el-option label="CONFIDENTIAL" value="CONFIDENTIAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="认证方式">
          <el-select v-model="form.clientAuthMethod">
            <el-option label="NONE" value="NONE" />
            <el-option label="CLIENT_SECRET_BASIC" value="CLIENT_SECRET_BASIC" />
            <el-option label="CLIENT_SECRET_POST" value="CLIENT_SECRET_POST" />
          </el-select>
        </el-form-item>
        <el-form-item label="grant_types" required>
          <el-input v-model="form.grantTypes" placeholder="authorization_code,refresh_token" />
        </el-form-item>
        <el-form-item label="redirect_uris" required>
          <el-input v-model="form.redirectUris" type="textarea" placeholder="精确白名单，逗号分隔" />
        </el-form-item>
        <el-form-item label="scopes" required>
          <el-input v-model="form.scopes" placeholder="openid,profile" />
        </el-form-item>
        <el-form-item label="require_pkce">
          <el-switch v-model="form.requirePkce" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="require_consent">
          <el-switch v-model="form.requireConsent" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="reuse_refresh_tokens">
          <el-input-number v-model="form.reuseRefreshTokens" :min="0" :max="0" disabled />
          <span class="hint">必须为 0</span>
        </el-form-item>
        <el-form-item label="access_token_ttl_sec">
          <el-input-number v-model="form.accessTokenTtlSec" :min="300" :max="900" />
        </el-form-item>
        <el-form-item label="refresh_token_ttl_sec">
          <el-input-number v-model="form.refreshTokenTtlSec" :min="60" />
        </el-form-item>
        <el-form-item label="系统编码">
          <el-input v-model="form.systemCode" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="form.owner" />
        </el-form-item>
        <el-form-item label="环境">
          <el-input v-model="form.env" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <SecretDialog :visible="secretVisible" :secret="secret" @close="secretVisible = false" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { clientApi } from '@/api/client'
import { hasPermission } from '@/utils/permission'
import SecretDialog from '@/components/SecretDialog.vue'
import type { ClientRequest, ClientSecretVO, ClientVO } from '@/types'

const records = ref<ClientVO[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const editing = ref(false)
const editingId = ref('')
const secretVisible = ref(false)
const secret = ref<ClientSecretVO>({ id: '', clientId: '', clientSecret: '' })

const query = reactive({
  current: 1,
  size: 10,
  clientId: '',
  systemCode: '',
  enabled: undefined as number | undefined,
})

const emptyForm = (): ClientRequest => ({
  clientId: '',
  clientName: '',
  clientType: 'PUBLIC',
  clientAuthMethod: 'NONE',
  grantTypes: 'authorization_code,refresh_token',
  redirectUris: '',
  scopes: 'openid,profile',
  requirePkce: 1,
  requireConsent: 0,
  reuseRefreshTokens: 0,
  accessTokenTtlSec: 600,
  refreshTokenTtlSec: 604800,
  systemCode: '',
  owner: '',
  env: '',
  enabled: 1,
  remark: '',
})

const form = ref<ClientRequest>(emptyForm())

async function load() {
  const page = await clientApi.page(query)
  records.value = page.records
  total.value = page.total
}

function openCreate() {
  editing.value = false
  editingId.value = ''
  form.value = emptyForm()
  dialogVisible.value = true
}

function openEdit(row: ClientVO) {
  editing.value = true
  editingId.value = row.id
  form.value = {
    clientId: row.clientId,
    clientName: row.clientName,
    clientType: row.clientType,
    clientAuthMethod: row.clientAuthMethod,
    grantTypes: row.grantTypes,
    redirectUris: row.redirectUris,
    scopes: row.scopes,
    requirePkce: row.requirePkce,
    requireConsent: row.requireConsent,
    reuseRefreshTokens: 0,
    accessTokenTtlSec: row.accessTokenTtlSec,
    refreshTokenTtlSec: row.refreshTokenTtlSec,
    systemCode: row.systemCode,
    owner: row.owner,
    env: row.env,
    enabled: row.enabled,
    remark: row.remark,
  }
  dialogVisible.value = true
}

async function save() {
  if (editing.value) {
    await clientApi.update(editingId.value, form.value)
    dialogVisible.value = false
  } else {
    const created = await clientApi.create(form.value)
    dialogVisible.value = false
    if (created && created.clientSecret) {
      secret.value = created
      secretVisible.value = true
    }
  }
  await load()
}

async function toggleStatus(row: ClientVO) {
  await clientApi.updateStatus(row.id, row.enabled === 1 ? 0 : 1)
  await load()
}

async function resetSecret(row: ClientVO) {
  await ElMessageBox.confirm(`确认重置客户端 ${row.clientId} 的密钥？旧密钥将立即失效。`, '重置密钥', { type: 'warning' })
  const result = await clientApi.resetSecret(row.id)
  secret.value = result
  secretVisible.value = true
}

async function remove(row: ClientVO) {
  await ElMessageBox.confirm(`确认删除客户端 ${row.clientId}？`, '删除', { type: 'warning' })
  await clientApi.remove(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.hint {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
</style>
