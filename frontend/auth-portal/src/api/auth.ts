import axios from 'axios'
import { clearTokens, getAccessToken, getRefreshToken, setAccessToken, setRefreshToken } from '@/utils/token'

export interface ApiResult<T> {
  code: number
  msg: string
  data: T
}

export interface TokenVo {
  accessToken: string
  refreshToken: string
  accessExpiresIn: number
  userId: number | string | null
  username: string
  realName: string
}

export const http = axios.create({
  baseURL: '',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let refreshing: Promise<boolean> | null = null

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResult<unknown>
    if (body && typeof body.code === 'number' && body.code !== 0) {
      return Promise.reject(new Error(body.msg || '请求失败'))
    }
    return response
  },
  async (error) => {
    const original = error.config as { _retry?: boolean; url?: string } | undefined
    if (error.response?.status === 401 && original && !original._retry && original.url !== '/auth/refresh') {
      original._retry = true
      refreshing =
        refreshing ||
        tryRefresh().finally(() => {
          refreshing = null
        })
      const ok = await refreshing
      if (ok) {
        return http.request(original as never)
      }
    }
    return Promise.reject(error)
  },
)

async function tryRefresh(): Promise<boolean> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    clearTokens()
    return false
  }
  try {
    const res = await axios.post<ApiResult<TokenVo>>('/auth/refresh', { refreshToken })
    const data = res.data.data
    setAccessToken(data.accessToken)
    setRefreshToken(data.refreshToken)
    return true
  } catch {
    clearTokens()
    return false
  }
}

export async function login(username: string, password: string): Promise<TokenVo> {
  const res = await http.post<ApiResult<TokenVo>>('/auth/login', { username, password })
  const data = res.data.data
  setAccessToken(data.accessToken)
  setRefreshToken(data.refreshToken)
  return data
}

export async function logout(): Promise<void> {
  const refreshToken = getRefreshToken()
  try {
    await http.post('/auth/logout', refreshToken ? { refreshToken } : {})
  } catch {
    // 登出失败仍清理本地
  } finally {
    clearTokens()
  }
}

export async function ssoToken(code: string, clientId: string): Promise<TokenVo> {
  const res = await http.post<ApiResult<TokenVo>>('/auth/sso/token', { code, clientId })
  return res.data.data
}
