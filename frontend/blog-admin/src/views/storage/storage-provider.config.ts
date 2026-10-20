/**
 * 存储服务商列表 PageSchema（仅形态层：筛选/表格/分页/确认接 CrudPage）
 * 业务动作（测试连通 / 设默认 / 启停 / SK 掩码）保持现状语义，归 blog-object-storage-* 能力；
 * 新建/编辑为业务定制 D-Form（密码输入与联动校验超出 CrudForm 契约），经 custom-action 由页面接线。
 */
import { storageProviderApi } from '@/api/storageProvider'
import { compactQuery } from '@/utils/query'
import { PROVIDER_TYPES, type StorageProviderVO } from '@/types'
import type { CrudPageSchema } from '@/types/crud'

export function buildStorageProviderSchema(): CrudPageSchema<StorageProviderVO, Record<string, unknown>> {
  return {
    api: {
      page: (q) => storageProviderApi.page(compactQuery(q) as Parameters<typeof storageProviderApi.page>[0]),
      remove: (id) => storageProviderApi.delete(id),
    },
    primaryActions: [
      { key: 'create', label: '新建服务商', type: 'primary', permission: 'blog:storage-provider:create' },
    ],
    filters: [
      { prop: 'keyword', type: 'input', placeholder: '编码 / 名称关键字', width: 200 },
      {
        prop: 'providerType',
        type: 'select',
        placeholder: '类型：全部',
        width: 160,
        options: [...PROVIDER_TYPES],
      },
      {
        prop: 'status',
        type: 'select',
        placeholder: '状态：全部',
        width: 140,
        options: [
          { value: 1, label: '启用' },
          { value: 0, label: '停用' },
        ],
      },
    ],
    columns: [
      { prop: 'code', label: '编码 / 名称', minWidth: 180 },
      { prop: 'providerType', label: '类型', width: 120 },
      { prop: 'endpoint', label: 'endpoint / 桶', minWidth: 240 },
      { prop: 'publicBaseUrl', label: '公开基址', minWidth: 200, tooltip: true },
      { prop: 'secretKeyMasked', label: '密钥', width: 120 },
      { prop: 'status', label: '状态', width: 140 },
    ],
    rowActions: [
      { key: 'test', label: '测试', permission: 'blog:storage-provider:test' },
      { key: 'update', label: '编辑', permission: 'blog:storage-provider:update' },
      {
        key: 'enable',
        label: '启用',
        permission: 'blog:storage-provider:update',
        visible: (row) => row.status === 0,
        handler: (row) => storageProviderApi.updateStatus(row.id, { status: 1 }),
        successMessage: '已启用',
      },
      {
        key: 'disable',
        label: '停用',
        permission: 'blog:storage-provider:update',
        visible: (row) => row.status === 1,
        confirm: '停用后禁止新上传，存量文件仍可读 URL/逻辑删除。是否继续？',
        handler: (row) => storageProviderApi.updateStatus(row.id, { status: 0 }),
        successMessage: '已停用',
      },
      {
        key: 'setDefault',
        label: '设默认',
        permission: 'blog:storage-provider:update',
        visible: (row) => row.isDefault !== 1,
        confirm: '设为默认仅影响新上传，历史文件仍按其 storage_code 回源原服务商。是否继续？',
        handler: (row) => storageProviderApi.setDefault(row.id),
        successMessage: '已设为默认',
      },
      {
        key: 'delete',
        label: '删除',
        type: 'danger',
        permission: 'blog:storage-provider:delete',
        confirm: (row) => `确认删除服务商「${row.code}」？无活跃文件引用时才允许删除。`,
        successMessage: '已删除',
      },
    ],
    emptyText: '暂无服务商',
  }
}
