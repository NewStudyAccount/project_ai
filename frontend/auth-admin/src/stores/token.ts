import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getAccessToken, clearTokens } from '../utils/token'

export const useTokenStore = defineStore('token', () => {
  const accessToken = ref<string | null>(getAccessToken())
  const isAuthenticated = computed(() => !!accessToken.value)

  function setToken(at: string) {
    accessToken.value = at
  }

  function clear() {
    accessToken.value = null
    clearTokens()
  }

  return { accessToken, isAuthenticated, setToken, clear }
})
