import { http } from '@/api/request'
import type {
  PageResult,
  StorageProviderInput,
  StorageProviderPageQuery,
  StorageProviderStatusInput,
  StorageProviderVO,
} from '@/types'

/**
 * 对象存储服务商配置 API（对齐 docs/object-storage-design.md §8.2 / 后端 blog-storage-provider 契约）。
 * 路径前缀 /api/v1 由 request.ts 的 baseURL 统一注入；id 一律 String；SK 经出参掩码。
 */
export const storageProviderApi = {
  page: (params: StorageProviderPageQuery) =>
    http.get<PageResult<StorageProviderVO>>('/storage/providers', params),
  get: (id: string) => http.get<StorageProviderVO>(`/storage/providers/${id}`),
  create: (data: StorageProviderInput) => http.post<StorageProviderVO>('/storage/providers', data),
  update: (id: string, data: StorageProviderInput) =>
    http.put<StorageProviderVO>(`/storage/providers/${id}`, data),
  delete: (id: string) => http.delete<void>(`/storage/providers/${id}`),
  testConnection: (id: string) => http.post<void>(`/storage/providers/${id}/test`),
  setDefault: (id: string) => http.post<void>(`/storage/providers/${id}/default`),
  updateStatus: (id: string, data: StorageProviderStatusInput) =>
    http.put<void>(`/storage/providers/${id}/status`, data),
  /**
   * 启用中的服务商列表（用于文件页上传对话框与筛选下拉；规模假设 <100）。
   * 调 GET /storage/providers?status=1&size=100，取 records。
   */
  listEnabled: async (): Promise<StorageProviderVO[]> => {
    const page = await http.get<PageResult<StorageProviderVO>>('/storage/providers', {
      status: 1,
      size: 100,
      current: 1,
    })
    return page.records
  },
}
