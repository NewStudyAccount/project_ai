<template>
  <CrudPage ref="crudRef" :schema="schema" @custom-action="onCustomAction" />

  <!-- 吊销需填写原因：D-Form 弹窗（custom-action 接线） -->
  <el-dialog v-model="revokeVisible" :title="revokeUserMode ? '踢下线（按用户吊销）' : '吊销授权'" width="480px">
    <p v-if="revokeUserMode">将吊销用户 {{ targetUserId }} 的全部有效授权并失效 SSO 会话。</p>
    <el-form label-width="80px">
      <el-form-item label="原因">
        <el-input v-model="reason" type="textarea" placeholder="吊销原因（可选）" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="revokeVisible = false">取消</el-button>
      <el-button type="danger" :loading="revoking" @click="confirmRevoke">确认吊销</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import CrudPage from '@/components/common/CrudPage.vue'
import { grantApi } from '@/api/grant'
import { buildGrantSchema } from './grant.config'
import type { GrantVO } from '@/types'

interface CrudPageExpose {
  reload: () => Promise<void>
}

const schema = buildGrantSchema()
const crudRef = ref<CrudPageExpose>()

const revokeVisible = ref(false)
const revokeUserMode = ref(false)
const revoking = ref(false)
const targetGrantId = ref('')
const targetUserId = ref('')
const reason = ref('')

function onCustomAction(key: string, row?: GrantVO) {
  if (!row) return
  if (key === 'revoke') {
    revokeUserMode.value = false
    targetGrantId.value = row.id
    targetUserId.value = row.userId
    reason.value = ''
    revokeVisible.value = true
  } else if (key === 'revoke-user') {
    revokeUserMode.value = true
    targetGrantId.value = ''
    targetUserId.value = row.userId
    reason.value = ''
    revokeVisible.value = true
  }
}

async function confirmRevoke() {
  if (revoking.value) return
  revoking.value = true
  try {
    if (revokeUserMode.value) {
      await grantApi.revokeByUser(targetUserId.value, reason.value)
    } else {
      await grantApi.revoke(targetGrantId.value, reason.value)
    }
    ElMessage.success({ message: '已吊销', duration: 2000 })
    revokeVisible.value = false
    await crudRef.value?.reload()
  } finally {
    revoking.value = false
  }
}
</script>
