<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createTag, deleteTag, pageTags, updateTag, type TagVO } from '@/api/content'
import { hasPermission } from '@/utils/permission'
import type { PageResult } from '@/types'

const rows = ref<TagVO[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ current: 1, size: 10, name: '' })
const dialogVisible = ref(false)
const editingId = ref<string | null>(null)
const form = reactive({ name: '', slug: '', sort: 0, status: 1 })

async function load() {
  loading.value = true
  try {
    const page: PageResult<TagVO> = await pageTags(query)
    rows.value = page.records
    total.value = Number(page.total)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.name = ''
  form.slug = ''
  form.sort = 0
  form.status = 1
  dialogVisible.value = true
}

function openEdit(row: TagVO) {
  editingId.value = row.id
  form.name = row.name
  form.slug = row.slug
  form.sort = row.sort ?? 0
  form.status = row.status ?? 1
  dialogVisible.value = true
}

async function save() {
  if (editingId.value) await updateTag(editingId.value, { ...form })
  else await createTag({ ...form })
  ElMessage.success('保存成功')
  dialogVisible.value = false
  await load()
}

async function remove(row: TagVO) {
  await ElMessageBox.confirm(`确认删除标签「${row.name}」？`, '提示', { type: 'warning' })
  try {
    await deleteTag(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-input v-model="query.name" placeholder="名称" style="width: 200px" clearable @change="load" />
      <el-button v-if="hasPermission('blog:tag:create')" type="primary" @click="openCreate">新建标签</el-button>
    </div>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="slug" label="slug" width="160" />
      <el-table-column prop="articleCount" label="引用数" width="100" />
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button v-if="hasPermission('blog:tag:update')" link @click="openEdit(row)">编辑</el-button>
          <el-button v-if="hasPermission('blog:tag:delete')" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="query.current"
      v-model:page-size="query.size"
      class="pager"
      layout="total, prev, pager, next"
      :total="total"
      @current-change="load"
    />

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑标签' : '新建标签'" width="420px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" maxlength="64" /></el-form-item>
        <el-form-item label="slug"><el-input v-model="form.slug" maxlength="64" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item>
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
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}
.pager {
  margin-top: 12px;
}
</style>
