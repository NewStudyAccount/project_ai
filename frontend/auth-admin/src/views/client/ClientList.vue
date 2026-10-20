<template>
  <CrudPage :schema="schema" @created="onCreated" @action-result="onActionResult" />
  <SecretDialog :visible="secretVisible" :secret="secret" @close="secretVisible = false" />
</template>

<script setup lang="ts">
import { ref } from 'vue'
import CrudPage from '@/components/common/CrudPage.vue'
import SecretDialog from '@/components/SecretDialog.vue'
import { buildClientSchema } from './client.config'
import type { ClientSecretVO } from '@/types'

const schema = buildClientSchema()

const secretVisible = ref(false)
const secret = ref<ClientSecretVO>({ id: '', clientId: '', clientSecret: '' })

/** 创建 / 重置密钥返回的一次性密钥，打码面板展示 */
function showSecret(result: unknown) {
  const s = result as ClientSecretVO | undefined
  if (s && s.clientSecret) {
    secret.value = s
    secretVisible.value = true
  }
}

function onCreated(result: unknown) {
  showSecret(result)
}

function onActionResult(key: string, _row: unknown, result: unknown) {
  if (key === 'reset-secret') showSecret(result)
}
</script>
