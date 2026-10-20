/**
 * 授权（Grant）管理页 PageSchema：只读列表 + 吊销/踢下线自定义动作（带原因弹窗）
 */
import { grantApi } from '@/api/grant'
import type { GrantVO } from '@/types'
import type { CrudPageSchema } from '@/types/crud'

export function buildGrantSchema(): CrudPageSchema<GrantVO, Record<string, unknown>> {
  return {
    permissionPrefix: 'auth:grant',
    api: {
      page: (q) => grantApi.page(q as unknown as Parameters<typeof grantApi.page>[0]),
    },
    filters: [
      { prop: 'userId', type: 'input', placeholder: '用户 ID', width: 160 },
      { prop: 'clientId', type: 'input', placeholder: 'Client ID', width: 160 },
      {
        prop: 'status',
        type: 'select',
        placeholder: '状态',
        width: 120,
        options: [
          { value: 1, label: '有效' },
          { value: 0, label: '已吊销' },
        ],
      },
    ],
    columns: [
      { prop: 'id', label: 'ID', width: 180, type: 'copy' },
      { prop: 'userId', label: '用户 ID', width: 160, type: 'copy' },
      { prop: 'clientId', label: 'Client ID', width: 140 },
      { prop: 'scopes', label: 'Scopes', minWidth: 140, tooltip: true },
      {
        prop: 'status',
        label: '状态',
        width: 100,
        type: 'pill',
        options: [
          { value: 1, label: '有效', pillTone: 'success' },
          { value: 0, label: '已吊销', pillTone: 'danger' },
        ],
      },
      { prop: 'revokedAt', label: '吊销时间', width: 170, type: 'datetime' },
      { prop: 'revokeReason', label: '原因', minWidth: 140, tooltip: true },
    ],
    rowActions: [
      // 吊销需填写原因，走页面级弹窗（custom-action 接线）
      {
        key: 'revoke',
        label: '吊销',
        type: 'danger',
        permission: 'auth:grant:revoke',
        visible: (row) => row.status === 1,
      },
      {
        key: 'revoke-user',
        label: '踢下线',
        type: 'danger',
        permission: 'auth:grant:revoke',
      },
    ],
    emptyText: '暂无授权记录',
  }
}
