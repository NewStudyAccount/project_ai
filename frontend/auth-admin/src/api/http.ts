import axios from 'axios'
import type { ApiResult } from '../types'
import { getAccessToken, getRefreshToken, saveTokens, clearTokens } from '../utils/token'

export const http = axios.create({
  baseURL: '/',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const at = getAccessToken()
  if (at) config.headers.Authorization = `Bearer ${at}`
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
  async (error) => {
    return Promise.reject(error)
  },
)

export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const res = await http.get<ApiResult<T>>(url, { params })
  return res.data.data
}

export async function post<T>(url: string, data?: unknown, params?: Record<string, unknown>): Promise<T> {
  const res = await http.post<ApiResult<T>>(url, data, { params })
  return res.data.data
}

export async function put<T>(url: string, data?: unknown): Promise<T> {
  const res = await http.put<ApiResult<T>>(url, data)
  return res.data.data
}
