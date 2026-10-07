<template>
  <div class="page-card">
    <div class="toolbar">
      <el-button v-if="hasPermission('auth:menu:create')" type="primary" @click="openCreate()">新建</el-button>
    </div>
    <el-table v-if="menus.length" :data="menus" row-key="id" default-expand-all border>
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="type" label="类型" width="90">
        <template #default="{ row }">{{ typeLabel(row.type) }}</template>
      </el-table-column>
      <el-table-column prop="permission" label="权限标识" width="200" />
      <el-table-column prop="path" label="路由" width="160" />
      <el-table-column prop="component" label="组件" width="200" />
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">{{ row.status === 1 ? '启用' : '停用' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240">
        <template #default="{ row }">
          <el-button v-if="hasPermission('auth:menu:create')" link @click="openCreate(row.id)">新增子级</el-button>
          <el-button v-if="hasPermission('auth:menu:update')" link @click="openEdit(row)">编辑</el-button>
          <el-button v-if="hasPermission('auth:menu:delete')" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else description="暂无菜单" />

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑菜单' : '新建菜单'" width="640px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="父节点 ID">
          <el-input v-model="form.parentId" placeholder="根节点填 0" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="form.type">
            <el-option label="目录" :value="1" />
            <el-option label="菜单" :value="2" />
            <el-option label="按钮" :value="3" />
            <el-option label="接口" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item v-if="showPermission" label="权限标识">
          <el-input v-model="form.permission" placeholder="auth:resource:action" />
        </el-form-item>
        <el-form-item v-if="isMenuType" label="路由">
          <el-input v-model="form.path" />
        </el-form-item>
        <el-form-item v-if="isMenuType" label="组件">
          <el-input v-model="form.component" placeholder="views/xxx/Xxx.vue" />
        </el-form-item>
        <el-form-item v-if="isMenuType" label="图标">
          <el-input v-model="form.icon" />
        </el-form-item>
        <el-form-item v-if="isMenuType" label="隐藏">
          <el-switch v-model="form.hidden" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item v-if="isMenuType" label="需要鉴权">
          <el-switch v-model="form.requiresAuth" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { rbacApi } from '@/api/rbac'
import { hasPermission } from '@/utils/permission'
import type { MenuRequest, MenuVO } from '@/types'

const menus = ref<MenuVO[]>([])
const dialogVisible = ref(false)
const editing = ref(false)
const editingId = ref('')

const emptyForm = (): MenuRequest => ({
  parentId: '0',
  type: 2,
  name: '',
  permission: '',
  path: '',
  component: '',
  icon: '',
  hidden: 0,
  requiresAuth: 1,
  sort: 0,
  status: 1,
})

const form = ref<MenuRequest>(emptyForm())
const isMenuType = computed(() => form.value.type === 1 || form.value.type === 2)
const showPermission = computed(() => form.value.type !== 1)

function typeLabel(type: number): string {
  if (type === 1) return '目录'
  if (type === 2) return '菜单'
  if (type === 3) return '按钮'
  if (type === 4) return '接口'
  return '其他'
}

async function load() {
  menus.value = await rbacApi.menus()
}

function openCreate(parentId?: string) {
  editing.value = false
  editingId.value = ''
  form.value = { ...emptyForm(), parentId: parentId || '0' }
  dialogVisible.value = true
}

function openEdit(row: MenuVO) {
  editing.value = true
  editingId.value = row.id
  form.value = {
    parentId: row.parentId,
    type: row.type,
    name: row.name,
    permission: row.permission,
    path: row.path,
    component: row.component,
    icon: row.icon,
    hidden: row.hidden,
    requiresAuth: row.requiresAuth,
    sort: row.sort,
    status: row.status,
  }
  dialogVisible.value = true
}

async function save() {
  const payload: MenuRequest = {
    ...form.value,
    permission: showPermission.value ? form.value.permission : '',
    path: isMenuType.value ? form.value.path : '',
    component: isMenuType.value ? form.value.component : '',
    icon: isMenuType.value ? form.value.icon : '',
    hidden: isMenuType.value ? form.value.hidden : 0,
    requiresAuth: isMenuType.value ? form.value.requiresAuth : 1,
  }
  if (editing.value) {
    await rbacApi.updateMenu(editingId.value, payload)
  } else {
    await rbacApi.createMenu(payload)
  }
  dialogVisible.value = false
  await load()
}

async function remove(row: MenuVO) {
  await ElMessageBox.confirm(`确认删除菜单「${row.name}」？`, '删除', { type: 'warning' })
  await rbacApi.deleteMenu(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>
