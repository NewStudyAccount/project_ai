<template>
  <div>
    <el-form inline>
      <el-form-item label="用户 ID">
        <el-input v-model="userId" placeholder="user_id" clearable />
      </el-form-item>
      <el-form-item label="client_id">
        <el-input v-model="clientId" clearable />
      </el-form-item>
      <el-button type="primary" @click="load">查询</el-button>
    </el-form>

    <el-table :data="rows" border>
      <el-table-column prop="id" label="grant_id" min-width="140" />
      <el-table-column prop="userId" label="用户" width="140" />
      <el-table-column prop="clientId" label="client_id" min-width="120" />
      <el-table-column prop="scopes" label="scopes" min-width="120" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '活跃' : '吊销' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" type="danger" :disabled="row.status !== 1" @click="kick(row)">踢下线</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { kickClient, kickUser, pageGrants } from '../../api/clients'
import type { AuthGrant } from '../../types'

const rows = ref<AuthGrant[]>([])
const userId = ref('')
const clientId = ref('')

async function load() {
  const page = await pageGrants(userId.value || undefined, clientId.value || undefined, 1, 50)
  rows.value = page.records
}

async function kick(row: AuthGrant) {
  await ElMessageBox.confirm('确认吊销该授权链并踢下线？', '提示')
  if (row.userId) {
    await kickUser(row.userId)
  } else if (row.clientId) {
    await kickClient(row.clientId)
  }
  ElMessage.success('已吊销')
  await load()
}

onMounted(load)
</script>
