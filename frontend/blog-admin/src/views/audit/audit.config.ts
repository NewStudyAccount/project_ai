/**
 * 审计页 PageSchema：只读列表 + 详情 D-Panel（custom-action 接线）
 * 字段与 AuditVO / AuditQuery 一致；TraceId 等出参没有的字段不自造
 */
import { auditApi } from '@/api/audit'
import { compactQuery } from '@/utils/query'
import type { AuditVO } from '@/types'
import type { CrudPageSchema } from '@/types/crud'

export function buildAuditSchema(): CrudPageSchema<AuditVO, Record<string, unknown>> {
  return {
    api: {
      page: (q) => auditApi.page(compactQuery(q) as unknown as Parameters<typeof auditApi.page>[0]),
    },
    filters: [
      { prop: 'action', type: 'input', placeholder: '动作', width: 160 },
      { prop: 'actorUserId', type: 'input', placeholder: '操作人 ID', width: 150 },
      { prop: 'beginTime', propEnd: 'endTime', type: 'dateRange', width: 340 },
    ],
    columns: [
      { prop: 'id', label: 'ID', width: 180, type: 'copy' },
      { prop: 'action', label: '动作', width: 160 },
      { prop: 'actorUserId', label: '操作人', width: 150 },
      { prop: 'targetType', label: '目标类型', width: 120 },
      { prop: 'targetId', label: '目标 ID', width: 160, tooltip: true },
      { prop: 'detail', label: '摘要', minWidth: 180, tooltip: true },
      { prop: 'ip', label: 'IP', width: 140 },
      { prop: 'createTime', label: '时间', width: 180, type: 'datetime' },
    ],
    rowActions: [{ key: 'detail', label: '详情', permission: 'blog:audit:list' }],
    emptyText: '暂无审计记录',
  }
}

/** 详情 D-Panel 行（键值对）；TraceId 等出参未提供的字段不渲染 */
export function auditDetailRows(row: AuditVO): { key: string; label: string; value?: string | number | null }[] {
  const rows: { key: string; label: string; value?: string | number | null }[] = [
    { key: 'id', label: '记录 ID', value: row.id },
    { key: 'createTime', label: '发生时间', value: row.createTime },
    { key: 'action', label: '动作', value: row.action },
    { key: 'actorUserId', label: '操作人', value: row.actorUserId },
    { key: 'targetType', label: '目标类型', value: row.targetType },
    { key: 'targetId', label: '目标 ID', value: row.targetId },
    { key: 'ip', label: '来源 IP', value: row.ip },
    { key: 'detail', label: '摘要', value: row.detail },
  ]
  // TraceId 仅当出参携带时展示（AuditVO 无该字段，禁止前端自造）
  const traceId = (row as unknown as { traceId?: string }).traceId
  if (traceId) rows.push({ key: 'traceId', label: 'TraceId', value: traceId })
  return rows
}
