/**
 * 文件列表 PageSchema（仅形态层：筛选条/表格/分页/删除确认接 CrudPage）
 * 业务层（上传、引用检查、storageCode、URL 解析）保持现状，归 blog-object-storage-* 能力
 */
import { deleteFile, pageFiles } from '@/api/file'
import { compactQuery } from '@/utils/query'
import type { FileMeta, PageResult, StorageProviderVO } from '@/types'
import type { CrudPageSchema } from '@/types/crud'

export function formatBytes(n: number): string {
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(2) + ' MB'
}

export function buildFileSchema(
  providers: StorageProviderVO[],
  onPage?: (page: PageResult<FileMeta>) => void,
): CrudPageSchema<FileMeta, Record<string, unknown>> {
  return {
    api: {
      page: async (q) => {
        const page = await pageFiles(compactQuery(q) as Parameters<typeof pageFiles>[0])
        onPage?.(page)
        return page
      },
      remove: (id) => deleteFile(id),
    },
    primaryActions: [
      { key: 'upload', label: '上传', type: 'primary', permission: 'blog:file:upload' },
      { key: 'providers', label: '存储服务商配置' },
    ],
    filters: [
      { prop: 'keyword', type: 'input', placeholder: '原始文件名关键字', width: 200 },
      {
        prop: 'storageCode',
        type: 'select',
        placeholder: '服务商：全部',
        width: 180,
        options: providers.map((p) => ({ value: p.code, label: p.code })),
      },
      {
        prop: 'contentType',
        type: 'select',
        placeholder: '类型：全部',
        width: 160,
        options: [
          { value: 'image/png', label: 'PNG' },
          { value: 'image/jpeg', label: 'JPEG' },
          { value: 'image/gif', label: 'GIF' },
          { value: 'application/pdf', label: 'PDF' },
        ],
      },
    ],
    columns: [
      { prop: 'id', label: '缩略图', width: 80 },
      { prop: 'originalName', label: '文件名', minWidth: 220 },
      { prop: 'storageCode', label: '服务商 / 桶', width: 180 },
      { prop: 'contentType', label: '类型', width: 120 },
      { prop: 'sizeBytes', label: '大小', width: 100, formatter: (row) => formatBytes(row.sizeBytes) },
      { prop: 'createTime', label: '上传时间', width: 180, type: 'datetime' },
    ],
    rowActions: [
      { key: 'preview', label: '预览', permission: 'blog:file:read' },
      { key: 'download', label: '下载', permission: 'blog:file:read' },
      {
        key: 'delete',
        label: '删除',
        type: 'danger',
        permission: 'blog:file:delete',
        confirm: (row) =>
          `确认删除「${row.originalName}」？\n此为逻辑删除（deleted=1），公开 URL 在物理清理前可能仍可访问；删除前会做引用检查，被正文/业务引用时将拒绝删除并返回引用摘要。`,
        successMessage: '已逻辑删除',
      },
    ],
    emptyText: '暂无文件',
  }
}
