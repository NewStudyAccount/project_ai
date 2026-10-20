<template>
  <el-dialog :model-value="modelValue" :title="title" width="560px" @update:model-value="emit('update:modelValue', $event)">
    <div class="kv-list">
      <template v-for="row in rows" :key="row.key">
        <div class="kv-key">{{ row.label }}</div>
        <div class="kv-val">{{ row.value ?? '—' }}</div>
      </template>
    </div>
    <template #footer>
      <el-button type="primary" @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
export interface DetailRow {
  key: string
  label: string
  value?: string | number | null
}

defineProps<{
  modelValue: boolean
  title: string
  rows: DetailRow[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()
</script>

<style scoped>
.kv-list {
  display: grid;
  grid-template-columns: 96px 1fr;
  row-gap: 12px;
  column-gap: 12px;
  font-size: 13px;
}

.kv-key {
  color: var(--text-secondary);
  text-align: right;
}

.kv-val {
  color: var(--text-regular);
  word-break: break-all;
}
</style>
