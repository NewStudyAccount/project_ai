export interface Result<T> {
  code: number
  msg: string
  data: T
}

export interface PageQuery {
  current: number
  size: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

export interface FileMeta {
  id: string
  /** 归属服务商编码（指向 sys_storage_provider.code） */
  storageCode?: string
  bucket: string
  objectKey: string
  /** 稳定公开 URL（publicBaseUrl + objectKey，无签名、无过期） */
  url?: string
  originalName: string
  contentType: string
  sizeBytes: number
  deleted: number
  createBy?: string
  createTime?: string
}

export interface FileUrlResult {
  url: string
}

/**
 * 对象存储服务商配置（出参；SK 仅掩码，禁明文）。
 * 对齐 docs/object-storage-design.md §5.2 / §5.3 与后端 blog-storage-provider 契约。
 */
export interface StorageProviderVO {
  id: string
  code: string
  name: string
  /** MINIO / AWS_S3 / ALIYUN_OSS / TENCENT_COS / HUAWEI_OBS / CUSTOM_S3 */
  providerType: string
  endpoint: string
  region: string
  bucket: string
  accessKey: string
  /** SK 掩码（如 ****2345），禁明文 */
  secretKeyMasked: string
  /** 1=path-style / 0=virtual-hosted */
  pathStyle: number
  /** 公开读基址（无尾斜杠） */
  publicBaseUrl: string
  /** 0 停用 / 1 启用（控制新上传） */
  status: number
  /** 1=默认服务商（同库仅一条） */
  isDefault: number
  description: string
  createTime?: string
  updateTime?: string
}

export interface StorageProviderPageQuery {
  current?: number
  size?: number
  keyword?: string
  providerType?: string
  status?: number
}

/**
 * 服务商新建/编辑入参。
 * secretKey 只写：新建必填；编辑留空=不修改。
 */
export interface StorageProviderInput {
  code: string
  name: string
  providerType: string
  endpoint: string
  region: string
  bucket: string
  accessKey: string
  /** 留空=不修改（编辑时）；新建必填 */
  secretKey?: string
  pathStyle: number
  publicBaseUrl: string
  status: number
  isDefault: number
  description?: string
}

export interface StorageProviderStatusInput {
  status: number
}

/**
 * 服务商类型枚举（与后端 StorageProviderTypeEnum 一致）。
 * 后端新增类型时须同步本常量（前端不依赖后端包）。
 */
export const PROVIDER_TYPES: ReadonlyArray<{ value: string; label: string }> = [
  { value: 'MINIO', label: 'MINIO' },
  { value: 'AWS_S3', label: 'AWS_S3' },
  { value: 'ALIYUN_OSS', label: 'ALIYUN_OSS' },
  { value: 'TENCENT_COS', label: 'TENCENT_COS' },
  { value: 'HUAWEI_OBS', label: 'HUAWEI_OBS' },
  { value: 'CUSTOM_S3', label: 'CUSTOM_S3' },
]

/** 按 providerType 推荐 path_style 默认与 region 是否必填 */
export function providerTypeDefaults(providerType: string): { pathStyle: number; regionRequired: boolean } {
  switch (providerType) {
    case 'MINIO':
      return { pathStyle: 1, regionRequired: false }
    case 'AWS_S3':
    case 'ALIYUN_OSS':
    case 'TENCENT_COS':
    case 'HUAWEI_OBS':
      return { pathStyle: 0, regionRequired: true }
    default:
      return { pathStyle: 1, regionRequired: false }
  }
}

export interface MenuVO {
  id: string
  parentId: string
  type: number
  name: string
  permission: string
  path: string
  component: string
  icon: string
  hidden: number
  requiresAuth: number
  sort: number
  status: number
  children: MenuVO[]
}

export interface RoleVO {
  id: string
  roleCode: string
  roleName: string
  dataScope: number
  sort: number
  status: number
  remark: string
  menuIds: string[]
}

export interface AuditVO {
  id: string
  action: string
  actorUserId: string
  targetType: string
  targetId: string
  detail: string
  ip: string
  createTime: string
}

export interface UserOption {
  id: string
  username: string
  displayName: string
}
