import { defineStore } from 'pinia'
import { login as loginApi } from '@/api/auth'
import type { LoginOutcome } from '@/types'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    loading: false,
  }),
  actions: {
    async login(username: string, password: string): Promise<LoginOutcome> {
      this.loading = true
      try {
        return await loginApi(username, password)
      } finally {
        this.loading = false
      }
    },
  },
})
