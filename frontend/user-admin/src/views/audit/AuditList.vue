<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input v-model="query.action" placeholder="动作" clearable style="width: 180px" />
      <el-input v-model="query.actorUserId" placeholder="操作人 ID" clearable style="width: 180px" />
      <el-button type="primary" @click="load">查询</el-button>
    </div>
    <el-table :data="records" border>
      <el-table-column prop="id" label="ID" width="180" />
      <el-table-column prop="action" label="动作" width="160" />
      <el-table-column prop="actorUserId" label="操作人" width="160" />
      <el-table-column prop="targetUserId" label="目标用户" width="160" />
      <el-table-column prop="detail" label="摘要" min-width="220" />
      <el-table-column prop="ip" label="IP" width="150" />
      <el-table-column prop="createTime" label="时间" width="180" />
    </el-table>
    <el-pagination
      v-model:current-page="query.current"
      v-model:page-size="query.size"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="load"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { auditApi } from '@/api/audit'
import type { AuditVO } from '@/types'

const records = ref<AuditVO[]>([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, action: '', actorUserId: '' })

async function load() {
  const page = await auditApi.page(query)
  records.value = page.records
  total.value = page.total
}

onMounted(load)
</script>
