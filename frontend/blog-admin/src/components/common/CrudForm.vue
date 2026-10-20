<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    :width="width"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="local" :rules="rules" label-width="96px">
      <el-row :gutter="16">
        <el-col v-for="field in visibleFields" :key="field.prop" :span="field.span === 2 ? 24 : 24">
          <el-form-item :label="field.label" :prop="field.prop">
            <el-input
              v-if="field.type === 'input'"
              v-model="local[field.prop]"
              :placeholder="field.placeholder"
              :disabled="isDisabled(field)"
            />
            <el-input
              v-else-if="field.type === 'textarea'"
              v-model="local[field.prop]"
              type="textarea"
              :rows="3"
              :placeholder="field.placeholder"
              :disabled="isDisabled(field)"
            />
            <el-input-number
              v-else-if="field.type === 'number'"
              v-model="local[field.prop] as number"
              :min="field.min"
              :max="field.max"
              :disabled="isDisabled(field)"
              controls-position="right"
            />
            <el-select
              v-else-if="field.type === 'select'"
              v-model="local[field.prop]"
              :placeholder="field.placeholder"
              :disabled="isDisabled(field)"
              style="width: 100%"
            >
              <el-option v-for="o in field.options ?? []" :key="String(o.value)" :label="o.label" :value="o.value" />
            </el-select>
            <el-switch
              v-else-if="field.type === 'switch'"
              v-model="local[field.prop]"
              :active-value="1"
              :inactive-value="0"
              :disabled="isDisabled(field)"
            />
            <el-date-picker
              v-else-if="field.type === 'date'"
              v-model="local[field.prop]"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              :placeholder="field.placeholder ?? '选择时间'"
              :disabled="isDisabled(field)"
            />
            <div v-if="field.hint" class="field-hint">{{ field.hint }}</div>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { FormFieldDef } from '@/types/crud'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    fields: FormFieldDef[]
    /** 表单初值（由父级持有，字段 prop 对齐后端 DTO）；组件内部持副本编辑 */
    values: Record<string, unknown>
    mode: 'create' | 'edit'
    loading?: boolean
    width?: number
  }>(),
  { loading: false, width: 640 },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  save: [values: Record<string, unknown>]
}>()

const formRef = ref<FormInstance>()

/** 本地副本：避免直接修改 props.values */
const local = ref<Record<string, unknown>>({})

const visibleFields = computed(() => props.fields.filter((f) => !f.hiddenWhen?.(props.mode)))

function isDisabled(field: FormFieldDef): boolean {
  return field.disabled === true || field.disabledWhen?.(props.mode) === true
}

const rules = computed<FormRules>(() => {
  const out: FormRules = {}
  for (const field of visibleFields.value) {
    if (field.required) {
      out[field.prop] = [
        {
          required: true,
          message: field.message ?? (field.type === 'select' ? `请选择${field.label}` : `请输入${field.label}`),
          trigger: field.type === 'select' || field.type === 'switch' || field.type === 'date' ? 'change' : 'blur',
        },
      ]
    }
  }
  return out
})

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      local.value = { ...props.values }
      formRef.value?.clearValidate()
    }
  },
)

async function submit() {
  const form = formRef.value
  if (!form) return
  const valid = await form.validate().catch(() => false)
  if (!valid) return
  emit('save', { ...local.value })
}
</script>

<style scoped>
.field-hint {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
}
</style>
