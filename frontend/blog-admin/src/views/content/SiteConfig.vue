<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getSiteConfig, saveSiteConfig } from '@/api/content'
import { hasPermission } from '@/utils/permission'

const form = reactive({
  title: '',
  description: '',
  copyright: '',
  about: '',
})
const saving = ref(false)

async function load() {
  const conf = await getSiteConfig()
  form.title = conf.title || ''
  form.description = conf.description || ''
  form.copyright = conf.copyright || ''
  form.about = conf.about || ''
}

async function save() {
  saving.value = true
  try {
    await saveSiteConfig({
      'site.title': form.title,
      'site.description': form.description,
      'site.copyright': form.copyright,
      'site.about': form.about,
    })
    ElMessage.success('已保存')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-form :model="form" label-width="100px" class="form">
      <el-form-item label="站点标题"><el-input v-model="form.title" maxlength="100" /></el-form-item>
      <el-form-item label="站点描述"><el-input v-model="form.description" type="textarea" :rows="2" maxlength="1024" /></el-form-item>
      <el-form-item label="版权/备案"><el-input v-model="form.copyright" maxlength="255" /></el-form-item>
      <el-form-item label="关于"><el-input v-model="form.about" type="textarea" :rows="6" maxlength="1024" /></el-form-item>
      <el-form-item>
        <el-button v-if="hasPermission('blog:site:update')" type="primary" :loading="saving" @click="save">保存</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped>
.page {
  padding: 16px;
}
.form {
  max-width: 640px;
}
</style>
