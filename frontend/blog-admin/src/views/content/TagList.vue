<template>
  <CrudPage :schema="schema" />
</template>

<script setup lang="ts">
import CrudPage from '@/components/common/CrudPage.vue'
import { buildTagSchema } from './tag.config'

const schema = buildTagSchema()
</script>
