import { http } from './request'
import type { PageResult } from '@/types'

/** 知识库域类型（api-design.md §1/§2/§3；id 一律 string） */

export type IngestState = 'UNINGESTED' | 'INGESTED' | 'STALE' | 'INVALID'

export interface Candidate {
  articleId: string
  title: string
  categoryId: string
  categoryName: string
  publishTime: string | null
  updateTime: string | null
  contentVersion: string
  ingestState: IngestState
  docId: string | null
  docStatus: number | null
  chunkCount: number
  lastIngestTime: string | null
}

export interface CandidateSummary {
  total: number
  uningested: number
  ingested: number
  stale: number
  invalid: number
}

export interface ArticleIndexSyncResult {
  pulled: number
  inserted: number
  updated: number
  removed: number
  invalidated: number
}

export interface FromArticlesResult {
  submitted: number
  skipped: number
  rejected: number
  accepted: { articleId: string; docId: string; taskId: string; reused: boolean }[]
  skippedItems: { articleId: string; reason: string }[]
  rejectedItems: { articleId: string; reason: string; code: number }[]
}

export interface KnowledgeDoc {
  id: string
  source: number
  sourceId: string | null
  title: string
  tags: string
  status: number
  chunkCount: number
  readyChunkCount: number
  failReason: string
  embedModel: string
  embedDim: number
  contentVersion: string
  docUrl: string
  updateTime: string | null
}

export interface ImportDocsResult {
  submitted: number
  accepted: { originalName: string; docId: string; taskId: string }[]
  rejected: { originalName: string; reason: string; code: number }[]
}

export interface KnowledgeTask {
  id: string
  taskType: string
  docId: string
  docTitle: string
  status: number
  retryCount: number
  maxRetry: number
  failReason: string
  startTime: string | null
  endTime: string | null
  createTime: string | null
}

export interface KnowledgeStats {
  docTotal: number
  docByStatus: Record<string, number>
  chunkTotal: number
  chunkReadyRatio: number
  esDocCount: number | null
  esHealthy: boolean
  taskFailed: number
  providerCalls: Record<string, number>
}

export interface AiProvider {
  id: string
  code: string
  kind: number
  type: string
  name: string
  baseUrl: string
  model: string
  dim: number
  apiKeyMasked: string
  isDefault: number
  status: number
  updateTime: string | null
}

export interface AiProviderInput {
  code: string
  kind: number
  type: string
  name: string
  baseUrl: string
  model: string
  dim: number
  apiKey?: string
  isDefault: number
  status: number
}

export interface AiProviderTestResult {
  ok: boolean
  latencyMs: number
  message: string
}

const BASE = '/admin/knowledge'

export const knowledgeApi = {
  // ---- 文章入库 ----
  candidates: (params: Record<string, unknown>) =>
    http.get<PageResult<Candidate>>(`${BASE}/candidates`, params),
  candidatesSummary: () => http.get<CandidateSummary>(`${BASE}/candidates/summary`),
  syncArticleIndex: () => http.post<ArticleIndexSyncResult>(`${BASE}/article-index/sync`),
  fromArticles: (articleIds: string[]) =>
    http.post<FromArticlesResult>(`${BASE}/docs/from-articles`, { articleIds }),

  // ---- 知识文档 ----
  docsPage: (params: Record<string, unknown>) =>
    http.get<PageResult<KnowledgeDoc>>(`${BASE}/docs`, params),
  importDocs: (files: File[], tags: string) => {
    const form = new FormData()
    files.forEach((f) => form.append('files', f))
    if (tags) form.append('tags', tags)
    return http.post<ImportDocsResult>(`${BASE}/docs`, form)
  },
  updateDoc: (id: string, file: File | null, tags: string) => {
    const form = new FormData()
    if (file) form.append('file', file)
    if (tags !== '') form.append('tags', tags)
    return http.put<Record<string, unknown>>(`${BASE}/docs/${id}`, form)
  },
  deleteDoc: (id: string) => http.delete<Record<string, unknown>>(`${BASE}/docs/${id}`),
  revectorize: (id: string) =>
    http.post<Record<string, unknown>>(`${BASE}/docs/${id}/revectorize`),
  reindex: () => http.post<Record<string, unknown>>(`${BASE}/docs/reindex`),

  // ---- 任务与统计 ----
  tasksPage: (params: Record<string, unknown>) =>
    http.get<PageResult<KnowledgeTask>>(`${BASE}/tasks`, params),
  rerunTask: (id: string) => http.post<KnowledgeTask>(`${BASE}/tasks/${id}/rerun`),
  stats: () => http.get<KnowledgeStats>(`${BASE}/stats`),

  // ---- AI 服务商 ----
  providersPage: (params: Record<string, unknown>) =>
    http.get<PageResult<AiProvider>>(`${BASE}/ai-providers`, params),
  createProvider: (input: AiProviderInput) =>
    http.post<AiProvider>(`${BASE}/ai-providers`, input),
  updateProvider: (id: string, input: AiProviderInput) =>
    http.put<AiProvider>(`${BASE}/ai-providers/${id}`, input),
  deleteProvider: (id: string) => http.delete<void>(`${BASE}/ai-providers/${id}`),
  testProvider: (id: string) =>
    http.post<AiProviderTestResult>(`${BASE}/ai-providers/${id}/test`),
}
