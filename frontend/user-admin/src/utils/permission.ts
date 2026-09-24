import { useAuthStore } from '@/stores/auth'

export function hasPermission(permission: string): boolean {
  return useAuthStore().permissions.includes(permission)
}
