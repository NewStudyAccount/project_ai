<template>
  <div class="page-card">
    <div class="toolbar">
      <template v-for="act in primaryActions" :key="act.key">
        <el-button v-if="actionVisible(act)" :type="act.type ?? 'default'" @click="runPrimary(act)">
          {{ act.label }}
        </el-button>
      </template>
      <span class="crud-toolbar-spacer" />
      <template v-for="f in schema.filters ?? []" :key="f.prop">
        <el-input
          v-if="f.type === 'input'"
          v-model="filterValues[f.prop]"
          :placeholder="f.placeholder ?? f.label ?? f.prop"
          clearable
          :style="{ width: (f.width ?? 160) + 'px' }"
          @keyup.enter="search"
          @clear="search"
        />
        <el-select
          v-else-if="f.type === 'select'"
          v-model="filterValues[f.prop]"
          :placeholder="f.placeholder ?? f.label ?? f.prop"
          clearable
          :style="{ width: (f.width ?? 120) + 'px' }"
          @change="search"
        >
          <el-option v-for="o in f.options ?? []" :key="String(o.value)" :label="o.label" :value="o.value" />
        </el-select>
        <el-date-picker
          v-else
          v-model="filterValues[f.prop]"
          type="datetimerange"
          value-format="YYYY-MM-DDTHH:mm:ss"
          start-placeholder="开始"
          end-placeholder="结束"
          :style="{ width: (f.width ?? 340) + 'px' }"
          @change="search"
        />
      </template>
      <el-button type="primary" @click="search">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="records" border :empty-text="schema.emptyText ?? '暂无数据'">
      <el-table-column
        v-for="col in schema.columns"
        :key="col.prop"
        :prop="col.prop"
        :label="col.label"
        :width="col.width"
        :min-width="col.minWidth"
        :fixed="col.fixed"
        :show-overflow-tooltip="col.tooltip"
      >
        <template #default="{ row }">
          <slot v-if="slots['col-' + col.prop]" :name="'col-' + col.prop" :row="row" />
          <span v-else-if="col.type === 'pill'" :class="['status-pill', pillClass(col, row)]">{{ cellText(col, row) }}</span>
          <span v-else-if="col.type === 'copy'" class="crud-mono">
            {{ cellText(col, row) }}
            <el-button v-if="cellText(col, row)" link @click="copyText(cellText(col, row))">复制</el-button>
          </span>
          <span v-else :class="{ 'crud-mono': col.type === 'datetime' }">{{ cellText(col, row) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        v-if="rowActions.length"
        label="操作"
        :width="Math.max(rowActions.length * 76, 120)"
        fixed="right"
      >
        <template #default="{ row }">
          <template v-for="act in rowActions" :key="act.key">
            <el-button
              v-if="actionVisible(act, row)"
              link
              :type="act.type === 'danger' ? 'danger' : 'default'"
              @click="runRowAction(act, row)"
            >
              {{ act.label }}
            </el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="pageState.current"
      v-model:page-size="pageState.size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, prev, pager, next, sizes"
      class="crud-pagination"
      @current-change="load"
      @size-change="onSizeChange"
    />

    <CrudForm
      v-if="schema.form"
      v-model="dialogVisible"
      :title="dialogTitle"
      :fields="schema.form"
      :values="formValues"
      :mode="formMode"
      :loading="saving"
      :width="schema.formWidth ?? 640"
      @save="save"
    />
  </div>
</template>

<script setup lang="ts" generic="Row extends object, F extends object">
import { computed, onMounted, reactive, ref, useSlots } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import CrudForm from '@/components/common/CrudForm.vue'
import { hasPermission } from '@/utils/permission'
import type { ActionDef, ColumnDef, CrudPageSchema, OptionItem } from '@/types/crud'

const props = defineProps<{ schema: CrudPageSchema<Row, F> }>()

const emit = defineEmits<{
  /** 自定义动作（无 handler 的行内/主操作），由页面接线 */
  'custom-action': [key: string, row: Row | undefined]
  /** handler 执行成功后的结果（如一次性密钥），由页面接弹窗 */
  'action-result': [key: string, row: Row | undefined, result: unknown]
  created: [result: unknown]
  updated: [result: unknown]
}>()

const slots = useSlots()

const records = ref<Row[]>([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref('')
const formValues = ref<Record<string, unknown>>({})

const pageState = reactive({ current: 1, size: 10 })
const filterValues = ref<Record<string, unknown>>({})

const rowKey = 'id'

const primaryActions = computed<ActionDef<Row>[]>(() => {
  if (props.schema.primaryActions) return props.schema.primaryActions
  return props.schema.api.create ? [{ key: 'create', label: '新建', type: 'primary' }] : []
})

const rowActions = computed<ActionDef<Row>[]>(() => {
  if (props.schema.rowActions) return props.schema.rowActions
  const acts: ActionDef<Row>[] = []
  if (props.schema.api.update && props.schema.form) acts.push({ key: 'update', label: '编辑' })
  if (props.schema.api.remove) {
    acts.push({ key: 'delete', label: '删除', type: 'danger', confirm: '确定删除该记录吗？删除后不可恢复。' })
  }
  return acts
})

const dialogTitle = computed(() =>
  formMode.value === 'create'
    ? (props.schema.createTitle ?? '新建')
    : (props.schema.editTitle ?? '编辑'),
)

function permissionOf(act: ActionDef<Row>): string | undefined {
  return act.permission ?? (props.schema.permissionPrefix ? `${props.schema.permissionPrefix}:${act.key}` : undefined)
}

function actionVisible(act: ActionDef<Row>, row?: Row): boolean {
  const perm = permissionOf(act)
  if (perm && !hasPermission(perm)) return false
  return row ? (act.visible?.(row) ?? true) : true
}

function buildQuery(): Record<string, unknown> {
  const q: Record<string, unknown> = { current: pageState.current, size: pageState.size }
  for (const f of props.schema.filters ?? []) {
    const v = filterValues.value[f.prop]
    if (f.type === 'dateRange') {
      const range = v as [string, string] | null
      q[f.prop] = range?.[0] ?? ''
      q[f.propEnd ?? `${f.prop}End`] = range?.[1] ?? ''
    } else {
      q[f.prop] = v ?? ''
    }
  }
  return q
}

async function load() {
  loading.value = true
  try {
    const page = await props.schema.api.page(buildQuery())
    records.value = page.records
    total.value = page.total
    // 删空回退一页：当前页无数据且不在首页时回退重查
    if (!page.records.length && pageState.current > 1) {
      pageState.current -= 1
      return await load()
    }
  } finally {
    loading.value = false
  }
}

function search() {
  pageState.current = 1
  void load()
}

function onSizeChange() {
  pageState.current = 1
  void load()
}

function raw(row: Row): Record<string, unknown> {
  return row as unknown as Record<string, unknown>
}

function cellText(col: ColumnDef<Row>, row: Row): string {
  if (col.formatter) return col.formatter(row)
  const value = raw(row)[col.prop]
  const opt = col.options?.find((o) => o.value === value)
  if (opt) return opt.label
  return value === undefined || value === null ? '' : String(value)
}

function optionOf(col: ColumnDef<Row>, row: Row): OptionItem | undefined {
  return col.options?.find((o) => o.value === raw(row)[col.prop])
}

function pillClass(col: ColumnDef<Row>, row: Row): string {
  const tone = optionOf(col, row)?.pillTone
  return tone === 'danger' ? 'is-bad' : tone === 'warning' ? 'is-off' : 'is-on'
}

async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success({ message: '已复制', duration: 2000 })
  } catch {
    ElMessage.error('复制失败，请手动选择文本复制')
  }
}

function openCreate() {
  formMode.value = 'create'
  editingId.value = ''
  formValues.value = { ...(props.schema.formDefaults ?? {}) }
  dialogVisible.value = true
}

function openEdit(row: Row) {
  formMode.value = 'edit'
  editingId.value = String(raw(row)[rowKey] ?? '')
  const values: Record<string, unknown> = { ...(props.schema.formDefaults ?? {}) }
  for (const field of props.schema.form ?? []) {
    values[field.prop] = raw(row)[field.prop]
  }
  formValues.value = values
  dialogVisible.value = true
}

async function save(values: Record<string, unknown>) {
  if (saving.value) return
  saving.value = true
  try {
    const data = values as F
    const result =
      formMode.value === 'create'
        ? await props.schema.api.create?.(data)
        : await props.schema.api.update?.(editingId.value, data)
    dialogVisible.value = false
    ElMessage.success({ message: '保存成功', duration: 2000 })
    await load()
    if (formMode.value === 'create') {
      emit('created', result)
    } else {
      emit('updated', result)
    }
  } finally {
    saving.value = false
  }
}

async function removeRow(row: Row, act: ActionDef<Row>) {
  await props.schema.api.remove?.(String(raw(row)[rowKey] ?? ''))
  ElMessage.success({ message: act.successMessage ?? '删除成功', duration: 2000 })
  await load()
}

async function runHandler(act: ActionDef<Row>, row: Row) {
  const result = await act.handler!(row)
  if (act.successMessage) ElMessage.success({ message: act.successMessage, duration: 2000 })
  await load()
  emit('action-result', act.key, row, result)
}

async function runRowAction(act: ActionDef<Row>, row: Row) {
  const isDelete = act.key === 'delete' && props.schema.api.remove && !act.handler
  const isUpdate = act.key === 'update' && props.schema.form && props.schema.api.update && !act.handler
  const needsConfirm = Boolean(act.confirm) || isDelete

  if (needsConfirm) {
    const text = act.confirm
      ? typeof act.confirm === 'function'
        ? act.confirm(row)
        : act.confirm
      : '确定删除该记录吗？删除后不可恢复。'
    try {
      await ElMessageBox.confirm(text, act.label, { type: 'warning' })
    } catch {
      return
    }
  }

  if (isDelete) {
    await removeRow(row, act)
  } else if (isUpdate) {
    openEdit(row)
  } else if (act.handler) {
    await runHandler(act, row)
  } else {
    emit('custom-action', act.key, row)
  }
}

async function runPrimary(act: ActionDef<Row>) {
  if (act.key === 'create' && props.schema.form && props.schema.api.create) {
    openCreate()
    return
  }
  emit('custom-action', act.key, undefined)
}

onMounted(load)

/** 页面级自定义动作完成后刷新列表 */
defineExpose({ reload: load, search })
</script>

<style scoped>
.crud-toolbar-spacer {
  flex: 1;
}

.crud-pagination {
  margin-top: 12px;
  justify-content: flex-end;
}

.crud-mono {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
}
</style>
