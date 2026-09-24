<template>
  <div class="page-card">
    <div class="toolbar">
      <el-button v-if="hasPermission('auth:role:create')" type="primary" @click="openCreate">新建</el-button>
    </div>
    <el-table :data="roles" border>
      <el-table-column prop="id" label="ID" width="180" />
      <el-table-column prop="roleCode" label="编码" width="140" />
      <el-table-column prop="roleName" label="名称" width="140" />
      <el-table-column prop="dataScope" label="数据范围" width="100">
        <template #default="{ row }">{{ scopeLabel(row.dataScope) }}</template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">{{ row.status === 1 ? '启用' : '停用' }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" />
      <el-table-column label="操作" width="260">
        <template #default="{ row }">
          <el-button v-if="hasPermission('auth:role:update')" link @click="openEdit(row)">编辑</el-button>
          <el-button v-if="hasPermission('auth:role:assign')" link @click="openMenus(row)">分配菜单</el-button>
          <el-button v-if="hasPermission('auth:role:delete')" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑角色' : '新建角色'" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="编码" required>
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
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
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

    <el-dialog v-model="menuDialogVisible" title="角色菜单授权" width="520px">
      <el-tree
        ref="menuTreeRef"
        :data="menuTree"
        node-key="id"
        show-checkbox
        default-expand-all
        :props="{ label: 'name', children: 'children' }"
      />
      <template #footer>
        <el-button @click="menuDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveMenus">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox, type ElTree } from 'element-plus'
import { rbacApi } from '@/api/rbac'
import { hasPermission } from '@/utils/permission'
import type { MenuVO, RoleRequest, RoleVO } from '@/types'

const roles = ref<RoleVO[]>([])
const menuTree = ref<MenuVO[]>([])
const dialogVisible = ref(false)
const menuDialogVisible = ref(false)
const editing = ref(false)
const editingId = ref('')
const currentRole = ref<RoleVO | null>(null)
const menuTreeRef = ref<InstanceType<typeof ElTree>>()

const emptyForm = (): RoleRequest => ({
  roleCode: '',
  roleName: '',
  dataScope: 1,
  sort: 0,
  status: 1,
  remark: '',
})

const form = ref<RoleRequest>(emptyForm())

function scopeLabel(scope: number): string {
  if (scope === 1) return '全部'
  if (scope === 2) return '本部门'
  if (scope === 3) return '仅本人'
  return String(scope)
}

async function load() {
  roles.value = await rbacApi.roles()
}

function openCreate() {
  editing.value = false
  editingId.value = ''
  form.value = emptyForm()
  dialogVisible.value = true
}

function openEdit(row: RoleVO) {
  editing.value = true
  editingId.value = row.id
  form.value = {
    roleCode: row.roleCode,
    roleName: row.roleName,
    dataScope: row.dataScope,
    sort: row.sort,
    status: row.status,
    remark: row.remark,
  }
  dialogVisible.value = true
}

async function save() {
  if (editing.value) {
    await rbacApi.updateRole(editingId.value, form.value)
  } else {
    await rbacApi.createRole(form.value)
  }
  dialogVisible.value = false
  await load()
}

async function openMenus(row: RoleVO) {
  currentRole.value = row
  menuTree.value = await rbacApi.menus()
  menuDialogVisible.value = true
  await nextTick()
  menuTreeRef.value?.setCheckedKeys(row.menuIds || [])
}

async function saveMenus() {
  const checked = menuTreeRef.value?.getCheckedKeys(false) as string[]
  const halfChecked = menuTreeRef.value?.getHalfCheckedKeys() as string[]
  const ids = [...new Set([...(checked || []), ...(halfChecked || [])])]
  await rbacApi.assignRoleMenus(currentRole.value?.id || '', ids)
  ElMessage.success('已保存角色菜单')
  menuDialogVisible.value = false
  await load()
}

async function remove(row: RoleVO) {
  await ElMessageBox.confirm(`确认删除角色「${row.roleCode}」？`, '删除', { type: 'warning' })
  await rbacApi.deleteRole(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(async () => {
  await load()
  menuTree.value = await rbacApi.menus()
})
</script>
