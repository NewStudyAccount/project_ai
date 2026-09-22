<template>
  <div>
    <el-tabs v-model="tab">
      <el-tab-pane label="登录尝试" name="login">
        <el-form inline>
          <el-form-item label="用户名">
            <el-input v-model="username" clearable />
          </el-form-item>
          <el-form-item label="IP">
            <el-input v-model="ip" clearable />
          </el-form-item>
          <el-button type="primary" @click="loadLogin">查询</el-button>
        </el-form>
        <el-table :data="loginRows" border>
          <el-table-column prop="username" label="用户名" width="140" />
          <el-table-column prop="userId" label="用户 ID" width="140" />
          <el-table-column prop="success" label="结果" width="90">
            <template #default="{ row }">
              <el-tag :type="row.success === 1 ? 'success' : 'danger'">{{ row.success === 1 ? '成功' : '失败' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="failReason" label="原因" width="120" />
          <el-table-column prop="ip" label="IP" width="130" />
          <el-table-column prop="clientId" label="client_id" width="120" />
          <el-table-column prop="createTime" label="时间" min-width="160" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="安全审计" name="audit">
        <el-form inline>
          <el-form-item label="action">
            <el-input v-model="action" clearable />
          </el-form-item>
          <el-button type="primary" @click="loadAudit">查询</el-button>
        </el-form>
        <el-table :data="auditRows" border>
          <el-table-column prop="action" label="action" width="140" />
          <el-table-column prop="actorUserId" label="操作者" width="140" />
          <el-table-column prop="targetType" label="目标类型" width="110" />
          <el-table-column prop="targetId" label="目标" width="140" />
          <el-table-column prop="detail" label="详情" min-width="160" />
          <el-table-column prop="ip" label="IP" width="130" />
          <el-table-column prop="createTime" label="时间" min-width="160" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { pageAuditLogs, pageLoginAttempts } from '../../api/clients'
import type { AuthAuditLog, LoginAttempt } from '../../types'

const tab = ref('login')
const username = ref('')
const ip = ref('')
const action = ref('')
const loginRows = ref<LoginAttempt[]>([])
const auditRows = ref<AuthAuditLog[]>([])

async function loadLogin() {
  const page = await pageLoginAttempts(username.value || undefined, ip.value || undefined, undefined, 1, 50)
  loginRows.value = page.records
}

async function loadAudit() {
  const page = await pageAuditLogs(action.value || undefined, undefined, 1, 50)
  auditRows.value = page.records
}

onMounted(() => {
  loadLogin()
  loadAudit()
})
</script>
