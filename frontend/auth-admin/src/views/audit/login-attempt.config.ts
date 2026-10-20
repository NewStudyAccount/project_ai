/**
 * 登录尝试页 PageSchema：只读列表 + 详情 D-Panel
 * 结果展示沿用 action === 'LOGIN_SUCCESS' 的既有语义（后端出参映射，前端不自造枚举）
 */
import { auditApi } from '@/api/audit'
import type { AuditVO } from '@/types'
import type { CrudPageSchema } from '@/types/crud'

export function buildLoginAttemptSchema(): CrudPageSchema<AuditVO, Record<string, unknown>> {
  return {
    api: {
      page: (q) => auditApi.loginAttempts(q as unknown as Parameters<typeof auditApi.loginAttempts>[0]),
    },
    filters: [
      { prop: 'username', type: 'input', placeholder: '用户名', width: 150 },
      { prop: 'userId', type: 'input', placeholder: '用户 ID', width: 150 },
      { prop: 'clientId', type: 'input', placeholder: 'Client ID', width: 140 },
      { prop: 'ip', type: 'input', placeholder: 'IP', width: 140 },
      {
        prop: 'success',
        type: 'select',
        placeholder: '结果',
        width: 120,
        options: [
          { value: 1, label: '成功' },
          { value: 0, label: '失败' },
        ],
      },
      { prop: 'beginTime', propEnd: 'endTime', type: 'dateRange', width: 340 },
    ],
    columns: [
      { prop: 'id', label: 'ID', width: 180, type: 'copy' },
      // 结果列由页面 slot 渲染（软胶囊，映射既有 LOGIN_SUCCESS 语义）
      { prop: 'action', label: '结果', width: 120 },
      { prop: 'targetId', label: '用户名', width: 140 },
      { prop: 'actorUserId', label: '用户 ID', width: 150, tooltip: true },
      { prop: 'detail', label: '失败原因', minWidth: 140, tooltip: true },
      { prop: 'ip', label: 'IP', width: 140 },
      { prop: 'createTime', label: '时间', width: 180, type: 'datetime' },
    ],
    rowActions: [{ key: 'detail', label: '详情' }],
    emptyText: '暂无登录尝试记录',
  }
}

export function loginAttemptDetailRows(row: AuditVO): { key: string; label: string; value?: string | number | null }[] {
  return [
    { key: 'id', label: '记录 ID', value: row.id },
    { key: 'createTime', label: '发生时间', value: row.createTime },
    { key: 'result', label: '结果', value: row.action === 'LOGIN_SUCCESS' ? '成功' : '失败' },
    { key: 'username', label: '用户名', value: row.targetId },
    { key: 'userId', label: '用户 ID', value: row.actorUserId },
    { key: 'ip', label: '来源 IP', value: row.ip },
    { key: 'detail', label: '失败原因', value: row.detail },
  ]
}
