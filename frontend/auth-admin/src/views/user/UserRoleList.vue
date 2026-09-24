<template>
  <div class="page-card">
    <el-alert type="info" :closable="false" show-icon title="为用户分配角色：提交后全量覆盖该用户的角色集合" />
    <el-form :model="form" label-width="100px" class="filter-form mt12">
      <el-form-item label="用户 ID" required>
        <el-input v-model="form.userId" placeholder="目标用户 ID" style="width: 280px" />
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="form.ids" multiple placeholder="选择角色" style="width: 420px">
          <el-option v-for="role in roles" :key="role.id" :label="role.roleCode + ' / ' + role.roleName" :value="role.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button v-if="hasPermission('auth:role:assign')" type="primary" @click="save">保存分配</el-button>
      </el-form-item>
    </el-form>

    <h3>角色一览</h3>
    <el-table :data="roles" border>
      <el-table-column prop="id" label="ID" width="180" />
      <el-table-column prop="roleCode" label="编码" width="140" />
      <el-table-column prop="roleName" label="名称" width="140" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">{{ row.status === 1 ? '启用' : '停用' }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { rbacApi } from '@/api/rbac'
import { hasPermission } from '@/utils/permission'
import type { RoleVO } from '@/types'

const roles = ref<RoleVO[]>([])
const form = reactive({ userId: '', ids: [] as string[] })

async function load() {
  roles.value = await rbacApi.roles()
}

async function save() {
  if (!form.userId) {
    ElMessage.warning('请填写用户 ID')
    return
  }
  await rbacApi.assignUserRoles(form.userId, form.ids)
  ElMessage.success('已保存用户角色')
}

onMounted(load)
</script>
