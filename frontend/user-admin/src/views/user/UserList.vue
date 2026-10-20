<template>
  <CrudPage :schema="schema" />
</template>

<script setup lang="ts">
import CrudPage from '@/components/common/CrudPage.vue'
import { buildUserSchema } from './user.config'

const schema = buildUserSchema()
</script>
