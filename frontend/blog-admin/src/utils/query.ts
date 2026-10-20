/**
 * 筛选参数空值清理（generic-admin-page-design.md §3.2：丢弃 undefined / null / ''）。
 * 后端查询均按空值不筛选处理（isBlank / null 判定），丢弃空值与显式传空串等价。
 */
export function compactQuery(query: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue
    out[key] = value
  }
  return out
}
