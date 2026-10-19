export interface Result<T> {
  code: number
  msg: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

export interface FileMeta {
  id: string
  bucket: string
  objectKey: string
  /** 稳定公开 URL */
  url?: string
  originalName: string
  contentType: string
  sizeBytes: number
  deleted: number
  createBy?: string
  createTime?: string
}

export interface FileUrlResult {
  url: string
}

export interface MenuVO {
  id: string
  parentId: string
  type: number
  name: string
  permission: string
  path: string
  component: string
  icon: string
  hidden: number
  requiresAuth: number
  sort: number
  status: number
  children: MenuVO[]
}

export interface RoleVO {
  id: string
  roleCode: string
  roleName: string
  dataScope: number
  sort: number
  status: number
  remark: string
  menuIds: string[]
}

export interface AuditVO {
  id: string
  action: string
  actorUserId: string
  targetType: string
  targetId: string
  detail: string
  ip: string
  createTime: string
}

export interface UserOption {
  id: string
  username: string
  displayName: string
}
