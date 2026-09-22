<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { Permission } from '@/types'
import { pagePermissions } from '@/api/rbac'

const rows = ref<Permission[]>([])
const total = ref(0)
const current = ref(1)
const size = ref(20)

async function load() {
  const page = await pagePermissions(current.value, size.value)
  rows.value = page.records
  total.value = Number(page.total)
}

onMounted(load)
</script>

<template>
  <el-card>
    <el-table :data="rows" stripe>
      <el-table-column prop="permissionCode" label="权限码" width="220" />
      <el-table-column prop="permissionName" label="名称" />
      <el-table-column prop="permissionType" label="类型" width="80" />
      <el-table-column prop="status" label="状态" width="80" />
    </el-table>
  </el-card>
</template>
