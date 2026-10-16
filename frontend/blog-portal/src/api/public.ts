import { get } from '@/api/request'

export interface CategoryVO {
  id: string
  name: string
  slug: string
  children?: CategoryVO[]
}

export interface TagVO {
  id: string
  name: string
  slug: string
  articleCount?: number
}

export interface ArticleVO {
  id: string
  title: string
  slug?: string
  summary?: string
  coverFileId?: string
  categoryName?: string
  tags?: TagVO[]
  authorName?: string
  publishTime?: string
  isTop?: number
}

export interface ArticleDetailVO extends ArticleVO {
  content?: string
  contentFormat?: string
  seoTitle?: string
  seoDescription?: string
}

export interface SiteConfigVO {
  title?: string
  description?: string
  copyright?: string
  about?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

export function fetchCategories() {
  return get<CategoryVO[]>('/public/categories')
}

export function fetchTags() {
  return get<TagVO[]>('/public/tags')
}

export function fetchArticles(params: Record<string, unknown>) {
  return get<PageResult<ArticleVO>>('/public/articles', params)
}

export function fetchArticle(idOrSlug: string) {
  return get<ArticleDetailVO>(`/public/articles/${idOrSlug}`)
}

export function fetchSiteConfig() {
  return get<SiteConfigVO>('/public/site-config')
}
