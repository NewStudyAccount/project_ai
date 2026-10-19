import { http, request } from '@/api/request'
import type { FileMeta, FileUrlResult, PageResult } from '@/types'

export function pageFiles(params: {
  current?: number
  size?: number
  keyword?: string
  contentType?: string
}) {
  return http.get<PageResult<FileMeta>>('/files', params)
}

export function getFile(id: string) {
  return http.get<FileMeta>(`/files/${id}`)
}

export function uploadFile(file: File, bizType?: string, bizId?: string) {
  const form = new FormData()
  form.append('file', file)
  if (bizType) form.append('bizType', bizType)
  if (bizId) form.append('bizId', bizId)
  return request<FileMeta>({ method: 'POST', url: '/files', data: form })
}

export function getFileUrl(id: string) {
  return http.get<FileUrlResult>(`/files/${id}/url`)
}

export function deleteFile(id: string) {
  return http.delete<void>(`/files/${id}`)
}
