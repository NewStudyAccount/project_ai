<template>
  <div class="page-card">
    <div class="toolbar">
      <el-button v-if="hasPermission('user:menu:create')" type="primary" @click="openCreate">新建</el-button>
    </div>
    <el-table :data="menus" row-key="id" default-expand-all border>
      <el-table-column prop="name" label="名称" min-width="180" />
      <el-table-column prop="type" label="类型" width="90" />
      <el-table-column prop="permission" label="权限标识" width="220" />
      <el-table-column prop="path" label="路由" width="180" />
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button v-if="hasPermission('user:menu:update')" link @click="openEdit(row)">编辑</el-button>
          <el-button v-if="hasPermission('user:menu:delete')" link @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑菜单' : '新建菜单'">
      <el-form :model="form" label-width="100px">
        <el-form-item label="父节点">
          <el-input v-model="form.parentId" />
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
        <el-form-item label="权限标识">
          <el-input v-model="form.permission" />
        </el-form-item>
        <el-form-item label="路由">
          <el-input v-model="form.path" />
        </el-form-item>
        <el-form-item label="组件">
          <el-input v-model="form.component" />
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
import { onMounted, reactive, ref } from 'vue'
import { rbacApi, type MenuInput } from '@/api/rbac'
import { hasPermission } from '@/utils/permission'
import type { MenuVO } from '@/types'

const menus = ref<MenuVO[]>([])
const dialogVisible = ref(false)
const editing = ref(false)
const editingId = ref('')
const form = reactive<MenuInput>({ parentId: '0', type: 2, name: '', permission: '', path: '', component: '', sort: 0 })

async function load() {
  menus.value = await rbacApi.menus()
}

function openCreate() {
  editing.value = false
  editingId.value = ''
  Object.assign(form, { parentId: '0', type: 2, name: '', permission: '', path: '', component: '', sort: 0 })
  dialogVisible.value = true
}

function openEdit(row: MenuVO) {
  editing.value = true
  editingId.value = row.id
  Object.assign(form, { parentId: row.parentId, type: row.type, name: row.name, permission: row.permission, path: row.path, component: row.component, sort: row.sort })
  dialogVisible.value = true
}

async function save() {
  if (editing.value) await rbacApi.updateMenu(editingId.value, form)
  else await rbacApi.createMenu(form)
  dialogVisible.value = false
  await load()
}

async function remove(id: string) {
  await rbacApi.deleteMenu(id)
  await load()
}

onMounted(load)
</script>
