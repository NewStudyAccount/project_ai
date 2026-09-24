import { useAuthStore } from '@/stores/auth'

/** 按钮级权限：标识格式 system:resource:action（CLAUDE.md 5.2） */
export function hasPermission(permission: string): boolean {
  return useAuthStore().permissions.includes(permission)
}
