<template>
  <div class="page-card">
    <div class="toolbar">
      <el-button v-if="hasPermission('user:role:create')" type="primary" @click="openCreate">新建</el-button>
    </div>
    <el-table :data="roles" border>
      <el-table-column prop="roleCode" label="编码" width="160" />
      <el-table-column prop="roleName" label="名称" width="160" />
      <el-table-column prop="dataScope" label="数据范围" width="100" />
      <el-table-column prop="status" label="状态" width="90" />
      <el-table-column prop="remark" label="备注" min-width="180" />
      <el-table-column label="操作" width="240">
        <template #default="{ row }">
          <el-button v-if="hasPermission('user:role:update')" link @click="openEdit(row)">编辑</el-button>
          <el-button v-if="hasPermission('user:role:assign')" link @click="openMenus(row)">授权</el-button>
          <el-button v-if="hasPermission('user:role:delete')" link @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑角色' : '新建角色'">
      <el-form :model="form" label-width="90px">
        <el-form-item label="编码">
          <el-input v-model="form.roleCode" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.roleName" />
        </el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="form.dataScope">
            <el-option label="全部" :value="1" />
            <el-option label="本部门" :value="2" />
            <el-option label="仅本人" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="menuDialogVisible" title="角色菜单授权">
      <el-tree ref="menuTree" :data="menus" node-key="id" show-checkbox default-expand-all />
      <template #footer>
        <el-button @click="menuDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveMenus">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { rbacApi, type RoleInput } from '@/api/rbac'
import { hasPermission } from '@/utils/permission'
import type { MenuVO, RoleVO } from '@/types'
import type { ElTree } from 'element-plus'

const roles = ref<RoleVO[]>([])
const menus = ref<MenuVO[]>([])
const dialogVisible = ref(false)
const menuDialogVisible = ref(false)
const editing = ref(false)
const editingId = ref('')
const currentRole = ref<RoleVO>()
const menuTree = ref<InstanceType<typeof ElTree>>()
const form = reactive<RoleInput>({ roleCode: '', roleName: '', dataScope: 1, sort: 0, status: 1, remark: '' })

async function load() {
  roles.value = await rbacApi.roles()
}

function openCreate() {
  editing.value = false
  editingId.value = ''
  Object.assign(form, { roleCode: '', roleName: '', dataScope: 1, sort: 0, status: 1, remark: '' })
  dialogVisible.value = true
}

function openEdit(row: RoleVO) {
  editing.value = true
  editingId.value = row.id
  Object.assign(form, { roleCode: row.roleCode, roleName: row.roleName, dataScope: row.dataScope, sort: row.sort, status: row.status, remark: row.remark })
  dialogVisible.value = true
}

async function save() {
  if (editing.value) await rbacApi.updateRole(editingId.value, form)
  else await rbacApi.createRole(form)
  dialogVisible.value = false
  await load()
}

async function openMenus(row: RoleVO) {
  currentRole.value = row
  menus.value = await rbacApi.menus()
  menuDialogVisible.value = true
}

async function saveMenus() {
  const ids = (menuTree.value?.getCheckedKeys(false) as string[]) || []
  await rbacApi.assignRoleMenus(currentRole.value?.id || '', ids)
  menuDialogVisible.value = false
  await load()
}

async function remove(id: string) {
  await rbacApi.deleteRole(id)
  await load()
}

onMounted(async () => {
  await load()
  menus.value = await rbacApi.menus()
})
</script>
