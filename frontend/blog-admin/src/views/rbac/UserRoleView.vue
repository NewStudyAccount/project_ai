<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { Role } from '@/types'
import { BlogPermissions } from '@/types'
import { grantRole, listRoles, revokeRole } from '@/api/rbac'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const roles = ref<Role[]>([])
const userId = ref('')
const roleId = ref('')

async function load() {
  roles.value = await listRoles()
}

async function onGrant() {
  try {
    await grantRole(userId.value, roleId.value)
    ElMessage.success('已授权')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '授权失败')
  }
}

async function onRevoke() {
  try {
    await revokeRole(userId.value, roleId.value)
    ElMessage.success('已回收')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '回收失败')
  }
}

onMounted(load)
</script>

<template>
  <el-card>
    <el-alert
      v-if="!userStore.hasPermission(BlogPermissions.USER_ROLE_MANAGE)"
      type="info"
      :closable="false"
      title="当前账号可能无 blog:rbac:user-role 权限，后端将返回 403"
      style="margin-bottom: 12px"
    />
    <el-form inline>
      <el-form-item label="用户 ID">
        <el-input v-model="userId" placeholder="14 位 user_id" />
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="roleId" placeholder="选择角色" style="width: 200px">
          <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
        </el-select>
      </el-form-item>
      <el-button type="primary" @click="onGrant">授权</el-button>
      <el-button @click="onRevoke">回收</el-button>
    </el-form>
  </el-card>
</template>
