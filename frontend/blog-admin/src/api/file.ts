import { http, request } from '@/api/request'
import type { FileMeta, PageResult, PresignResult } from '@/types'

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

export function uploadFile(file: File) {
  const form = new FormData()
  form.append('file', file)
  return request<FileMeta>({ method: 'POST', url: '/files', data: form })
}

export function presignFile(id: string) {
  return http.get<PresignResult>(`/files/${id}/url`)
}

export function deleteFile(id: string) {
  return http.delete<void>(`/files/${id}`)
}
