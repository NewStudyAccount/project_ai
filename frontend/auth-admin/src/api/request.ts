/**
 * 统一 Result 解包请求封装（CLAUDE.md 6.4.1）。
 * 业务组件只调用 src/api；携带 Bearer AT，过期走 RT 轮转刷新。
 */
import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { Result } from '@/types'
import { clearTokens, getTokens, hasValidAccessToken, redirectForLogin, refreshTokens } from '@/utils/oidc'

const instance = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
})

instance.interceptors.request.use((config) => {
  config.headers.set('X-Request-Id', window.crypto.randomUUID())
  const tokens = getTokens()
  if (tokens?.accessToken) {
    config.headers.set('Authorization', `Bearer ${tokens.accessToken}`)
  }
  return config
})

function dropEmpty(params?: object): object | undefined {
  if (!params) return undefined
  const out: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(params as Record<string, unknown>)) {
    if (value === undefined || value === null || value === '') continue
    out[key] = value
  }
  return out
}

async function ensureAccessToken(): Promise<boolean> {
  if (hasValidAccessToken()) return true
  const refreshed = await refreshTokens()
  return !!refreshed?.accessToken
}

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  if (!(await ensureAccessToken())) {
    clearTokens()
    await redirectForLogin(window.location.pathname + window.location.search)
    throw new Error('未认证')
  }
  try {
    const response = await instance.request<Result<T>>({
      ...config,
      params: dropEmpty(config.params),
    })
    const result = response.data
    if (result.code === 0) {
      return result.data
    }
    if (result.code === 10001) {
      clearTokens()
      await redirectForLogin(window.location.pathname + window.location.search)
      throw new Error(result.msg || '未认证')
    }
    if (result.code === 10002) {
      ElMessage.error('无权限')
      throw new Error(result.msg || '无权限')
    }
    ElMessage.error(result.msg || '请求失败')
    throw new Error(result.msg || '请求失败')
  } catch (error) {
    const status = axios.isAxiosError(error) ? error.response?.status : undefined
    if (status === 401) {
      clearTokens()
      await redirectForLogin(window.location.pathname + window.location.search)
    }
    throw error
  }
}

export const http = {
  get: <T>(url: string, params?: object) => request<T>({ method: 'GET', url, params }),
  post: <T>(url: string, data?: unknown, params?: object) => request<T>({ method: 'POST', url, data, params }),
  put: <T>(url: string, data?: unknown) => request<T>({ method: 'PUT', url, data }),
  delete: <T>(url: string) => request<T>({ method: 'DELETE', url }),
}
