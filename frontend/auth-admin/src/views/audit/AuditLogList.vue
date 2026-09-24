<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input v-model="query.action" placeholder="动作" clearable style="width: 160px" />
      <el-input v-model="query.actorUserId" placeholder="操作人 ID" clearable style="width: 150px" />
      <el-input v-model="query.targetId" placeholder="目标 ID" clearable style="width: 150px" />
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
      <el-table-column prop="action" label="动作" width="160" />
      <el-table-column prop="actorUserId" label="操作人" width="150" />
      <el-table-column prop="targetType" label="目标类型" width="120" />
      <el-table-column prop="targetId" label="目标 ID" width="160" />
      <el-table-column prop="detail" label="摘要" min-width="180" />
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
  action: '',
  actorUserId: '',
  targetId: '',
  beginTime: '',
  endTime: '',
})

watch(timeRange, (value) => {
  query.beginTime = value?.[0] || ''
  query.endTime = value?.[1] || ''
})

async function load() {
  const page = await auditApi.auditLogs(query)
  records.value = page.records
  total.value = page.total
}

onMounted(load)
</script>
