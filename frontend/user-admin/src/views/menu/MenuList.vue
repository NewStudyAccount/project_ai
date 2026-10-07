<template>
  <div class="page-card">
    <div class="toolbar">
      <el-button v-if="hasPermission('user:menu:create')" type="primary" @click="openCreate()">新建</el-button>
    </div>
    <el-table v-if="menus.length" :data="menus" row-key="id" default-expand-all border>
      <el-table-column prop="name" label="名称" min-width="180" />
      <el-table-column prop="type" label="类型" width="90">
        <template #default="{ row }">{{ typeLabel(row.type) }}</template>
      </el-table-column>
      <el-table-column prop="permission" label="权限标识" width="220" />
      <el-table-column prop="path" label="路由" width="180" />
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button v-if="hasPermission('user:menu:create')" link @click="openCreate(row.id)">新增子级</el-button>
          <el-button v-if="hasPermission('user:menu:update')" link @click="openEdit(row)">编辑</el-button>
          <el-button v-if="hasPermission('user:menu:delete')" link type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else description="暂无菜单" />
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑菜单' : '新建菜单'" width="560px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="父节点">
          <el-input v-model="form.parentId" placeholder="根为 0" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type">
            <el-option label="目录" :value="1" />
            <el-option label="菜单" :value="2" />
            <el-option label="按钮" :value="3" />
            <el-option label="接口" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item v-if="showPermission" label="权限标识">
          <el-input v-model="form.permission" placeholder="user:resource:action" />
        </el-form-item>
        <el-form-item v-if="isMenuType" label="路由">
          <el-input v-model="form.path" />
        </el-form-item>
        <el-form-item v-if="isMenuType" label="组件">
          <el-input v-model="form.component" placeholder="views/xxx/XxxList.vue" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" />
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
import { computed, onMounted, reactive, ref } from 'vue'
import { rbacApi, type MenuInput } from '@/api/rbac'
import { hasPermission } from '@/utils/permission'
import type { MenuVO } from '@/types'

const menus = ref<MenuVO[]>([])
const dialogVisible = ref(false)
const editing = ref(false)
const editingId = ref('')
const form = reactive<MenuInput>({ parentId: '0', type: 2, name: '', permission: '', path: '', component: '', sort: 0 })

const isMenuType = computed(() => form.type === 1 || form.type === 2)
const showPermission = computed(() => form.type === 2 || form.type === 3 || form.type === 4)

function typeLabel(type: number) {
  return ({ 1: '目录', 2: '菜单', 3: '按钮', 4: '接口' } as Record<number, string>)[type] || type
}

async function load() {
  menus.value = await rbacApi.menus()
}

function openCreate(parentId?: string) {
  editing.value = false
  editingId.value = ''
  Object.assign(form, { parentId: parentId || '0', type: 2, name: '', permission: '', path: '', component: '', sort: 0 })
  dialogVisible.value = true
}

function openEdit(row: MenuVO) {
  editing.value = true
  editingId.value = row.id
  Object.assign(form, {
    parentId: row.parentId,
    type: row.type,
    name: row.name,
    permission: row.permission,
    path: row.path,
    component: row.component,
    sort: row.sort,
  })
  dialogVisible.value = true
}

async function save() {
  const payload: MenuInput = {
    ...form,
    permission: showPermission.value ? form.permission : '',
    path: isMenuType.value ? form.path : '',
    component: isMenuType.value ? form.component : '',
  }
  if (editing.value) await rbacApi.updateMenu(editingId.value, payload)
  else await rbacApi.createMenu(payload)
  dialogVisible.value = false
  await load()
}

async function remove(id: string) {
  await rbacApi.deleteMenu(id)
  await load()
}

onMounted(load)
</script>
