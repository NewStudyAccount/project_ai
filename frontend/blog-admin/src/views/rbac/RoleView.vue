<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { Role } from '@/types'
import { BlogPermissions } from '@/types'
import { createRole, pageRoles } from '@/api/rbac'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const rows = ref<Role[]>([])
const total = ref(0)
const current = ref(1)
const size = ref(10)
const roleCode = ref('')
const roleName = ref('')

async function load() {
  const page = await pageRoles(current.value, size.value)
  rows.value = page.records
  total.value = Number(page.total)
}

async function onCreate() {
  if (!userStore.hasPermission(BlogPermissions.ROLE_MANAGE)) {
    // 仍允许尝试，后端 403 兜底
  }
  try {
    await createRole({ roleCode: roleCode.value, roleName: roleName.value })
    ElMessage.success('已创建')
    roleCode.value = ''
    roleName.value = ''
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '创建失败')
  }
}

onMounted(load)
</script>

<template>
  <el-card>
    <el-form inline @submit.prevent="onCreate">
      <el-form-item label="编码">
        <el-input v-model="roleCode" placeholder="blog_author" />
      </el-form-item>
      <el-form-item label="名称">
        <el-input v-model="roleName" placeholder="作者" />
      </el-form-item>
      <el-button type="primary" native-type="submit">新建角色</el-button>
    </el-form>
    <el-table :data="rows" stripe>
      <el-table-column prop="id" label="ID" width="160" />
      <el-table-column prop="roleCode" label="编码" width="140" />
      <el-table-column prop="roleName" label="名称" />
      <el-table-column prop="status" label="状态" width="80" />
      <el-table-column prop="remark" label="备注" />
    </el-table>
  </el-card>
</template>
