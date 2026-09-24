<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input v-model="query.userId" placeholder="用户 ID" clearable style="width: 160px" />
      <el-input v-model="query.clientId" placeholder="Client ID" clearable style="width: 160px" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
        <el-option label="有效" :value="1" />
        <el-option label="已吊销" :value="0" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
    </div>
    <el-table :data="records" border>
      <el-table-column prop="id" label="ID" width="180" />
      <el-table-column prop="userId" label="用户 ID" width="160" />
      <el-table-column prop="clientId" label="Client ID" width="140" />
      <el-table-column prop="scopes" label="Scopes" min-width="140" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '有效' : '已吊销' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="revokedAt" label="吊销时间" width="170" />
      <el-table-column prop="revokeReason" label="原因" min-width="140" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="hasPermission('auth:grant:revoke') && row.status === 1"
            link
            type="danger"
            @click="openRevoke(row)"
          >
            吊销
          </el-button>
          <el-button
            v-if="hasPermission('auth:grant:revoke')"
            link
            type="danger"
            @click="openRevokeUser(row.userId)"
          >
            踢下线
          </el-button>
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

    <el-dialog v-model="revokeVisible" :title="revokeUserMode ? '踢下线（按用户吊销）' : '吊销授权'" width="480px">
      <p v-if="revokeUserMode">将吊销用户 {{ targetUserId }} 的全部有效授权并失效 SSO 会话。</p>
      <el-form label-width="80px">
        <el-form-item label="原因">
          <el-input v-model="reason" type="textarea" placeholder="吊销原因（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="revokeVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmRevoke">确认吊销</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { grantApi } from '@/api/grant'
import { hasPermission } from '@/utils/permission'
import type { GrantVO } from '@/types'

const records = ref<GrantVO[]>([])
const total = ref(0)
const revokeVisible = ref(false)
const revokeUserMode = ref(false)
const targetGrantId = ref('')
const targetUserId = ref('')
const reason = ref('')

const query = reactive({
  current: 1,
  size: 10,
  userId: '',
  clientId: '',
  status: undefined as number | undefined,
})

async function load() {
  const page = await grantApi.page(query)
  records.value = page.records
  total.value = page.total
}

function openRevoke(row: GrantVO) {
  revokeUserMode.value = false
  targetGrantId.value = row.id
  targetUserId.value = row.userId
  reason.value = ''
  revokeVisible.value = true
}

function openRevokeUser(userId: string) {
  revokeUserMode.value = true
  targetGrantId.value = ''
  targetUserId.value = userId
  reason.value = ''
  revokeVisible.value = true
}

async function confirmRevoke() {
  if (revokeUserMode.value) {
    await grantApi.revokeByUser(targetUserId.value, reason.value)
  } else {
    await grantApi.revoke(targetGrantId.value, reason.value)
  }
  ElMessage.success('已吊销')
  revokeVisible.value = false
  await load()
}

onMounted(load)
</script>
