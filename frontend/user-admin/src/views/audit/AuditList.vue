<template>
  <CrudPage :schema="schema" @custom-action="onCustomAction" />
  <DetailDialog v-model="detailVisible" title="审计详情" :rows="detailRows" />
</template>

<script setup lang="ts">
import { ref } from 'vue'
import CrudPage from '@/components/common/CrudPage.vue'
import DetailDialog from '@/components/common/DetailDialog.vue'
import { auditDetailRows, buildAuditSchema } from './audit.config'
import type { AuditVO } from '@/types'

const schema = buildAuditSchema()

const detailVisible = ref(false)
const detailRows = ref<ReturnType<typeof auditDetailRows>>([])

function onCustomAction(key: string, row?: AuditVO) {
  if (key === 'detail' && row) {
    detailRows.value = auditDetailRows(row)
    detailVisible.value = true
  }
}
</script>
