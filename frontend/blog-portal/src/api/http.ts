import axios from 'axios'
import type { ApiResult } from '@/types'

/** 公开 API 客户端（匿名可读）。 */
export const http = axios.create({
  baseURL: '',
  timeout: 15000,
})

http.interceptors.response.use((response) => {
  const body = response.data as ApiResult<unknown>
  if (body && typeof body.code === 'number' && body.code !== 0) {
    return Promise.reject(new Error(body.msg || '请求失败'))
  }
  return response
})

export async function getPublic<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const res = await http.get<ApiResult<T>>(url, { params })
  return res.data.data
}
