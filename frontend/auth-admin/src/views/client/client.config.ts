/**
 * Client 管理页 PageSchema（generic-admin-page-design.md §3）
 * 字段/枚举与 src/types 的 ClientRequest / ClientVO 一致（前端不自造枚举值）
 */
import { clientApi } from '@/api/client'
import type { ClientRequest, ClientSecretVO, ClientVO } from '@/types'
import type { CrudPageSchema, FormFieldDef } from '@/types/crud'

const emptyForm = (): ClientRequest => ({
  clientId: '',
  clientName: '',
  clientType: 'PUBLIC',
  clientAuthMethod: 'NONE',
  grantTypes: 'authorization_code,refresh_token',
  redirectUris: '',
  scopes: 'openid,profile',
  requirePkce: 1,
  requireConsent: 0,
  reuseRefreshTokens: 0,
  accessTokenTtlSec: 600,
  refreshTokenTtlSec: 604800,
  systemCode: '',
  owner: '',
  env: '',
  enabled: 1,
  remark: '',
})

const formFields: FormFieldDef[] = [
  { prop: 'clientId', label: 'Client ID', type: 'input', required: true, disabledWhen: (mode) => mode === 'edit' },
  { prop: 'clientName', label: '名称', type: 'input', required: true },
  {
    prop: 'clientType',
    label: '客户端类型',
    type: 'select',
    options: [
      { value: 'PUBLIC', label: 'PUBLIC' },
      { value: 'CONFIDENTIAL', label: 'CONFIDENTIAL' },
    ],
  },
  {
    prop: 'clientAuthMethod',
    label: '认证方式',
    type: 'select',
    options: [
      { value: 'NONE', label: 'NONE' },
      { value: 'CLIENT_SECRET_BASIC', label: 'CLIENT_SECRET_BASIC' },
      { value: 'CLIENT_SECRET_POST', label: 'CLIENT_SECRET_POST' },
    ],
  },
  { prop: 'grantTypes', label: 'grant_types', type: 'input', required: true, placeholder: 'authorization_code,refresh_token' },
  { prop: 'redirectUris', label: 'redirect_uris', type: 'textarea', required: true, placeholder: '精确白名单，逗号分隔' },
  { prop: 'scopes', label: 'scopes', type: 'input', required: true, placeholder: 'openid,profile' },
  { prop: 'requirePkce', label: 'require_pkce', type: 'switch' },
  { prop: 'requireConsent', label: 'require_consent', type: 'switch' },
  {
    prop: 'reuseRefreshTokens',
    label: 'reuse_refresh',
    type: 'number',
    min: 0,
    max: 0,
    disabled: true,
    hint: '必须为 0（refresh token 一次性）',
  },
  { prop: 'accessTokenTtlSec', label: 'access_ttl', type: 'number', min: 300, max: 900 },
  { prop: 'refreshTokenTtlSec', label: 'refresh_ttl', type: 'number', min: 60 },
  { prop: 'systemCode', label: '系统编码', type: 'input' },
  { prop: 'owner', label: '负责人', type: 'input' },
  { prop: 'env', label: '环境', type: 'input' },
  { prop: 'remark', label: '备注', type: 'textarea' },
]

export function buildClientSchema(): CrudPageSchema<ClientVO, ClientRequest> {
  return {
    permissionPrefix: 'auth:client',
    api: {
      page: (q) => clientApi.page(q as unknown as Parameters<typeof clientApi.page>[0]),
      create: (data) => clientApi.create(data),
      update: (id, data) => clientApi.update(id, data),
      remove: (id) => clientApi.remove(id),
    },
    filters: [
      { prop: 'clientId', type: 'input', placeholder: 'clientId', width: 180 },
      { prop: 'systemCode', type: 'input', placeholder: '系统编码', width: 160 },
      {
        prop: 'enabled',
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
      { prop: 'id', label: 'ID', width: 180, type: 'copy' },
      { prop: 'clientId', label: 'Client ID', width: 160 },
      { prop: 'clientName', label: '名称', width: 140 },
      {
        prop: 'clientType',
        label: '类型',
        width: 110,
        type: 'enum',
        options: [
          { value: 'PUBLIC', label: 'PUBLIC' },
          { value: 'CONFIDENTIAL', label: 'CONFIDENTIAL' },
        ],
      },
      { prop: 'grantTypes', label: '授权类型', minWidth: 160, tooltip: true },
      { prop: 'scopes', label: 'Scopes', width: 140, tooltip: true },
      { prop: 'systemCode', label: '系统', width: 100 },
      {
        prop: 'enabled',
        label: '状态',
        width: 80,
        type: 'pill',
        options: [
          { value: 1, label: '启用', pillTone: 'success' },
          { value: 0, label: '停用', pillTone: 'warning' },
        ],
      },
    ],
    form: formFields,
    formDefaults: emptyForm(),
    formWidth: 720,
    createTitle: '新建客户端',
    editTitle: '编辑客户端',
    rowActions: [
      { key: 'update', label: '编辑', permission: 'auth:client:update' },
      {
        key: 'enable',
        label: '启用',
        permission: 'auth:client:update',
        visible: (row) => row.enabled === 0,
        handler: (row) => clientApi.updateStatus(row.id, 1),
      },
      {
        key: 'disable',
        label: '停用',
        permission: 'auth:client:update',
        visible: (row) => row.enabled === 1,
        handler: (row) => clientApi.updateStatus(row.id, 0),
      },
      {
        key: 'reset-secret',
        label: '重置密钥',
        permission: 'auth:client:secret',
        confirm: (row) => `确认重置客户端 ${row.clientId} 的密钥？旧密钥将立即失效。`,
        handler: (row) => clientApi.resetSecret(row.id),
      },
      {
        key: 'delete',
        label: '删除',
        type: 'danger',
        permission: 'auth:client:delete',
        confirm: (row) => `确认删除客户端 ${row.clientId}？`,
        successMessage: '已删除',
      },
    ],
  }
}

export type { ClientSecretVO }
