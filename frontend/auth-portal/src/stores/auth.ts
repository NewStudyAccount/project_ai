import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as apiLogin, logout as apiLogout } from '../api/auth'
import type { LoginResponse } from '../types'

export const useAuthStore = defineStore('auth', () => {
  const session = ref<LoginResponse | null>(null)

  async function doLogin(username: string, password: string) {
    session.value = await apiLogin(username, password)
    return session.value
  }

  async function doLogout() {
    await apiLogout()
    session.value = null
  }

  return { session, doLogin, doLogout }
})
