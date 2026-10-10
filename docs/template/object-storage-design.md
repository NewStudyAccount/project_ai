# 对象存储 · 通用设计（MinIO / S3 兼容）

> 状态：**模板稿**（对齐 `CLAUDE.md` §2 对象存储、§6.11 上传与清理约束；吸收 `example-system-design.md` / `gateway-design.md` 中已裁决的上传与预签名口径）
> 定位：**通用对象存储能力设计**——客户端封装落点、上传/下载/删除策略、元数据表、配置键、安全与运维边界。各系统**引用本文件**裁剪落地，不复制字段与默认值到系统设计正文。
> 范围：MinIO（S3 兼容）客户端封装、单文件/批量上传约束、预签名 URL、逻辑删除与对象清理边界、元数据落库、限流/幂等/审计接线、网关与 Nginx 不经转 body 的约定。
> 配套：`CLAUDE.md`（§2 技术栈、§2.1 落点原则、§5.4/§5.6 模块边界、§6.9 缓存键、§6.11 上传）、`docs/version-baseline.md`（MinIO SDK 版本唯一取用处）、`gateway-design.md`（§4.9 大文件不经网关中转）、`example-system-design.md`（系统内演示落点）、`docs/test-env.md`（连接信息）、`docs/port-registry.md`（MinIO 端口）
> 约束：本文**不写**具体 IP/端口/AccessKey/Secret/桶生产密钥；版本号**不**在本文复制（只取基线表）

---

## 1. 目标与边界

### 1.1 目标

在多系统容器下提供**可复制**的对象存储接入设计，示范并固定：

- **封装唯一落点**：MinIO SDK 只在 `{system}-framework` 封装一次，业务禁止直绑 SDK
- **文件生命周期**：上传（校验 + UUID 对象名）→ 元数据落库 → 查询/预签名访问 → **逻辑删除**
- **安全基线**：MIME/大小白名单、短 TTL 预签名、敏感信息不入日志、生产密钥不入仓
- **链路正确性**：大文件上传/下载走预签名直连对象存储，**不经网关/业务服务中转 body**
- **横切接线**：上传/预签名/列表限流，关键写幂等 + 操作审计（与系统内审计表约定对齐）

### 1.2 边界（明确不做）

| 不在本设计 | 归属 / 说明 |
|------------|-------------|
| 对象存储集群部署、纠删码、多站点复制 | 运维/基础设施；本文只约定应用侧接入 |
| 用户主数据、登录态、权限硬拦截 | 用户中心 / 认证中心 / 网关（纯网关鉴权） |
| 跨系统共享文件元数据表 / 统一文件中心服务 | **不做**；元数据由**拥有该表的** `{domain}-service` 自治（`CLAUDE.md` §5.6.1） |
| 批量上传默认能力 | 单文件为默认演示/契约；批量由各系统按需另开变更（上限见 §4.1） |
| MinIO 对象**定时**清理 / 生命周期规则依赖 | **不建分布式定时**（`CLAUDE.md` §2.2）；清理走运维脚本 |
| Excel 导入导出、病毒扫描、图片压缩/水印等加工 | 暂不做；需要时另开变更 |
| 在网关或 Nginx 中转大文件 body | 禁止（`gateway-design.md` §4.9） |
| 将 MinIO 客户端放进 `common` 或网关 | 禁止（`CLAUDE.md` §2.1 / `gateway-design.md`） |

---

## 2. 架构位置与职责分层

```text
浏览器 / 管理端 SPA
  │ ① 业务 API（元数据/授权）
  │ ② 预签名 PUT/GET（直连对象存储，不经网关 body）
  ▼
Nginx ──► {system}-gateway ──► {domain}-service
                                │
                                ├─ {system}-framework
                                │    └─ ObjectStorageClient（MinIO SDK 唯一封装）
                                │         · 上传 / 预签名 / 删除 / 存在性探测
                                ├─ MySQL：sys_file（或等价）元数据
                                └─ MinIO / S3（bucket + object）
```

| 能力 | Nginx | 网关 | `{domain}-service` / framework | MinIO |
|------|-------|------|--------------------------------|-------|
| TLS / 静态 | 托管前端、TLS | 不处理 | — | — |
| 文件 body 上传/下载 | **不中转大文件** | **不中转**（可短 TTL 预签名） | 校验 + 写元数据；封装 SDK | 存对象 |
| 预签名 URL 签发 | — | 不签 | **唯一签发点**（短 TTL） | 校验签名 |
| 删除语义 | — | — | `deleted=1` 逻辑删除 | 物理对象延后清理 |
| 鉴权 | 粗防护 | JWT / 登录态 | 权限码软校验 | 预签名即临时授权 |

**硬约束：**

1. 业务代码 MUST NOT 直接依赖 `io.minio.*` 或 S3 SDK；只调用 framework 封装接口。
2. 浏览器/客户端 MUST NOT 获得长期有效的对象存储凭证；只经预签名 URL 或后端代理的小文件场景。
3. 对象名 MUST 使用 UUID（或等价随机唯一名），MUST NOT 使用用户可控原始文件名作对象键。
4. 元数据与对象的一致性以**库为真相源**；对象存在性探测失败不静默当作成功。

---

## 3. 模块落点与封装契约

### 3.1 依赖落点（对齐 `CLAUDE.md` §2.1 / §5.4）

| 模块 | 是否引入 MinIO/S3 | 说明 |
|------|-------------------|------|
| `{system}-common` | **禁止** | 纯基础；无中间件客户端 |
| `{system}-framework` | **必须（唯一）** | SDK 版本取 `docs/version-baseline.md`；提供 `ObjectStorageClient`（或等价）+ `@ConfigurationProperties` |
| `{system}-api` | 禁止 | 仅 DTO/VO + Feign 契约 |
| `{domain}-service` | 依赖 framework，**不直绑 SDK** | 业务只调封装接口 |
| `{system}-gateway` | 禁止 | 不碰对象存储 |

### 3.2 framework 封装接口（形态约定）

命名可按系统微调，职责不得外溢：

| 能力 | 语义 | 失败语义 |
|------|------|----------|
| `put(bucket, objectKey, stream, size, contentType)` | 上传对象 | 类型/大小非法 → 业务/校验错误；IO 失败 → 系统错误并记日志 |
| `presignGet(bucket, objectKey, ttl)` | 预签名下载 | 对象不存在 → 业务错误；TTL 受配置上限约束 |
| `presignPut(bucket, objectKey, ttl)` | （可选）预签名上传 | 默认不用；需要浏览器直传时另开变更并收紧策略 |
| `remove(bucket, objectKey)` | 物理删除对象 | 供运维脚本/补偿；**不**作为业务主路径 |
| `exists(bucket, objectKey)` | 存在性探测 | 用于补偿与排障 |

- 配置绑定：`@ConfigurationProperties`（前缀如 `{system}.minio` 或统一 `object-storage`），**禁止**散落 `@Value`。
- 超时：连接/读取必须可配且有默认上限；禁止无限等待。
- 日志：可记 bucket/objectKey/size/contentType/耗时；**禁止**记 AccessKey/Secret/完整预签名 URL query 中的敏感签名段（可记过期时间）。

### 3.3 与横切能力的接线

| 横切 | 落点 | 建议默认形态（可配，键名 kebab-case） |
|------|------|--------------------------------------|
| `@RateLimit` | 上传 / 列表 / 预签名接口 | 上传 `10/min/用户`；查询/预签名 `60/min/用户`（示例，随变更调整） |
| `@Idempotent` | 删除等关键写 | 键 = `Idempotent-Key` 或 `X-Request-Id`；重复返回**首次 `Result`** |
| 操作审计 | 上传/删除成功后（事务提交后或统一 AOP） | 记操作人/动作/目标；`detail` 禁密码与完整令牌 |
| 发号 | 元数据表主键 | 16 位发号（`CLAUDE.md` §6.4.5）；对象名 UUID 与主键分离 |

---

## 4. 文件生命周期与策略

### 4.1 上传

| 项 | 约定 |
|----|------|
| 默认形态 | **单文件** ≤ 10MB（`CLAUDE.md` §6.11） |
| 批量 | 暂不强制；若系统需要，批量总大小建议 ≤ 50MB，并**先在该系统变更中登记**后实现 |
| 类型 | MIME **白名单**（禁止空 = 全放行）；应用层校验 + 入库 `content_type` 双一致 |
| 对象名 | UUID（含扩展名可选）；`original_name` 仅作展示，**不**作对象键 |
| 元数据 | 落库（见 §5）；出参 `id` 为 String |
| 传输路径 | 小文件可经业务接口 multipart；大文件/下载优先**预签名直传直下**，不经网关中转 body |

推荐默认 MIME 白名单（可按系统覆盖，禁止置空）：

`image/png`、`image/jpeg`、`image/gif`、`application/pdf`

### 4.2 访问（预签名 URL）

| 项 | 约定 |
|----|------|
| 签发方 | **仅**后端 framework（业务接口触发） |
| TTL | 短时有效，默认形态 `10m`；可配，必须有**上限**（禁止签发长期公开 URL） |
| 用途 | 预览 / 下载；上传直传策略若启用同样短 TTL |
| 响应形态 | `{ url, expireAt }`（`expireAt` 建议 ISO 或 epoch，前后端约定一致） |
| 权限 | 预签名是**临时能力凭证**；签发前仍应完成登录态与资源权限软校验 |

### 4.3 删除与清理

| 项 | 约定 |
|----|------|
| 业务删除 | **逻辑删除**（元数据 `deleted=1`）；**不**另设 `status` 与逻辑删除双轨 |
| 物理对象 | 默认保留；清理走**运维脚本**（按 `deleted=1` + 时间窗口） |
| 定时清理 | **禁止** Quartz/XXL-Job/ShedLock 等（`CLAUDE.md` §2.2） |
| 误删 | 逻辑删除可从库恢复元数据；物理对象是否可恢复取决于运维脚本策略与备份 |

### 4.4 失败与幂等

- 网络/5xx 可按 `CLAUDE.md` §6.11 重试策略有限重试；4xx（类型/大小非法）不重试。
- 上传成功但元数据落库失败：记 error 日志（含 objectKey），便于运维脚本补偿；禁止静默丢对象。
- 重复业务删除：走 `@Idempotent`，返回首次 `Result`。

---

## 5. 元数据表（通用形态）

> 各系统在**本库**建表（可名 `sys_file` 或 `{system}_file`）；字段语义对齐下表。完整 SQL 随 openspec 变更提供。

**列约定：** 主键 `id` BIGINT，16 位发号，出参 String；审计 5 字段（`create_time` / `update_time` / `create_by` / `update_by` / `deleted`）；字符串 `NOT NULL` + 默认值；索引 `uk_/idx_表名_字段`，单表 ≤ 5；禁止物理外键。

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|:--:|------|------|
| id | BIGINT | N | — | 主键，16 位发号 |
| bucket | VARCHAR(64) | N | `''` | 桶名 |
| object_key | VARCHAR(255) | N | — | 对象键（**UUID 文件名**） |
| original_name | VARCHAR(255) | N | `''` | 原始文件名（展示用） |
| content_type | VARCHAR(128) | N | `''` | MIME（白名单校验） |
| size_bytes | BIGINT | N | 0 | 字节大小 |
| create_time / update_time | DATETIME | N | | 审计 |
| create_by / update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | 0 未删 / 1 已删（**删除语义**） |

- **唯一**：`uk_表名_object_key (object_key)`
- **索引**：`idx_表名_create_time`（建议）

**逻辑关系（示意）：**

```text
（用户中心）sys_user.id ──（逻辑）──► create_by / update_by
sys_file 1 ──── * 审计日志（target_type=FILE）
```

**对内契约（若系统暴露 Feign）：** 出参 VO 至少含 `id`（String）、`bucket`、`objectKey`、`originalName`、`contentType`、`sizeBytes`；未命中 Fallback 返回约定空/降级，不抛裸异常。

---

## 6. 配置契约

三份 yml（`CLAUDE.md` §6.10）：`application.yml`（无主机/账密）/ `application-local.yml` / `application-test.yml`。  
连接信息真相源：`docs/test-env.md`；端口：`docs/port-registry.md`；生产密钥：Nacos / 密钥管理 / Jenkins Credentials（`CLAUDE.md` §6.7）。

**键名形态（前缀可按系统，如 `blog.minio`；结构须一致）：**

```yaml
# 结构示意；endpoint/账密不得写入公共 application.yml
example:            # 或 blog / {system}
  minio:
    bucket: example           # 默认桶；可按业务分桶，需登记
    endpoint: ""              # 仅 local/test yml 或 Nacos
    access-key: ""            # 禁止入仓生产值
    secret-key: ""            # 禁止入仓生产值
    upload-max-bytes: 10485760
    allowed-content-types:
      - image/png
      - image/jpeg
      - image/gif
      - application/pdf
    presign-ttl: 10m
    connect-timeout: 5s
    read-timeout: 30s
```

| 键 | 要求 |
|----|------|
| `allowed-content-types` | 白名单非空；禁止空数组表示全放行 |
| `upload-max-bytes` | 默认 ≤ 10MB（单文件）；调整须走变更 |
| `presign-ttl` | 短 TTL；禁止无上限 |
| endpoint / AK / SK | 仅 local/test 或 Nacos；**生产禁止入仓** |

绑定类示例：`ObjectStorageProperties` / `MinioProperties`；业务读配置一律经该绑定。

---

## 7. 安全约定

- **密钥分级**（唯一权威 `CLAUDE.md` §6.7）：生产 AK/SK/连接串禁止入仓；测试/开发账密仅 `docs/test-env.md` / `application-local|test.yml`，须标注环境。
- **上传安全**：白名单 + 大小上限双校验；对象名 UUID，防路径穿越与覆盖；`Content-Type` 与白名单一致。
- **访问安全**：预签名短 TTL；不把预签名完整 URL 打进 info 日志；泄露后可依赖 TTL 自然过期 + 对象逻辑删除。
- **权限**：上传/删除/预签名接口仍走网关登录态 + 权限码软校验（如 `{system}:file:upload`）；预签名不替代业务鉴权。
- **审计**：上传/删除记审计；`detail` 禁密码、完整令牌、Secret。
- **网关/Nginx**：不配置对象存储代理改写大包 body；CORS 不在此放行对象存储域名上的任意跨域写（若浏览器直传，需单独收敛）。

---

## 8. 部署、自检与验收

### 8.1 部署要点

1. 准备 MinIO（或 S3 兼容服务）：建桶、网络策略仅内网/必要出口；账密入 Nacos 或测试文档。
2. `framework` 配置指向桶与策略；服务健康检查不依赖 MinIO 强可用（避免启动即失败的脆弱耦合，可按系统选择懒加载客户端）。
3. 联调：上传 → 列表 → 预签名访问 → 逻辑删除；校验超限与非法 MIME 拒绝。

### 8.2 提交前自检

```bash
cd backend/<system> && mvn -q compile
# 若涉及管理端文件页
cd frontend/<system> && npm run lint && npm run type-check
```

- [ ] MinIO/S3 依赖只出现在 `{system}-framework`（或等价唯一封装模块）
- [ ] 版本与 `docs/version-baseline.md` 一致（当前登记：MinIO SDK **9.0.3**，可退 8.5.17）
- [ ] `allowed-content-types` 非空；`upload-max-bytes` / `presign-ttl` 已配
- [ ] 无生产 AK/SK 入仓；无完整预签名 URL / Secret 进日志
- [ ] 业务删除为逻辑删除；未引入分布式定时清理
- [ ] 大文件未走网关/Nginx 中转 body

### 8.3 验收清单

- [ ] 上传合法文件成功且元数据字段完整（含 UUID objectKey）
- [ ] 超限 / 非白名单 MIME 被拒绝
- [ ] 预签名 URL 在 TTL 内可访问，过期后不可用
- [ ] 逻辑删除后列表不可见（或按契约过滤）；物理清理策略有运维说明
- [ ] 重复删除/重复提交（幂等键）返回首次 `Result`
- [ ] 限流触发返回 HTTP 429 + 系统段 `10003`

---

## 9. 与系统设计文档的关系

| 文档 | 职责 | 不要放什么 |
|------|------|------------|
| **本文件** | 对象存储通用策略、封装、表形态、配置键、安全与验收 | 具体系统权限码全表、生产密钥、端口 |
| 各系统 `*-design.md` | 只写**裁剪取值**（桶名、白名单差异、接口权限码、是否暴露对内 Feign） | 复制 SDK 封装细节、复制本表字段全表 |
| `CLAUDE.md` §6.11 | 上传总约束（大小/批量/UUID/逻辑删除） | 实现代码 |
| `docs/version-baseline.md` | SDK 版本唯一登记 | 在设计正文复制版本号 |
| `gateway-design.md` | 预签名与不经中转 body 的边缘约束 | 对象存储业务 API |

**系统设计引用示例：**  
「对象存储按 `object-storage-design.md` 裁剪；桶 = `{bucket}`；MIME 白名单 / 预签名 TTL 若与默认不同，在此登记差异。」

---

## 10. 相关文档

| 文档 | 内容 |
|------|------|
| `CLAUDE.md` | 项目全局契约（§2 / §2.1 / §6.7 / §6.11） |
| `docs/version-baseline.md` | MinIO SDK 等组件版本 |
| `docs/test-env.md` | 测试环境 MinIO 连接与测试账密 |
| `docs/port-registry.md` | MinIO 端口登记 |
| `gateway-design.md` | 大文件预签名、不经网关中转 |
| `example-system-design.md` | 系统内文件能力演示与 API 形态参照；文件管理页交互见 **§7.1** |
| `assets/rbac-admin-proto.html` | 文件管理页可运行交互原型（上传/预签名/逻辑删除示意） |
| `rbac-design.md` | 文件接口权限标识如何进菜单/按钮 |
