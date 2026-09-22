/** 统一 API / 模型类型；主键一律 string（后端 Long→String）。 */

export interface ApiResult<T> {
  code: number
  msg: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number | string
  size: number | string
  current: number | string
}

export interface Role {
  id: string
  systemCode: string
  roleCode: string
  roleName: string
  sort: number
  status: number
  remark: string
}

export interface Permission {
  id: string
  systemCode: string
  parentId: string
  permissionType: number
  permissionCode: string
  permissionName: string
  sort: number
  status: number
}

export interface UserRef {
  userId: string
  username: string
  realName: string
  status: number
  syncTime?: string
}

/** 与后端 BlogPermissions 对齐 */
export const BlogPermissions = {
  POST_CREATE: 'blog:post:create',
  POST_PUBLISH: 'blog:post:publish',
  POST_MANAGE: 'blog:post:manage',
  CATEGORY_MANAGE: 'blog:category:manage',
  TAG_MANAGE: 'blog:tag:manage',
  COMMENT_MODERATE: 'blog:comment:moderate',
  ROLE_MANAGE: 'blog:rbac:role',
  USER_ROLE_MANAGE: 'blog:rbac:user-role',
} as const
