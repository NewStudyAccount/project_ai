/**
 * 统一 Result 解包请求封装（CLAUDE.md 6.4.1 / 8.5）。
 * 业务组件只调用 src/api，不直接使用 axios。
 */
import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { router } from '@/router'
import type { Result } from '@/types'

const instance = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
  withCredentials: true,
})

instance.interceptors.request.use((config) => {
  config.headers.set('X-Request-Id', window.crypto.randomUUID())
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

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
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
      await router.push('/login')
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
      await router.push('/login')
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
