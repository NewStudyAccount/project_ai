/**
 * 账号管理页 PageSchema（generic-admin-page-design.md §3）
 * 字段/枚举与真实契约一致（src/types UserVO、api UserInput / UserPageQuery），前端不自造：
 * - 筛选仅 username / status（后端 UserPageQuery 只支持这两个条件，不自造参数）
 * - 表单仅 UserInput 字段；id 由后端发号（string），不暴露输入
 * - 状态变更走 userApi.updateStatus（Create/Update DTO 无 status 字段，状态不在表单内）
 */
import { userApi, type UserInput } from '@/api/user'
import { compactQuery } from '@/utils/query'
import type { UserVO } from '@/types'
import type { CrudPageSchema, FormFieldDef, OptionItem } from '@/types/crud'

/** 状态取值 0/1/2（后端 UpdateStatusRequest 约束），文案沿用现有页面 */
const statusOptions: OptionItem[] = [
  { value: 1, label: '正常', pillTone: 'success' },
  { value: 0, label: '停用', pillTone: 'warning' },
  { value: 2, label: '锁定', pillTone: 'danger' },
]

const emptyForm = (): UserInput => ({
  username: '',
  realName: '',
  nickname: '',
  phone: '',
  email: '',
  remark: '',
})

const formFields: FormFieldDef[] = [
  {
    prop: 'username',
    label: '用户名',
    type: 'input',
    required: true,
    // 编辑锁定用户名（业务标识不可改）
    disabledWhen: (mode) => mode === 'edit',
  },
  { prop: 'realName', label: '姓名', type: 'input', required: true },
  { prop: 'nickname', label: '昵称', type: 'input' },
  { prop: 'phone', label: '手机号', type: 'input' },
  { prop: 'email', label: '邮箱', type: 'input' },
  { prop: 'remark', label: '备注', type: 'textarea' },
]

export function buildUserSchema(): CrudPageSchema<UserVO, UserInput> {
  return {
    permissionPrefix: 'user:user',
    api: {
      page: (q) => userApi.page(compactQuery(q) as unknown as Parameters<typeof userApi.page>[0]),
      create: (data) => userApi.create(data),
      update: (id, data) => userApi.update(id, data),
    },
    filters: [
      { prop: 'username', type: 'input', placeholder: '用户名', width: 180 },
      { prop: 'status', type: 'select', placeholder: '状态', width: 120, options: statusOptions },
    ],
    columns: [
      { prop: 'id', label: 'ID', width: 180, type: 'copy' },
      { prop: 'username', label: '用户名', width: 140 },
      { prop: 'realName', label: '姓名', width: 120 },
      { prop: 'phone', label: '手机号', width: 130 },
      { prop: 'email', label: '邮箱', minWidth: 180, tooltip: true },
      { prop: 'status', label: '状态', width: 90, type: 'pill', options: statusOptions },
      { prop: 'createTime', label: '创建时间', width: 180, type: 'datetime' },
    ],
    form: formFields,
    formDefaults: emptyForm(),
    formWidth: 640,
    createTitle: '新建用户',
    editTitle: '编辑用户',
    emptyText: '暂无账号',
    rowActions: [
      { key: 'update', label: '编辑', permission: 'user:user:update' },
      {
        key: 'enable',
        label: '启用',
        permission: 'user:user:status',
        visible: (row) => row.status !== 1,
        handler: (row) => userApi.updateStatus(row.id, 1),
      },
      {
        key: 'disable',
        label: '停用',
        permission: 'user:user:status',
        visible: (row) => row.status === 1,
        handler: (row) => userApi.updateStatus(row.id, 0),
      },
    ],
  }
}
