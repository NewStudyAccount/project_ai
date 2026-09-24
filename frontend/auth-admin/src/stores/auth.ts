import { defineStore } from 'pinia'
import { rbacApi } from '@/api/rbac'
import type { MenuVO } from '@/types'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    initialized: false,
    menus: [] as MenuVO[],
    permissions: [] as string[],
  }),
  actions: {
    async init() {
      if (this.initialized) return
      const [menus, permissions] = await Promise.all([rbacApi.myMenus(), rbacApi.myPermissions()])
      this.menus = menus
      this.permissions = permissions
      this.initialized = true
    },
    reset() {
      this.initialized = false
      this.menus = []
      this.permissions = []
    },
  },
})
