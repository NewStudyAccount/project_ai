import { defineStore } from 'pinia'
import { rbacApi } from '@/api/rbac'
import { getTokens } from '@/utils/oidc'
import type { MenuVO } from '@/types'

function preferredUsername(): string {
  const idToken = getTokens()?.idToken
  if (!idToken) return '当前操作员'
  try {
    const payload = JSON.parse(atob(idToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))) as {
      preferred_username?: string
    }
    return payload.preferred_username || '当前操作员'
  } catch {
    return '当前操作员'
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    initialized: false,
    operatorName: preferredUsername(),
    menus: [] as MenuVO[],
    permissions: [] as string[],
  }),
  actions: {
    async init() {
      if (this.initialized) return
      const [menus, permissions] = await Promise.all([rbacApi.myMenus(), rbacApi.myPermissions()])
      this.menus = menus
      this.permissions = permissions
      this.operatorName = preferredUsername()
      this.initialized = true
    },
    reset() {
      this.initialized = false
      this.menus = []
      this.permissions = []
    },
  },
})
