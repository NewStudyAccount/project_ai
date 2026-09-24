import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { router } from '@/router'
import type { Result } from '@/types'

const instance = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
})

instance.interceptors.request.use((config) => {
  const requestId = window.crypto.randomUUID()
  config.headers.set('X-Request-Id', requestId)
  return config
})

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await instance.request<Result<T>>(config)
  const result = response.data
  if (result.code === 0) {
    return result.data
  }
  if (result.code === 10001) {
    await router.push('/login')
  } else if (result.code === 10002) {
    ElMessage.error('无权限')
  } else {
    ElMessage.error(result.msg || '请求失败')
  }
  throw new Error(result.msg || '请求失败')
}

export const http = {
  get: <T>(url: string, params?: object) => request<T>({ method: 'GET', url, params }),
  post: <T>(url: string, data?: unknown) => request<T>({ method: 'POST', url, data }),
  put: <T>(url: string, data?: unknown) => request<T>({ method: 'PUT', url, data }),
  delete: <T>(url: string, data?: unknown) => request<T>({ method: 'DELETE', url, data }),
}
