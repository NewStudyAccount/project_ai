/**
 * 文章列表 PageSchema（形态层收敛；编辑器跳转与内容域逻辑归在途变更，本文件不碰）
 * 状态枚举映射后端 ArticleStatusEnum desc（0 草稿 / 1 已发布 / 2 已下架），前端不自造枚举值
 */
import { deleteArticle, offlineArticle, pageArticles, publishArticle, type ArticleVO } from '@/api/content'
import { compactQuery } from '@/utils/query'
import type { CrudPageSchema, OptionItem } from '@/types/crud'

/** 后端 ArticleStatusEnum 的前端映射（pillTone 仅 success / warning / danger 三档） */
export const ARTICLE_STATUS_OPTIONS: OptionItem[] = [
  { value: 0, label: '草稿', pillTone: 'warning' },
  { value: 1, label: '已发布', pillTone: 'success' },
  { value: 2, label: '已下架', pillTone: 'danger' },
]

export function buildArticleSchema(categories: OptionItem[]): CrudPageSchema<ArticleVO, Record<string, unknown>> {
  return {
    api: {
      page: (q) => pageArticles(compactQuery(q)),
      remove: (id) => deleteArticle(id),
    },
    primaryActions: [{ key: 'create', label: '新建文章', type: 'primary', permission: 'blog:article:create' }],
    filters: [
      { prop: 'keyword', type: 'input', placeholder: '标题关键字', width: 200 },
      { prop: 'categoryId', type: 'select', placeholder: '分类', width: 140, options: categories },
      {
        prop: 'status',
        type: 'select',
        placeholder: '状态',
        width: 120,
        options: ARTICLE_STATUS_OPTIONS,
      },
    ],
    columns: [
      { prop: 'title', label: '标题', minWidth: 200, tooltip: true },
      { prop: 'categoryName', label: '分类', width: 120 },
      { prop: 'status', label: '状态', width: 110 },
      { prop: 'publishTime', label: '发布时间', width: 170, type: 'datetime' },
      { prop: 'authorName', label: '作者', width: 100 },
    ],
    rowActions: [
      { key: 'main', label: '主表', permission: 'blog:article:update' },
      { key: 'content', label: '正文', permission: 'blog:article:update' },
      {
        key: 'publish',
        label: '发布',
        permission: 'blog:article:publish',
        visible: (row) => row.status !== 1,
        confirm: (row) => `确认发布「${row.title}」？`,
        handler: (row) => publishArticle(row.id),
        successMessage: '已发布',
      },
      {
        key: 'offline',
        label: '下架',
        permission: 'blog:article:publish',
        visible: (row) => row.status === 1,
        confirm: (row) => `确认下架「${row.title}」？`,
        handler: (row) => offlineArticle(row.id),
        successMessage: '已下架',
      },
      {
        key: 'delete',
        label: '删除',
        type: 'danger',
        permission: 'blog:article:delete',
        confirm: (row) => `确认删除「${row.title}」？`,
        successMessage: '已删除',
      },
    ],
    emptyText: '暂无文章',
  }
}
