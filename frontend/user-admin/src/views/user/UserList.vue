<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input v-model="query.username" placeholder="用户名" clearable style="width: 220px" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
        <el-option label="正常" :value="1" />
        <el-option label="停用" :value="0" />
        <el-option label="锁定" :value="2" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
      <el-button v-if="hasPermission('user:user:create')" type="primary" @click="openCreate">新建</el-button>
    </div>
    <el-table :data="records" border>
      <el-table-column prop="id" label="ID" width="180" />
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="realName" label="姓名" width="120" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column prop="email" label="邮箱" min-width="180" />
      <el-table-column prop="status" label="状态" width="90" />
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button v-if="hasPermission('user:user:update')" link @click="openEdit(row)">编辑</el-button>
          <el-button v-if="hasPermission('user:user:status')" link @click="toggleStatus(row)">
            {{ row.status === 1 ? '停用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="query.current"
      v-model:page-size="query.size"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="load"
    />
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑用户' : '新建用户'">
      <el-form :model="form" label-width="90px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" :disabled="editing" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { userApi, type UserInput } from '@/api/user'
import { hasPermission } from '@/utils/permission'
import type { UserVO } from '@/types'

const records = ref<UserVO[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const editing = ref(false)
const editingId = ref('')
const query = reactive({ current: 1, size: 10, username: '', status: undefined as number | undefined })
const form = reactive<UserInput>({ username: '', realName: '', nickname: '', phone: '', email: '', remark: '' })

async function load() {
  const page = await userApi.page(query)
  records.value = page.records
  total.value = page.total
}

function openCreate() {
  editing.value = false
  editingId.value = ''
  Object.assign(form, { username: '', realName: '', nickname: '', phone: '', email: '', remark: '' })
  dialogVisible.value = true
}

function openEdit(row: UserVO) {
  editing.value = true
  editingId.value = row.id
  Object.assign(form, {
    username: row.username,
    realName: row.realName,
    nickname: row.nickname,
    phone: row.phone,
    email: row.email,
    remark: row.remark,
  })
  dialogVisible.value = true
}

async function save() {
  if (editing.value) {
    await userApi.update(editingId.value, form)
  } else {
    await userApi.create(form)
  }
  dialogVisible.value = false
  await load()
}

async function toggleStatus(row: UserVO) {
  await userApi.updateStatus(row.id, row.status === 1 ? 0 : 1)
  await load()
}

onMounted(load)
</script>
