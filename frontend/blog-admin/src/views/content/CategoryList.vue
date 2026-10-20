<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import CrudForm from '@/components/common/CrudForm.vue'
import { createCategory, deleteCategory, listCategories, updateCategory, type CategoryVO } from '@/api/content'
import { hasPermission } from '@/utils/permission'
import type { FormFieldDef } from '@/types/crud'

/** 分类为树形数据（GET /admin/categories 返回树），按 P-Tree 形态保留树表（不套 CrudPage 分页） */
const tree = ref<CategoryVO[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<string | null>(null)
const parentId = ref('0')
const filters = reactive({ name: '', status: '' as number | '' })
const formValues = ref<Record<string, unknown>>({ ...formDefaults() })

/** D-Form 字段（Bean Validation 前端镜像：name / slug 必填） */
const fields: FormFieldDef[] = [
  { prop: 'name', label: '名称', type: 'input', required: true, placeholder: '分类名' },
  { prop: 'slug', label: 'slug', type: 'input', required: true, placeholder: '小写连字符' },
  { prop: 'description', label: '描述', type: 'textarea', placeholder: '可选' },
  { prop: 'sort', label: '排序', type: 'number', min: 0 },
  { prop: 'status', label: '状态', type: 'switch' },
]

const formMode = computed<'create' | 'edit'>(() => (editingId.value ? 'edit' : 'create'))

function formDefaults(): Record<string, unknown> {
  return { name: '', slug: '', description: '', sort: 0, status: 1 }
}

async function load() {
  loading.value = true
  try {
    tree.value = await listCategories({
      name: filters.name || undefined,
      status: filters.status === '' ? undefined : filters.status,
    })
  } finally {
    loading.value = false
  }
}

function openCreate(parent = '0') {
  editingId.value = null
  parentId.value = parent
  formValues.value = { ...formDefaults() }
  dialogVisible.value = true
}

function openEdit(row: CategoryVO) {
  editingId.value = row.id
  parentId.value = row.parentId
  formValues.value = {
    name: row.name,
    slug: row.slug,
    description: row.description || '',
    sort: row.sort ?? 0,
    status: row.status ?? 1,
  }
  dialogVisible.value = true
}

async function save(values: Record<string, unknown>) {
  if (saving.value) return
  saving.value = true
  try {
    const body = {
      parentId: parentId.value,
      name: values.name,
      slug: values.slug,
      description: values.description ?? '',
      sort: values.sort ?? 0,
      status: values.status ?? 1,
    }
    if (editingId.value) {
      await updateCategory(editingId.value, body)
    } else {
      await createCategory(body)
    }
    ElMessage.success({ message: '保存成功', duration: 2000 })
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function remove(row: CategoryVO) {
  await ElMessageBox.confirm(
    `确认删除分类「${row.name}」？删除前会做引用检查，存在子类或文章引用时将拒绝删除。`,
    '提示',
    { type: 'warning' },
  )
  try {
    await deleteCategory(row.id)
    ElMessage.success({ message: '已删除', duration: 2000 })
    await load()
  } catch {
    // 后端引用约束业务 msg（如「分类下存在子类或文章」）已由 request.ts 统一提示（D3：前端不自造引用文案分支）
  }
}

onMounted(load)
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <el-button v-if="hasPermission('blog:category:create')" type="primary" @click="openCreate('0')">新建根分类</el-button>
      <span class="toolbar-spacer" />
      <el-input
        v-model="filters.name"
        placeholder="名称关键词"
        clearable
        style="width: 200px"
        @keyup.enter="load"
        @clear="load"
      />
      <el-select v-model="filters.status" placeholder="状态" clearable style="width: 120px" @change="load">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="tree" row-key="id" default-expand-all border>
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="slug" label="slug" width="160" />
      <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <span :class="['status-pill', row.status === 1 ? 'is-on' : 'is-off']">
            {{ row.status === 1 ? '启用' : '停用' }}
          </span>
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

    <CrudForm
      v-model="dialogVisible"
      :title="editingId ? '编辑分类' : '新建分类'"
      :fields="fields"
      :values="formValues"
      :mode="formMode"
      :loading="saving"
      :width="480"
      @save="save"
    />
  </div>
</template>

<style scoped>
.toolbar-spacer {
  flex: 1;
}
</style>
