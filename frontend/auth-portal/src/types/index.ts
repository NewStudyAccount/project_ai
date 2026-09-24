export interface Result<T> {
  code: number
  msg: string
  data: T
}

export interface LoginForm {
  username: string
  password: string
}

export interface LoginOutcome {
  ok: boolean
  message?: string
}
