import axios from 'axios'

const client = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
})

export interface ApiResult<T> {
  code: number
  msg: string
  data: T
}

export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const res = await client.get<ApiResult<T>>(url, { params })
  if (res.data.code !== 0) {
    throw new Error(res.data.msg || '请求失败')
  }
  return res.data.data
}
