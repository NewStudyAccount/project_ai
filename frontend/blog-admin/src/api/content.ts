import { http } from '@/api/request'
import type { PageResult } from '@/types'

export interface CategoryVO {
  id: string
  parentId: string
  name: string
  slug: string
  description?: string
  sort?: number
  status?: number
  articleCount?: number
  children?: CategoryVO[]
}

export interface TagVO {
  id: string
  name: string
  slug: string
  sort?: number
  status?: number
  articleCount?: number
}

export interface ArticleVO {
  id: string
  title: string
  slug?: string
  summary?: string
  coverFileId?: string
  categoryId?: string
  categoryName?: string
  tags?: TagVO[]
  authorName?: string
  status?: number
  publishTime?: string
  isTop?: number
  hasDraft?: boolean
  createTime?: string
}

export interface ArticleDetailVO extends ArticleVO {
  content?: string
  contentFrom?: string
  contentFormat?: string
  wordCount?: number
  seoTitle?: string
  seoDescription?: string
  draftFileId?: string
  contentFileId?: string
}

export interface ArticleContentResult {
  draftFileId?: string
  contentFileId?: string
  hasDraft?: boolean
  status?: number
  publishTime?: string
}

export interface SiteConfigVO {
  title?: string
  description?: string
  copyright?: string
  about?: string
}

export function listCategories(params?: { status?: number; name?: string }) {
  return http.get<CategoryVO[]>('/admin/categories', params)
}

export function createCategory(body: Record<string, unknown>) {
  return http.post<CategoryVO>('/admin/categories', body)
}

export function updateCategory(id: string, body: Record<string, unknown>) {
  return http.put<CategoryVO>(`/admin/categories/${id}`, body)
}

export function deleteCategory(id: string) {
  return http.delete<void>(`/admin/categories/${id}`)
}

export function pageTags(params: { current?: number; size?: number; name?: string; status?: number }) {
  return http.get<PageResult<TagVO>>('/admin/tags', params)
}

export function createTag(body: Record<string, unknown>) {
  return http.post<TagVO>('/admin/tags', body)
}

export function updateTag(id: string, body: Record<string, unknown>) {
  return http.put<TagVO>(`/admin/tags/${id}`, body)
}

export function deleteTag(id: string) {
  return http.delete<void>(`/admin/tags/${id}`)
}

export function pageArticles(params: Record<string, unknown>) {
  return http.get<PageResult<ArticleVO>>('/admin/articles', params)
}

export function getArticle(id: string) {
  return http.get<ArticleDetailVO>(`/admin/articles/${id}`)
}

export function createArticle(body: Record<string, unknown>) {
  return http.post<ArticleDetailVO>('/admin/articles', body)
}

export function updateArticle(id: string, body: Record<string, unknown>) {
  return http.put<ArticleVO>(`/admin/articles/${id}`, body)
}

export function saveArticleContent(
  id: string,
  body: { mode: string; content: string; contentFormat?: string; seoTitle?: string; seoDescription?: string },
) {
  return http.put<ArticleContentResult>(`/admin/articles/${id}/content`, body)
}

export function discardDraft(id: string) {
  return http.delete<ArticleContentResult>(`/admin/articles/${id}/draft`)
}

export function publishArticle(id: string) {
  return http.post<ArticleContentResult>(`/admin/articles/${id}/publish`)
}

export function offlineArticle(id: string) {
  return http.post<ArticleContentResult>(`/admin/articles/${id}/offline`)
}

export function deleteArticle(id: string) {
  return http.delete<void>(`/admin/articles/${id}`)
}

export function getSiteConfig() {
  return http.get<SiteConfigVO>('/admin/site-config')
}

export function saveSiteConfig(body: Record<string, string>) {
  return http.put<SiteConfigVO>('/admin/site-config', body)
}
