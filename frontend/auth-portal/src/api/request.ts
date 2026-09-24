/**
 * 统一 Result 解包请求封装。
 * 业务组件只调用 src/api，不直接使用 axios。
 */
import axios, { type AxiosRequestConfig } from 'axios'
import type { Result } from '@/types'

const instance = axios.create({
  timeout: 10000,
  withCredentials: true,
})

instance.interceptors.request.use((config) => {
  config.headers.set('X-Request-Id', window.crypto.randomUUID())
  return config
})

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await instance.request<Result<T>>(config)
  const result = response.data
  if (result.code === 0) {
    return result.data
  }
  throw new Error(result.msg || '请求失败')
}

export const http = {
  get: <T>(url: string, params?: object) => request<T>({ method: 'GET', url, params }),
  post: <T>(url: string, data?: unknown) => request<T>({ method: 'POST', url, data }),
  put: <T>(url: string, data?: unknown) => request<T>({ method: 'PUT', url, data }),
  delete: <T>(url: string, data?: unknown) => request<T>({ method: 'DELETE', url, data }),
}
