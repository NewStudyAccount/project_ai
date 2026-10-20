/**
 * 审计列表页 PageSchema + 详情 D-Panel 键值对
 * 字段与真实契约一致（api AuditQuery、types AuditVO），出参没有的字段（如 TraceId）不自造、不渲染
 */
import { auditApi } from '@/api/audit'
import type { AuditVO } from '@/types'
import type { CrudPageSchema } from '@/types/crud'

export function buildAuditSchema(): CrudPageSchema<AuditVO, Record<string, unknown>> {
  return {
    api: {
      page: (q) => auditApi.page(q as unknown as Parameters<typeof auditApi.page>[0]),
    },
    filters: [
      { prop: 'action', type: 'input', placeholder: '动作', width: 160 },
      { prop: 'actorUserId', type: 'input', placeholder: '操作人 ID', width: 150 },
      { prop: 'targetUserId', type: 'input', placeholder: '目标用户 ID', width: 150 },
      { prop: 'beginTime', propEnd: 'endTime', type: 'dateRange', width: 340 },
    ],
    columns: [
      { prop: 'id', label: 'ID', width: 180, type: 'copy' },
      { prop: 'action', label: '动作', width: 160 },
      { prop: 'actorUserId', label: '操作人', width: 160 },
      { prop: 'targetUserId', label: '目标用户', width: 160 },
      { prop: 'detail', label: '摘要', minWidth: 220, tooltip: true },
      { prop: 'ip', label: 'IP', width: 150 },
      { prop: 'createTime', label: '时间', width: 180, type: 'datetime' },
    ],
    rowActions: [{ key: 'detail', label: '详情' }],
    emptyText: '暂无审计记录',
  }
}

/** 详情 D-Panel 行（键值对，覆盖 AuditVO 全量出参字段） */
export function auditDetailRows(row: AuditVO): { key: string; label: string; value?: string | number | null }[] {
  const rows: { key: string; label: string; value?: string | number | null }[] = [
    { key: 'id', label: '记录 ID', value: row.id },
    { key: 'createTime', label: '发生时间', value: row.createTime },
    { key: 'action', label: '动作', value: row.action },
    { key: 'actorUserId', label: '操作人', value: row.actorUserId },
    { key: 'targetUserId', label: '目标用户', value: row.targetUserId },
    { key: 'ip', label: '来源 IP', value: row.ip },
    { key: 'detail', label: '摘要', value: row.detail },
  ]
  // TraceId 仅当出参携带时渲染（AuditVO 无该字段，禁止前端自造，D5）
  const traceId = (row as unknown as { traceId?: string }).traceId
  if (traceId) rows.push({ key: 'traceId', label: 'TraceId', value: traceId })
  return rows
}
