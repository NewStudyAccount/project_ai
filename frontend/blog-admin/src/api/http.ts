import axios from 'axios'
import type { ApiResult, PageResult } from '@/types'
import { clearTokens, getAccessToken } from '@/utils/token'

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

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResult<unknown>
    if (body && typeof body.code === 'number' && body.code !== 0) {
      return Promise.reject(new Error(body.msg || '请求失败'))
    }
    return response
  },
  (error) => {
    if (error.response?.status === 401) {
      clearTokens()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const res = await http.get<ApiResult<T>>(url, { params })
  return res.data.data
}

export async function post<T>(url: string, body?: unknown): Promise<T> {
  const res = await http.post<ApiResult<T>>(url, body)
  return res.data.data
}

export async function del<T = void>(url: string, params?: Record<string, unknown>): Promise<T> {
  const res = await http.delete<ApiResult<T>>(url, { params })
  return res.data.data
}

export async function getPage<T>(url: string, params?: Record<string, unknown>): Promise<PageResult<T>> {
  return get<PageResult<T>>(url, params)
}
