<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input v-model="query.username" placeholder="用户名" clearable style="width: 150px" />
      <el-input v-model="query.userId" placeholder="用户 ID" clearable style="width: 150px" />
      <el-input v-model="query.clientId" placeholder="Client ID" clearable style="width: 140px" />
      <el-input v-model="query.ip" placeholder="IP" clearable style="width: 140px" />
      <el-select v-model="query.success" placeholder="结果" clearable style="width: 120px">
        <el-option label="成功" :value="1" />
        <el-option label="失败" :value="0" />
      </el-select>
      <el-date-picker
        v-model="timeRange"
        type="datetimerange"
        value-format="YYYY-MM-DDTHH:mm:ss"
        start-placeholder="开始"
        end-placeholder="结束"
        style="width: 340px"
      />
      <el-button type="primary" @click="load">查询</el-button>
    </div>
    <el-table :data="records" border>
      <el-table-column prop="id" label="ID" width="180" />
      <el-table-column prop="action" label="结果" width="120">
        <template #default="{ row }">
          <el-tag :type="row.action === 'LOGIN_SUCCESS' ? 'success' : 'danger'">
            {{ row.action === 'LOGIN_SUCCESS' ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="targetId" label="用户名" width="140" />
      <el-table-column prop="actorUserId" label="用户 ID" width="150" />
      <el-table-column prop="detail" label="失败原因" min-width="140" />
      <el-table-column prop="ip" label="IP" width="140" />
      <el-table-column prop="createTime" label="时间" width="180" />
    </el-table>
    <el-pagination
      v-model:current-page="query.current"
      v-model:page-size="query.size"
      :total="total"
      layout="total, prev, pager, next"
      class="mt12"
      @current-change="load"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { auditApi } from '@/api/audit'
import type { AuditVO } from '@/types'

const records = ref<AuditVO[]>([])
const total = ref(0)
const timeRange = ref<[string, string] | null>(null)

const query = reactive({
  current: 1,
  size: 10,
  username: '',
  userId: '',
  clientId: '',
  ip: '',
  success: undefined as number | undefined,
  beginTime: '',
  endTime: '',
})

watch(timeRange, (value) => {
  query.beginTime = value?.[0] || ''
  query.endTime = value?.[1] || ''
})

async function load() {
  const page = await auditApi.loginAttempts(query)
  records.value = page.records
  total.value = page.total
}

onMounted(load)
</script>
