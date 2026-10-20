<template>
  <CrudPage :schema="schema" @custom-action="onCustomAction">
    <!-- 结果列：软胶囊（沿用 action === 'LOGIN_SUCCESS' 既有语义） -->
    <template #col-action="{ row }">
      <span :class="['status-pill', row.action === 'LOGIN_SUCCESS' ? 'is-on' : 'is-bad']">
        {{ row.action === 'LOGIN_SUCCESS' ? '成功' : '失败' }}
      </span>
    </template>
  </CrudPage>
  <DetailDialog v-model="detailVisible" title="登录尝试详情" :rows="detailRows" />
</template>

<script setup lang="ts">
import { ref } from 'vue'
import CrudPage from '@/components/common/CrudPage.vue'
import DetailDialog from '@/components/common/DetailDialog.vue'
import { buildLoginAttemptSchema, loginAttemptDetailRows } from './login-attempt.config'
import type { AuditVO } from '@/types'

const schema = buildLoginAttemptSchema()

const detailVisible = ref(false)
const detailRows = ref<ReturnType<typeof loginAttemptDetailRows>>([])

function onCustomAction(key: string, row?: AuditVO) {
  if (key === 'detail' && row) {
    detailRows.value = loginAttemptDetailRows(row)
    detailVisible.value = true
  }
}
</script>
