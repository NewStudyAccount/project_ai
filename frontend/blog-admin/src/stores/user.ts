import { defineStore } from "pinia"
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const userId = ref('')
  const username = ref('')
  const permissions = ref<string[]>([])

  function setUser(id: string, name: string) {
    userId.value = id
    username.value = name
  }

  function setPermissions(codes: string[]) {
    permissions.value = codes
  }

  function hasPermission(code: string): boolean {
    return permissions.value.includes(code)
  }

  function reset() {
    userId.value = ''
    username.value = ''
    permissions.value = []
  }

  return { userId, username, permissions, setUser, setPermissions, hasPermission, reset }
})
