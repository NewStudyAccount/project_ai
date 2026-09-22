import { getPublic } from '@/api/http'
import type { PageResult } from '@/types'

export interface PostSummary {
  id: string
  title: string
  slug: string
  summary: string
  coverUrl?: string
  publishTime?: string
}

/** 文章列表（content-modules 落地后可通）。 */
export function listPublicPosts(current = 1, size = 10): Promise<PageResult<PostSummary>> {
  return getPublic<PageResult<PostSummary>>('/api/v1/public/posts', { current, size })
}
