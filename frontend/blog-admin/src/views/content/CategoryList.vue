<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createCategory, deleteCategory, listCategories, updateCategory, type CategoryVO } from '@/api/content'
import { hasPermission } from '@/utils/permission'

const tree = ref<CategoryVO[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editingId = ref<string | null>(null)
const form = reactive({
  parentId: '0',
  name: '',
  slug: '',
  description: '',
  sort: 0,
  status: 1,
})

async function load() {
  loading.value = true
  try {
    tree.value = await listCategories()
  } finally {
    loading.value = false
  }
}

function openCreate(parentId = '0') {
  editingId.value = null
  form.parentId = parentId
  form.name = ''
  form.slug = ''
  form.description = ''
  form.sort = 0
  form.status = 1
  dialogVisible.value = true
}

function openEdit(row: CategoryVO) {
  editingId.value = row.id
  form.parentId = row.parentId
  form.name = row.name
  form.slug = row.slug
  form.description = row.description || ''
  form.sort = row.sort ?? 0
  form.status = row.status ?? 1
  dialogVisible.value = true
}

async function save() {
  const body = { ...form }
  if (editingId.value) {
    await updateCategory(editingId.value, body)
  } else {
    await createCategory(body)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  await load()
}

async function remove(row: CategoryVO) {
  await ElMessageBox.confirm(`确认删除分类「${row.name}」？`, '提示', { type: 'warning' })
  try {
    await deleteCategory(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '删除失败'
    ElMessage.error(msg)
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-button v-if="hasPermission('blog:category:create')" type="primary" @click="openCreate('0')">新建根分类</el-button>
    </div>
    <el-table v-loading="loading" :data="tree" row-key="id" default-expand-all>
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="slug" label="slug" width="160" />
      <el-table-column prop="description" label="描述" min-width="160" />
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button v-if="hasPermission('blog:category:create') && row.parentId === '0'" link @click="openCreate(row.id)">子分类</el-button>
          <el-button v-if="hasPermission('blog:category:update')" link @click="openEdit(row)">编辑</el-button>
          <el-button v-if="hasPermission('blog:category:delete')" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑分类' : '新建分类'" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" maxlength="64" /></el-form-item>
        <el-form-item label="slug"><el-input v-model="form.slug" maxlength="64" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" maxlength="255" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
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

<style scoped>
.page {
  padding: 16px;
}
.toolbar {
  margin-bottom: 12px;
}
</style>
