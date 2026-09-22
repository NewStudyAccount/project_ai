/** 统一返回体 */
export interface ApiResult<T> {
  code: number
  msg: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: string | number
  size: string | number
  current: string | number
}

export interface LoginResponse {
  sessionToken: string
  userId: string
  username: string
}
