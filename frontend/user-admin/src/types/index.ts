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

export interface UserVO {
  id: string
  username: string
  realName: string
  nickname: string
  email: string
  phone: string
  avatar: string
  status: number
  remark: string
  gender: number
  birthday?: string
  address: string
  extraJson: string
  createTime?: string
  updateTime?: string
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
  targetUserId: string
  detail: string
  ip: string
  createTime: string
}
