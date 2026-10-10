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
    const response = await instance.request<Result<T>>(config)
    const result = response.data
    if (result.code === 0) {
      return result.data
    }
    if (result.code === 10001) {
      clearTokens()
      await redirectForLogin(window.location.pathname + window.location.search)
    } else if (result.code === 10002) {
      ElMessage.error('无权限')
    } else {
      ElMessage.error(result.msg || '请求失败')
    }
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
  post: <T>(url: string, data?: unknown) => request<T>({ method: 'POST', url, data }),
  put: <T>(url: string, data?: unknown) => request<T>({ method: 'PUT', url, data }),
  delete: <T>(url: string, data?: unknown) => request<T>({ method: 'DELETE', url, data }),
}
