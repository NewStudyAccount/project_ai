<template>
  <div>
    <el-form inline>
      <el-form-item label="关键字">
        <el-input v-model="keyword" placeholder="名称 / client_id / system_code" clearable />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="enabled" clearable placeholder="全部" style="width: 120px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
      </el-form-item>
      <el-button type="primary" @click="load">查询</el-button>
      <el-button type="success" @click="openCreate">新建客户端</el-button>
    </el-form>

    <el-table :data="rows" border>
      <el-table-column prop="clientId" label="client_id" min-width="140" />
      <el-table-column prop="clientName" label="名称" min-width="120" />
      <el-table-column prop="clientType" label="类型" width="120" />
      <el-table-column prop="systemCode" label="系统" width="100" />
      <el-table-column prop="enabled" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" @click="toggleEnabled(row)">{{ row.enabled === 1 ? '停用' : '启用' }}</el-button>
          <el-button size="small" type="warning" @click="onResetSecret(row)">重置密钥</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑客户端' : '新建客户端'">
      <el-form label-width="120px">
        <el-form-item label="client_id">
          <el-input v-model="form.clientId" :disabled="!!editing" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.clientName" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.clientType" :disabled="!!editing" style="width: 100%">
            <el-option label="PUBLIC" value="PUBLIC" />
            <el-option label="CONFIDENTIAL" value="CONFIDENTIAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="redirect_uris">
          <el-input v-model="form.redirectUris" placeholder="逗号分隔，精确匹配" />
        </el-form-item>
        <el-form-item label="scopes">
          <el-input v-model="form.scopes" placeholder="openid,profile" />
        </el-form-item>
        <el-form-item label="grant_types">
          <el-input v-model="form.grantTypes" placeholder="authorization_code,refresh_token" />
        </el-form-item>
        <el-form-item label="强制 PKCE">
          <el-switch v-model="form.requirePkce" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="form.owner" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="secretVisible" title="密钥（仅显示一次）">
      <el-alert type="warning" title="请立即保存，关闭后无法再次查看明文" :closable="false" />
      <p style="word-break: break-all; font-family: monospace">{{ plainSecret }}</p>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createClient, pageClients, resetSecret, setClientEnabled, updateClient } from '../../api/clients'
import type { OauthClientVo } from '../../types'

const rows = ref<OauthClientVo[]>([])
const keyword = ref('')
const enabled = ref<number | undefined>()
const dialogVisible = ref(false)
const secretVisible = ref(false)
const plainSecret = ref('')
const editing = ref<string | null>(null)

const form = reactive({
  clientId: '',
  clientName: '',
  clientType: 'PUBLIC',
  redirectUris: '',
  scopes: 'openid,profile',
  grantTypes: 'authorization_code,refresh_token',
  requirePkce: true,
  owner: '',
  remark: '',
})

async function load() {
  const page = await pageClients(keyword.value || undefined, enabled.value, 1, 50)
  rows.value = page.records
}

function openCreate() {
  editing.value = null
  Object.assign(form, {
    clientId: '',
    clientName: '',
    clientType: 'PUBLIC',
    redirectUris: '',
    scopes: 'openid,profile',
    grantTypes: 'authorization_code,refresh_token',
    requirePkce: true,
    owner: '',
    remark: '',
  })
  dialogVisible.value = true
}

function openEdit(row: OauthClientVo) {
  editing.value = row.clientId
  Object.assign(form, {
    clientId: row.clientId,
    clientName: row.clientName,
    clientType: row.clientType,
    redirectUris: row.redirectUris,
    scopes: row.scopes,
    grantTypes: row.grantTypes,
    requirePkce: row.requirePkce === 1,
    owner: row.owner,
    remark: row.remark,
  })
  dialogVisible.value = true
}

async function save() {
  const payload = {
    clientName: form.clientName,
    redirectUris: form.redirectUris,
    scopes: form.scopes,
    grantTypes: form.grantTypes,
    requirePkce: form.requirePkce,
    owner: form.owner,
    remark: form.remark,
  }
  if (editing.value) {
    await updateClient(editing.value, payload)
  } else {
    const res = await createClient({
      ...payload,
      clientId: form.clientId || undefined,
      clientType: form.clientType,
    })
    if (res.clientSecret) {
      plainSecret.value = res.clientSecret
      secretVisible.value = true
    }
  }
  dialogVisible.value = false
  await load()
}

async function toggleEnabled(row: OauthClientVo) {
  await setClientEnabled(row.clientId, row.enabled !== 1)
  await load()
}

async function onResetSecret(row: OauthClientVo) {
  await ElMessageBox.confirm(`确认重置 ${row.clientId} 的密钥？`, '提示')
  const res = await resetSecret(row.clientId)
  if (res.clientSecret) {
    plainSecret.value = res.clientSecret
    secretVisible.value = true
  }
}

onMounted(load)
</script>
