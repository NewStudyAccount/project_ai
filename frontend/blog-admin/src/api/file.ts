import { http, request } from '@/api/request'
import type { FileMeta, FileUrlResult, PageResult } from '@/types'

export function pageFiles(params: {
  current?: number
  size?: number
  keyword?: string
  contentType?: string
  /** 按归属服务商精确筛选（多服务商排查） */
  storageCode?: string
}) {
  return http.get<PageResult<FileMeta>>('/files', params)
}

export function getFile(id: string) {
  return http.get<FileMeta>(`/files/${id}`)
}

export function uploadFile(
  file: File,
  bizType?: string,
  bizId?: string,
  /** 目标服务商 code（不传走系统默认服务商；仅启用中的服务商可选） */
  storageCode?: string,
) {
  const form = new FormData()
  form.append('file', file)
  if (bizType) form.append('bizType', bizType)
  if (bizId) form.append('bizId', bizId)
  if (storageCode) form.append('storageCode', storageCode)
  return request<FileMeta>({ method: 'POST', url: '/files', data: form })
}

export function getFileUrl(id: string) {
  return http.get<FileUrlResult>(`/files/${id}/url`)
}

export function deleteFile(id: string) {
  return http.delete<void>(`/files/${id}`)
}
