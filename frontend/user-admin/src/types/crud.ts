/**
 * 配置驱动 CrudPage 契约类型（generic-admin-page-design.md §3）
 * 主键/出参 id 一律 string（CLAUDE.md §6.4.7）；枚举值以后端契约为准，前端不自造。
 */
import type { PageQuery, PageResult } from '@/types'

export type PillTone = 'success' | 'warning' | 'danger'

export interface OptionItem {
  value: string | number
  label: string
  /** pill 列配色，仅 success / warning / danger 三档（云白 §5） */
  pillTone?: PillTone
}

export type ColumnType = 'text' | 'enum' | 'pill' | 'datetime' | 'copy'

export interface ColumnDef<Row = Record<string, unknown>> {
  prop: string
  label: string
  width?: number
  minWidth?: number
  type?: ColumnType
  options?: OptionItem[]
  /** 长文本省略 + hover 全文 */
  tooltip?: boolean
  fixed?: boolean | 'left' | 'right'
  formatter?: (row: Row) => string
}

export interface FilterDef {
  prop: string
  label?: string
  type: 'input' | 'select' | 'dateRange'
  options?: OptionItem[]
  placeholder?: string
  width?: number
  /** dateRange 的结束参数名（如 endTime）；缺省 `${prop}End` */
  propEnd?: string
}

export interface ActionDef<Row = Record<string, unknown>> {
  key: string
  label: string
  /** 缺省按 `${permissionPrefix}:${key}` 拼接（system:resource:action） */
  permission?: string
  type?: 'primary' | 'default' | 'danger'
  /** 非空 → danger 二次确认；字符串或按行生成文案 */
  confirm?: string | ((row: Row) => string)
  /** 行级显隐（如「启用」仅停用行显示） */
  visible?: (row: Row) => boolean
  /** 提供则骨架内执行（确认后调用、可选 toast、刷新）；否则仅抛 custom-action 事件由页面处理 */
  handler?: (row: Row) => Promise<unknown>
  /** handler 成功后的 toast 文案；缺省不提示 */
  successMessage?: string
}

export interface FormFieldDef {
  prop: string
  label: string
  type: 'input' | 'textarea' | 'number' | 'select' | 'switch' | 'date'
  options?: OptionItem[]
  placeholder?: string
  hint?: string
  required?: boolean
  message?: string
  min?: number
  max?: number
  disabled?: boolean
  /** 新建/编辑差异显隐 */
  hiddenWhen?: (mode: 'create' | 'edit') => boolean
  /** 新建/编辑差异锁定（如编辑锁业务标识） */
  disabledWhen?: (mode: 'create' | 'edit') => boolean
  span?: 1 | 2
}

export interface CrudPageSchema<Row = Record<string, unknown>, F = Record<string, unknown>> {
  /** 权限前缀，如 'auth:client' → 动作权限自动拼 :create/:update/:delete */
  permissionPrefix?: string
  api: {
    page: (q: Record<string, unknown>) => Promise<PageResult<Row>>
    create?: (data: F) => Promise<unknown>
    update?: (id: string, data: F) => Promise<unknown>
    remove?: (id: string) => Promise<void>
  }
  columns: ColumnDef<Row>[]
  filters?: FilterDef[]
  /** 主操作；缺省仅「新建」（有 create 接口时） */
  primaryActions?: ActionDef<Row>[]
  /** 行内操作；缺省内建 编辑 / 删除（有对应接口时） */
  rowActions?: ActionDef<Row>[]
  form?: FormFieldDef[]
  formDefaults?: Partial<F>
  formWidth?: number
  /** 弹窗标题覆盖 */
  createTitle?: string
  editTitle?: string
  /** 空态文案 */
  emptyText?: string
}

export type { PageQuery, PageResult }
