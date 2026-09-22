import axios from 'axios'
import type { ApiResult } from '../types'

export const http = axios.create({
  baseURL: '/',
  timeout: 15000,
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
    return Promise.reject(error)
  },
)

export async function post<T>(url: string, data?: unknown): Promise<T> {
  const res = await http.post<ApiResult<T>>(url, data)
  return res.data.data
}
