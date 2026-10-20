/**
 * 标签管理页 PageSchema（generic-admin-page-design.md §3）
 * 字段与 src/api/content 的 TagVO / TagRequest 一致（前端不自造枚举值）
 */
import { createTag, deleteTag, pageTags, updateTag, type TagVO } from '@/api/content'
import { compactQuery } from '@/utils/query'
import type { CrudPageSchema, FormFieldDef } from '@/types/crud'

/** TagRequest 前端镜像（Bean Validation：name / slug 必填） */
export type TagForm = {
  name: string
  slug: string
  sort: number
  status: number
}

const formDefaults = (): TagForm => ({ name: '', slug: '', sort: 0, status: 1 })

const formFields: FormFieldDef[] = [
  { prop: 'name', label: '名称', type: 'input', required: true, placeholder: '标签名' },
  { prop: 'slug', label: 'slug', type: 'input', required: true, placeholder: '小写连字符' },
  { prop: 'sort', label: '排序', type: 'number', min: 0 },
  { prop: 'status', label: '状态', type: 'switch' },
]

export function buildTagSchema(): CrudPageSchema<TagVO, TagForm> {
  return {
    api: {
      page: (q) => pageTags(compactQuery(q) as Parameters<typeof pageTags>[0]),
      create: (data) => createTag(data),
      update: (id, data) => updateTag(id, data),
      remove: (id) => deleteTag(id),
    },
    filters: [
      { prop: 'name', type: 'input', placeholder: '名称', width: 200 },
      {
        prop: 'status',
        type: 'select',
        placeholder: '状态',
        width: 120,
        options: [
          { value: 1, label: '启用' },
          { value: 0, label: '停用' },
        ],
      },
    ],
    columns: [
      { prop: 'name', label: '名称', minWidth: 160 },
      { prop: 'slug', label: 'slug', width: 160 },
      { prop: 'articleCount', label: '引用数', width: 100 },
      { prop: 'sort', label: '排序', width: 80 },
      {
        prop: 'status',
        label: '状态',
        width: 90,
        type: 'pill',
        options: [
          { value: 1, label: '启用', pillTone: 'success' },
          { value: 0, label: '停用', pillTone: 'warning' },
        ],
      },
    ],
    primaryActions: [{ key: 'create', label: '新建标签', type: 'primary', permission: 'blog:tag:create' }],
    rowActions: [
      { key: 'update', label: '编辑', permission: 'blog:tag:update' },
      {
        key: 'delete',
        label: '删除',
        type: 'danger',
        permission: 'blog:tag:delete',
        confirm: (row) => `确认删除标签「${row.name}」？删除前会做引用检查，已被文章引用时将拒绝删除。`,
        successMessage: '已删除',
      },
    ],
    form: formFields,
    formDefaults: formDefaults(),
    formWidth: 420,
    createTitle: '新建标签',
    editTitle: '编辑标签',
    emptyText: '暂无标签',
  }
}
