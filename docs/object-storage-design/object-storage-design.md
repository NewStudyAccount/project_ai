# 对象存储 · 通用设计（独立模块 / S3 多服务商）

> 状态：**设计稿 v2**（在原 MinIO 通用设计上扩展：独立存储模块、S3 协议多服务商、服务商配置能力与管理端双页）
> 定位：**通用对象存储能力设计**——独立模块落点、S3 多服务商适配、上传/下载/删除策略、元数据与服务商配置表、管理端页面、安全与运维边界。各系统**引用本文件**裁剪落地，不复制字段与默认值到系统设计正文。
> 范围：S3 兼容客户端封装与多服务商配置、单文件/批量上传约束、预签名 URL、逻辑删除与对象清理边界、元数据落库、服务商配置管理、限流/幂等/审计接线、网关与 Nginx 不经转 body 的约定、文件管理页与服务商配置页。
> 配套：`CLAUDE.md`（§2 技术栈、§2.1 落点原则、§5.4/§5.6 模块边界、§6.9 缓存键、§6.11 上传、§6.7 密钥分级）、`docs/version-baseline.md`（S3/MinIO SDK 版本唯一取用处）、`gateway-design.md`（§4.9 大文件不经网关中转）、`admin-ui-design.md`（管理端视觉与 P-List 布局）、`example-system-design.md`（系统内演示落点）、`docs/test-env.md`（连接信息）、`docs/port-registry.md`（对象存储端口）
> 约束：本文**不写**具体 IP/端口/AccessKey/Secret/桶生产密钥；版本号**不**在本文复制（只取基线表）
> 修订说明：v1 以「MinIO 客户端放 `framework` 单点封装」为主；v2 将能力抽为**独立模块**、协议改为**S3 多服务商**，并新增**服务商配置**（库表 + API + 管理端配置页）。既有生命周期/预签名/逻辑删除/安全红线继续有效；下游系统引用时以本版为准。
> 原型：本目录 [`prototype.html`](./prototype.html)（可交互）· [`assets/proto-file-list.svg`](./assets/proto-file-list.svg) · [`assets/proto-storage-provider.svg`](./assets/proto-storage-provider.svg)（静态原型图）

---

## 1. 目标与边界

### 1.1 目标

在多系统容器下提供**可复制**的对象存储接入设计，示范并固定：

- **独立模块落点**：对象存储能力落在**独立模块** `{system}-object-storage`（名称可裁剪），统一处理文件对象的上传/下载/预签名/删除；业务禁止直绑 S3/MinIO SDK
- **S3 协议多服务商**：以 **S3 协议**为唯一客户端契约，支持 MinIO、AWS S3、阿里云 OSS（S3 兼容）、腾讯云 COS（S3 兼容）、华为 OBS（S3 兼容）及其它 S3 兼容服务；通过服务商类型 + endpoint/region/path-style 等参数适配，**不**为每家厂商单开一套业务代码
- **服务商可配置**：存储服务商接入信息可经管理端配置页维护（库表真相源 + 引导默认），支持测试连通性、启用/停用、指定默认服务商
- **文件生命周期**：上传（校验 + UUID 对象名）→ 元数据落库 → 查询/预签名访问 → **逻辑删除**
- **安全基线**：MIME/大小白名单、短 TTL 预签名、密钥加密存储与脱敏展示、敏感信息不入日志、生产密钥不入仓
- **链路正确性**：大文件上传/下载走预签名直连对象存储，**不经网关/业务服务中转 body**
- **横切接线**：上传/预签名/列表/服务商配置限流，关键写幂等 + 操作审计
- **管理端双页**：**文件管理展示页**（上传/列表/预览下载/逻辑删除）+ **对象存储服务商配置页**（服务商 CRUD/连通性/默认项）

### 1.2 边界（明确不做）

| 不在本设计 | 归属 / 说明 |
|------------|-------------|
| 对象存储集群部署、纠删码、多站点复制、厂商托管生命周期规则 | 运维/基础设施；本文只约定应用侧接入 |
| 用户主数据、登录态、权限硬拦截 | 用户中心 / 认证中心 / 网关（纯网关鉴权） |
| 跨系统共享文件元数据表 / 统一文件中心**服务** | **不做**；元数据与服务商配置由**拥有该表的** `{domain}-service` 自治（`CLAUDE.md` §5.6.1）。独立模块指**模块级封装**，不是跨系统统一文件中心微服务 |
| 批量上传默认能力 | 单文件为默认演示/契约；批量由各系统按需另开变更（上限见 §4.1） |
| 对象**定时**清理 / 生命周期规则依赖 | **不建分布式定时**（`CLAUDE.md` §2.2）；清理走运维脚本 |
| Excel 导入导出、病毒扫描、图片压缩/水印等加工 | 暂不做；需要时另开变更 |
| 在网关或 Nginx 中转大文件 body | 禁止（`gateway-design.md` §4.9） |
| 将 S3/MinIO 客户端放进 `common` 或网关 | 禁止（`CLAUDE.md` §2.1 / `gateway-design.md`） |
| 浏览器直传（`presignPut`）默认开启 | 默认关闭；启用须另开变更并登记 TTL/CORS/阈值（见 §4.1） |
| 运行时切换服务商后**迁移存量对象** | 不自动搬迁；存量文件仍按 `sys_file.storage_code` 回源原服务商（见 §5.1） |

---

## 2. 架构位置与职责分层

### 2.1 目标架构

```text
浏览器 / 管理端 SPA
  │ ① 业务 API（multipart 小文件上传、元数据、签发预签名、服务商配置 CRUD）
  │ ② 预签名 GET（下载/预览直连；预签名 PUT 直传为可选能力，默认关闭）
  ▼
Nginx ──► {system}-gateway ──► {domain}-service
                                │
                                ├─ {system}-object-storage（独立模块）
                                │    ├─ S3Provider SPI + 多服务商适配
                                │    ├─ ObjectStorageClient（上传/下载/预签名/删除/探测）
                                │    └─ ProviderConfigResolver（yml 引导 + DB 运行时配置）
                                ├─ MySQL：sys_storage_provider（服务商配置）
                                │        sys_file（文件元数据，含 storage_code）
                                └─ S3 兼容对象存储（MinIO / AWS / OSS / COS / OBS / …）
```

### 2.2 职责分层

| 能力 | Nginx | 网关 | `{domain}-service` | `{system}-object-storage` | S3 对象存储 |
|------|-------|------|--------------------|---------------------------|-------------|
| TLS / 静态 | 托管前端、TLS | 不处理 | — | — | — |
| 文件 body 上传/下载 | **不中转大文件** | **不中转** | 校验 + 写元数据 + 权限/审计 | 封装协议读写、预签名 | 存对象 |
| 预签名 URL 签发 | — | 不签 | 触发签发、软校验权限 | **唯一签发实现**（短 TTL） | 校验签名 |
| 服务商配置 | — | 不碰 | 配置 CRUD、连通性测试入口 | 解析配置、建客户端、测连通 | 被连通性探测 |
| 删除语义 | — | — | `deleted=1` 逻辑删除 | 物理 `remove`（仅运维/补偿） | 物理对象延后清理 |
| 鉴权 | 粗防护 | JWT / 登录态 | 权限码软校验 | 不鉴权（能力层） | 预签名即临时授权 |

### 2.3 硬约束

1. 业务代码 MUST NOT 直接依赖 `io.minio.*`、`software.amazon.awssdk.services.s3.*` 或其它 S3 SDK；只调用 `{system}-object-storage` 封装接口。
2. 浏览器/客户端 MUST NOT 获得长期有效的对象存储凭证；只经预签名 URL 或后端代理的小文件场景。
3. 对象名 MUST 使用 UUID（或等价随机唯一名），MUST NOT 使用用户可控原始文件名作对象键。
4. 元数据与对象的一致性以**库为真相源**；对象存在性探测失败不静默当作成功。
5. 服务商 `secret-key` MUST 加密落库；接口出参 MUST 脱敏；日志 MUST NOT 打印 SK/完整预签名 URL。
6. 运行时切换默认服务商 **只影响新上传**；历史文件 MUST 按自身 `storage_code` 回源，禁止批量静默改写。

---

## 3. 独立模块落点与封装契约

### 3.1 模块落点（对齐并扩展 `CLAUDE.md` §2.1 / §5.4）

| 模块 | 是否引入 S3/MinIO SDK | 说明 |
|------|----------------------|------|
| `{system}-common` | **禁止** | 纯基础；无中间件客户端 |
| **`{system}-object-storage`（独立模块，v2 新增）** | **必须（唯一）** | S3 多服务商适配 + 对象读写/预签名；SDK 版本取 `docs/version-baseline.md` |
| `{system}-framework` | **禁止再引入第二套 SDK** | 可依赖 `{system}-object-storage` 做装配/横切接线；不再自带独立 MinIO 装配为唯一落点 |
| `{system}-api` | 禁止 | 仅 DTO/VO + Feign 契约 |
| `{domain}-service` | 依赖 object-storage，**不直绑 SDK** | 文件/业务只调封装接口；服务商配置 Service 可放本域 |
| `{system}-gateway` | 禁止 | 不碰对象存储 |

**模块边界：**

- `{system}-object-storage` = **与业务无关的对象存储能力层**（协议适配、客户端、配置解析、连通性探测）。
- 文件元数据（`sys_file`）与服务商配置表（`sys_storage_provider`）的 **Repository/Service** 按 `CLAUDE.md` §5.6.1 归**拥有表的服务模块**；object-storage 模块提供能力接口，不强制持有业务 Entity。
- 若系统规模小，允许把文件 Service 与 object-storage 同模块落地，但 **SDK 依赖仍只允许出现在 object-storage 包/模块**，禁止散落到 Controller。

**与 `CLAUDE.md` 的关系：** 本设计将 v1「MinIO 封装进 framework」演进为「独立 object-storage 模块」；落地系统若采用本模块形态，须在对应变更中登记，并按需回填 `CLAUDE.md` §5.4 模块表（本文件不直接改全局契约）。未采用独立模块的存量系统可暂保持 framework 内封装，但 **多服务商与配置能力仍以本文件为准**。

### 3.2 S3 多服务商适配

#### 3.2.1 协议选型

| 项 | 约定 |
|----|------|
| 协议 | **Amazon S3 REST API**（含 SigV4）；各厂商走其 **S3 兼容** endpoint |
| 客户端底座 | 优先 **AWS SDK for Java 2.x `s3`**（中立、多服务商兼容面最好）；或经验证的 MinIO SDK（S3 兼容）——**二选一，禁止双栈并行** |
| 版本 | **唯一登记处** `docs/version-baseline.md`；本文件不写版本号 |
| 适配层 | `ObjectStorageClient` 稳定接口 + 按 `providerType` 装配客户端参数（endpoint / region / path-style / credentials） |
| 厂商差异 | 只允许出现在适配层（如 path-style、region 默认、预签名实现细节）；禁止渗透到业务 Service |

#### 3.2.2 服务商类型（`provider_type`）

| code | 典型 endpoint 形态 | path-style 默认 | 说明 |
|------|-------------------|----------------|------|
| `MINIO` | `http(s)://host:9000` | **path-style** | 自建/私有部署；region 可空或自定义 |
| `AWS_S3` | `https://s3.<region>.amazonaws.com` | virtual-hosted | 需 region |
| `ALIYUN_OSS` | `https://oss-<region>.aliyuncs.com` | 视兼容模式 | 使用 OSS 的 S3 兼容能力 |
| `TENCENT_COS` | `https://cos.<region>.myqcloud.com` | 视兼容模式 | 使用 COS 的 S3 兼容能力 |
| `HUAWEI_OBS` | `https://obs.<region>.myhuaweicloud.com` | 视兼容模式 | 使用 OBS 的 S3 兼容能力 |
| `CUSTOM_S3` | 任意 S3 兼容 | 可配 | 其它兼容实现兜底 |

- `provider_type` 只决定**推荐默认参数**（path-style、region 是否必填等）；实际连接参数以配置为准。
- 新增服务商类型 = 适配层增加枚举与默认参数；**禁止**为此新开业务代码分支。

#### 3.2.3 连接参数（服务商配置字段语义）

| 参数 | 必填 | 说明 |
|------|:--:|------|
| `endpoint` | Y | S3 API 入口（含协议与端口） |
| `region` | 视类型 | AWS/OSS/COS/OBS 通常必填；MinIO 可空（或与服务端 region 对齐，否则可能 `SignatureDoesNotMatch`） |
| `bucket` | Y | 默认桶；可按业务分桶并在服务商或文件上登记 |
| `accessKey` / `secretKey` | Y | 凭证；SK 加密存储 |
| `pathStyle` | Y | `1` path-style / `0` virtual-hosted-style |
| `connectTimeout` / `readTimeout` | 建议 | 必须有默认上限；禁止无限等待 |

**连通性探测（配置页「测试连通」）：** `headBucket` 或等价探测 + 可选 `exists(探测对象)`；失败返回可读原因（网络/签名/桶不存在/无权限），**禁止**把 SK 打进错误详情。

### 3.3 framework / 装配契约

| 能力 | 语义 | 失败语义 |
|------|------|----------|
| `put(objectKey, stream, size, contentType)` | 上传对象（经解析后的活动服务商） | 类型/大小非法 → 业务/校验错误；IO/签名失败 → 系统错误并记日志 |
| `getBytes(objectKey)` | 读取小对象字节（正文等） | 对象不存在/IO 失败 → 业务/系统错误 |
| `presignGet(objectKey, ttl)` | 预签名下载 | 对象不存在 → 业务错误；TTL 受配置上限约束 |
| `presignPut(objectKey, ttl)` | （可选）预签名上传 | 默认不用；需要浏览器直传时另开变更并收紧策略 |
| `remove(objectKey)` | 物理删除对象 | 供运维脚本/补偿（§4.5）；**不**作为业务主路径 |
| `exists(objectKey)` | 存在性探测 | 用于补偿与排障 |
| `providerStatus()` / `testConnection(providerCode)` | 服务商健康/连通 | 配置页与部署自检使用 |

**多服务商解析（`ProviderConfigResolver`）：**

| 场景 | 解析顺序 |
|------|----------|
| 读/删**存量**文件 | `sys_file.storage_code` → 对应服务商配置（含已停用：允许读/删，禁止新上传） |
| **新上传** | 请求显式 `storageCode`（若业务允许）→ 系统默认服务商（`is_default=1`）→ yml 引导默认 |
| 无任何可用配置 | 明确失败（校验/业务错误），禁止静默用错误端点重试打满日志 |

- 客户端实例可按 `providerCode` 缓存；配置变更（更新/停用/改默认）后**失效缓存**。
- 配置绑定：`@ConfigurationProperties`（引导默认）+ DB 运行时配置；**禁止**散落 `@Value`。
- 日志：可记 providerCode/bucket/objectKey/size/contentType/耗时；**禁止**记 AccessKey/Secret/完整预签名 URL query 中的敏感签名段（可记过期时间）。

### 3.4 与横切能力的接线

| 横切 | 落点 | 建议默认形态（可配，键名 kebab-case） |
|------|------|--------------------------------------|
| `@RateLimit` | 上传 / 列表 / 预签名 / 服务商配置写接口 | 上传 `10/min/用户`；查询/预签名 `60/min/用户`；配置写 `20/min/用户`（示例，随变更调整） |
| `@Idempotent` | 删除文件、删除/停用服务商等关键写 | 键 = `Idempotent-Key` 或 `X-Request-Id`；重复返回**首次 `Result`** |
| 操作审计 | 上传/删除文件、服务商配置变更/测试连通成功后 | 记操作人/动作/目标；`detail` 禁密码与完整令牌/Secret |
| 发号 | 元数据表、服务商表主键 | 16 位发号（`CLAUDE.md` §6.4.5）；对象名 UUID 与主键分离 |

---

## 4. 文件生命周期与策略

### 4.1 上传

| 项 | 约定 |
|----|------|
| 默认形态 | **单文件** ≤ 10MB（`CLAUDE.md` §6.11） |
| 批量 | **默认不提供**；需要时另开变更，批量总大小建议 ≤ 50MB，并**先在该系统变更中登记**后实现 |
| 类型 | MIME **白名单**（禁止空 = 全放行）；应用层校验 + 入库 `content_type` 双一致 |
| 对象名 | UUID（含扩展名可选）；`original_name` 仅作展示，**不**作对象键 |
| 存储位置 | 元数据落库（§5.1），并记录 `storage_code` + `bucket` |
| 传输路径 | 见下方「传输路径分流」 |

推荐默认 MIME 白名单（可按系统覆盖，禁止置空）：

`image/png`、`image/jpeg`、`image/gif`、`application/pdf`

#### 传输路径分流（强制）

| 场景 | 默认路径 | 说明 |
|------|----------|------|
| **单文件上传**（默认契约，≤10MB） | 业务接口 **multipart** | 浏览器 → Nginx → 网关 → `{domain}-service` 校验 → object-storage `put`；元数据/限流/幂等/审计在业务侧完成 |
| **预览 / 下载** | **预签名 GET 直连** | 业务只返回 `{ url, expireAt }`，浏览器直连对象存储；**禁止**业务服务或网关中转下载 body |
| **预签名 PUT 直传** | **默认不启用** | 属可选能力；需要浏览器直传时**另开变更**，并登记 TTL、CORS、直传桶与阈值后才可开启 |
| 网关 / Nginx 代理对象存储 body | **禁止** | 与 `gateway-design.md` §4.9 一致 |
| 业务服务反向代理文件流 | **禁止** | 上传只收 multipart 后写对象；下载只签预签名 |

**启用 `presignPut` 后的补充分流（仅当变更已登记）：**

- 直传优先用于超过系统登记 multipart 阈值的文件（阈值在该系统变更中写明，不得高于单文件上限约束）。
- 未登记直传的场景，一律仍走 multipart 默认契约；**禁止**以「性能优化」为由静默改为直传。

**边界澄清：** 本文「大文件不经网关中转」指**不以网关/业务服务做对象 body 反代**；默认 multipart 上传的 body 进入业务服务属正常校验路径，与本条不冲突。单文件上限始终 ≤ 10MB（`CLAUDE.md` §6.11），超出应拒绝而非改走中转。

### 4.2 访问（预签名 URL）

| 项 | 约定 |
|----|------|
| 签发方 | **仅**后端 object-storage 封装（由业务接口触发） |
| TTL | 短时有效，默认形态 `10m`；可配，必须有**上限**（禁止签发长期公开 URL） |
| 用途 | 预览 / 下载；上传直传策略若启用同样短 TTL |
| 响应形态 | `{ url, expireAt }`（格式见下，**全系统统一**） |
| 权限 | 预签名是**临时能力凭证**；签发前仍应完成登录态与资源权限软校验 |
| 多服务商 | 按该文件的 `storage_code` 解析服务商后签发；URL 指向**该服务商** endpoint |

**`expireAt` 格式（已锁定，禁止各系统另选）：**

| 字段 | 类型 | 语义 | 示例 |
|------|------|------|------|
| `url` | `string` | 预签名 URL（含查询签名） | `https://...` |
| `expireAt` | `number` | **epoch 毫秒**，预签名失效时刻 | `1730000000000` |

- 前端可用 `Date.now() < expireAt` 直接判断是否过期，无需解析字符串。
- 禁止返回本地格式时间字符串（如 `yyyy-MM-dd HH:mm:ss`）、禁止混用秒级 epoch。
- 日志中可记 `expireAt`，**禁止**记完整 `url`（含签名 query，见 §3.3 / §7）。

### 4.3 删除与清理

| 项 | 约定 |
|----|------|
| 业务删除 | **逻辑删除**（元数据 `deleted=1`）；**不**另设 `status` 与逻辑删除双轨 |
| 物理对象 | 默认保留；清理走**运维脚本**（按 `deleted=1` + 时间窗口，见 **§4.5**） |
| 定时清理 | **禁止** Quartz/XXL-Job/ShedLock 等（`CLAUDE.md` §2.2） |
| 服务商停用/删除 | 停用后禁止新上传，存量可读/可逻辑删；**删除服务商配置**仅当无 `deleted=0` 文件引用，或强制为「停用 + 禁止新上传」 |
| 误删 | 逻辑删除可从库恢复元数据；物理对象是否可恢复取决于运维脚本策略与备份 |

### 4.4 失败与幂等

- 网络/5xx 可按 `CLAUDE.md` §6.11 重试策略有限重试；4xx（类型/大小非法、签名配置错误）不重试。
- 上传成功但元数据落库失败：记 error 日志（含 providerCode/objectKey），便于运维脚本补偿；禁止静默丢对象。
- 重复业务删除：走 `@Idempotent`，返回首次 `Result`。
- 服务商连通性测试失败：返回业务/校验错误并带可读原因；不得当作上传成功。

### 4.5 运维脚本契约（清理与补偿）

> 因**禁止分布式定时**（`CLAUDE.md` §2.2），对象物理清理与孤儿补偿一律走**人工触发的运维脚本**（或临时一次性任务），不建常驻调度。脚本语言/形态不限（Shell/Python/Java CLI），但必须满足下列契约。多服务商下脚本须**按 `storage_code` 分组**处理。

#### 4.5.1 物理对象清理（`deleted=1` → `remove`）

| 项 | 约定 |
|----|------|
| 清理条件 | `deleted=1` **且** `update_time` 早于保留窗口（默认建议 **≥ 7 天**，可按系统登记） |
| 执行顺序 | 先读库筛目标 → 按 `storage_code` 解析服务商 → `exists` 探测 → `remove` 物理删除 |
| 幂等 | 已不存在的对象**跳过**并记 warn；脚本可重复执行 |
| 误跑保护 | 必须支持 **dry-run**（只打印目标、不删）；正式执行前由操作人确认目标数量级 |
| 失败处理 | 单对象删除失败继续后续对象，汇总失败清单（`id` + `storage_code` + `objectKey` + 原因）；**禁止**静默吞错 |
| 输出 | 记录：窗口、命中数、跳过数、成功数、失败清单；不含 AK/SK 与完整预签名 URL |
| 回滚 | 物理删除**不可逆**；清理前应确认备份策略（库可恢复元数据，对象依赖存储快照/备份） |

#### 4.5.2 孤儿对象补偿（对象在、库无）

**产生原因：** 上传成功但元数据落库失败（见 §4.4）。

| 项 | 约定 |
|----|------|
| 发现 | ① 按 error 日志中的 `objectKey` 人工/脚本回收；② 或对比「桶内对象列表」与 `sys_file`（含 `deleted`）求差集 |
| 处理二选一 | **补落元数据**（若可关联原业务）或 **物理 `remove`**（无法归属时） |
| 保护 | 清理孤儿前保留观察窗口（建议 ≥ 24h）；疑似在途上传的对象不得立即删 |
| 禁止 | 未比对库直接按桶列表批量删除；禁止删除 `deleted=0` 仍被引用的对象 |

#### 4.5.3 与业务删除的边界

- 业务接口**只**做逻辑删除（`deleted=1`），**不**调用 `remove`（`remove` 仅运维/补偿路径）。
- 运维脚本**不**改业务语义（不把 `deleted=0` 改成 1），只处理物理对象与孤儿。
- 脚本部署与执行方式随系统运维文档登记；本文只定契约，不绑定具体命令。

---

## 5. 数据模型（通用形态）

> 各系统在**本库**建表；字段语义对齐下表。完整 SQL 随 openspec 变更提供（`CLAUDE.md` §6.5 / §2.2 不引 Flyway）。

**列约定：** 主键 `id` BIGINT，16 位发号，出参 String；审计 5 字段（`create_time` / `update_time` / `create_by` / `update_by` / `deleted`）；字符串 `NOT NULL` + 默认值；索引 `uk_/idx_表名_字段`，单表 ≤ 5；禁止物理外键。

### 5.1 `sys_file`（或等价）— 文件元数据

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|:--:|------|------|
| id | BIGINT | N | — | 主键，16 位发号 |
| storage_code | VARCHAR(64) | N | `''` | **服务商编码**（指向 `sys_storage_provider.code`）；回源用 |
| bucket | VARCHAR(64) | N | `''` | 桶名 |
| object_key | VARCHAR(255) | N | — | 对象键（**UUID 文件名**） |
| original_name | VARCHAR(255) | N | `''` | 原始文件名（展示用） |
| content_type | VARCHAR(128) | N | `''` | MIME（白名单校验） |
| size_bytes | BIGINT | N | 0 | 字节大小 |
| create_time / update_time | DATETIME | N | | 审计 |
| create_by / update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | 0 未删 / 1 已删（**删除语义**） |

- **唯一**：`uk_表名_object_key (object_key)`（若多服务商可能撞键，可扩为 `(storage_code, object_key)`；采用前须在变更登记）
- **索引**：`idx_表名_create_time`；`idx_表名_storage_code`（多服务商筛选/回源）
- 业务附加列（如 blog `biz_type`/`biz_id`）由各系统增量裁剪，不破坏本表主契约。

### 5.2 `sys_storage_provider` — 对象存储服务商配置

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|:--:|------|------|
| id | BIGINT | N | — | 主键，16 位发号 |
| code | VARCHAR(64) | N | — | 服务商编码，系统内唯一 |
| name | VARCHAR(128) | N | `''` | 展示名 |
| provider_type | VARCHAR(32) | N | — | `MINIO` / `AWS_S3` / `ALIYUN_OSS` / `TENCENT_COS` / `HUAWEI_OBS` / `CUSTOM_S3` |
| endpoint | VARCHAR(255) | N | `''` | S3 endpoint |
| region | VARCHAR(64) | N | `''` | 区域；类型要求必填时校验 |
| bucket | VARCHAR(64) | N | `''` | 默认桶 |
| access_key | VARCHAR(128) | N | `''` | AK |
| secret_key | VARCHAR(255) | N | `''` | SK，**密文存储** |
| path_style | TINYINT | N | 1 | 1=path-style / 0=virtual-hosted |
| status | TINYINT | N | 1 | 0 停用 / 1 启用（**控制新上传**） |
| is_default | TINYINT | N | 0 | 1=默认服务商（同库仅一条为 1） |
| description | VARCHAR(255) | N | `''` | 备注 |
| create_time / update_time | DATETIME | N | | 审计 |
| create_by / update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | 0 未删 / 1 已删 |

- **唯一**：`uk_sys_storage_provider_code (code)`
- **索引**：`idx_sys_storage_provider_status (status)`（可选）

**逻辑关系：**

```text
sys_storage_provider 1 ──── * sys_file.storage_code（逻辑关联，无物理外键）
（用户中心）sys_user.id ──（逻辑）──► create_by / update_by
sys_file 1 ──── * 审计日志（target_type=FILE / STORAGE_PROVIDER）
```

### 5.3 出参契约（脱敏）

| 对象 | 出参 |
|------|------|
| 文件 VO | `id`（String）、`storageCode`、`bucket`、`objectKey`、`originalName`、`contentType`、`sizeBytes`、`createTime`、`createBy`（显示名） |
| 服务商 VO | `id`（String）、`code`、`name`、`providerType`、`endpoint`、`region`、`bucket`、`pathStyle`、`status`、`isDefault`、`description`、`createTime`、`updateTime`、**`secretKeyMasked`（如 `****` 或尾 4 位策略，禁止全文）** |
| 预签名 | `{ url, expireAt }`（§4.2） |

- 创建/更新服务商时 `secretKey` 为**只写**字段：传空表示不修改；非空则加密存储。
- **禁止**任何接口返回明文 SK；禁止 SK 进审计 `detail` 与应用日志。

---

## 6. 配置契约

### 6.1 分层配置

| 层 | 载体 | 职责 |
|----|------|------|
| 引导/本地默认 | `application.yml` / `application-local.yml` / `application-test.yml` | 无密钥公共项 + local/test 连接；**生产敏感项不入仓** |
| 运行时服务商 | **DB `sys_storage_provider`**（管理端配置页） | 多服务商 CRUD、默认项、启停 |
| 生产密钥 | Nacos / 密钥管理 / Jenkins Credentials | 引导密钥或 SK 加密主密钥 |

- 三份 yml 仍遵守 `CLAUDE.md` §6.10：公共 `application.yml` **无**主机/账号密码。
- 连接信息真相源（测试环境）：`docs/test-env.md`；端口：`docs/port-registry.md`。
- 配置解析：DB 配置优先于 yml 引导默认；yml 用于冷启动/无库开发。

### 6.2 yml 引导键（结构示意，禁止写入生产密钥）

```yaml
# 结构示意；endpoint/账密不得写入公共 application.yml
{system}:                  # 或统一 object-storage
  object-storage:
    # 引导默认服务商（可选；运行时以 sys_storage_provider 为准）
    default-provider: minio-local
    providers:
      - code: minio-local
        provider-type: MINIO
        endpoint: ""
        region: ""
        bucket: ""
        access-key: ""
        secret-key: ""
        path-style: true
    upload-max-bytes: 10485760
    allowed-content-types:
      - image/png
      - image/jpeg
      - image/gif
      - application/pdf
    presign-ttl: 10m
    connect-timeout: 5s
    read-timeout: 30s
    secret-crypto:
      # SK 落库加密主密钥来源：环境变量 / Nacos；禁止明文入仓
      key-source: env
      key-env: OBJECT_STORAGE_SK_KEY
```

| 键 | 要求 |
|----|------|
| `allowed-content-types` | 白名单非空；禁止空数组表示全放行 |
| `upload-max-bytes` | 默认 ≤ 10MB（单文件）；调整须走变更 |
| `presign-ttl` | 短 TTL；禁止无上限 |
| providers[].endpoint / AK / SK | 仅 local/test 或 Nacos；**生产禁止入仓** |
| `secret-crypto` | SK 加密算法与主密钥来源必须显式；主密钥本身禁止入仓 |

---

## 7. 管理端前端设计

> 视觉与布局遵循 `admin-ui-design.md`（云白、Element Plus、P-List / D-Form）；权限标识格式 `system:resource:action`（`CLAUDE.md` §5.2）。

### 7.1 信息架构

| 页面 | 路由（示例） | 职责 | 权限（示例） |
|------|--------------|------|----------------|
| **文件管理（展示页）** | `/files` → `views/file/FileList.vue` | 上传、列表、筛选、预览/下载、逻辑删除 | `{system}:file:list/upload/read/delete` |
| **对象存储服务商配置** | `/storage/providers` → `views/storage/StorageProviderList.vue` | 服务商 CRUD、连通性测试、启停、设默认 | `{system}:storage-provider:list/create/update/delete/test` |

- 菜单由后端 RBAC 下发；前端**禁止**硬编码角色。
- 两页同属「存储」或「系统管理」目录，按系统菜单树裁剪。

### 7.2 文件管理展示页（重设计）

在 v1 列表页基础上增强「服务商」维度，布局仍为 P-List：

1. **页头**：标题「文件管理」+「上传」（`file:upload`）；可选右侧链接「存储服务商配置」
2. **统计条**：有效文件数、当前默认服务商、策略摘要（单文件 ≤10MB · 白名单 · 预签名 TTL）
3. **筛选栏**：
   - 原始文件名关键字
   - **服务商**（下拉：全部 / 各 `sys_storage_provider`）
   - Content-Type（白名单子集）
   - 上传时间范围（可选）
   - 「查询 / 重置」
4. **列表**：

| 列 | 说明 |
|----|------|
| 文件名 | `originalName`；悬浮/次行显示 `objectKey` |
| 服务商 / 桶 | `storageCode` + `bucket`（便于多服务商排查） |
| 类型 | `contentType` |
| 大小 | `sizeBytes` 友好展示 |
| 上传人 / 时间 | `createBy` 显示名 + `createTime` |
| 操作 | 预览 / 下载（预签名）、删除（`file:delete`） |

5. **分页条**：`current`/`size`，上限对齐后端。

**上传交互：**

- 「上传」对话框：单文件；前端即时校验大小/MIME（与后端一致，后端仍强校验）
- 可选「目标服务商」下拉（默认系统默认服务商；仅启用中的服务商）
- 提交 `POST /api/v1/files`（multipart，可带 `storageCode`），建议 `Idempotent-Key`；成功刷新列表
- 失败展示 `msg`（超限/类型非法/429）；不做多文件队列

**预览 / 下载：** `GET /files/{id}/url` → `{url, expireAt}`，新窗口打开或下载；过期重签；完整 URL 不进前端日志。

**删除：** 确认框强调**逻辑删除**；`DELETE /files/{id}` + `@Idempotent`；列表默认不含 `deleted=1`。

### 7.3 对象存储服务商配置页（新增）

布局：**P-List + D-Form**（新建/编辑用抽屉或对话框）。

1. **页头**：标题「存储服务商」+「新建服务商」（`storage-provider:create`）
2. **筛选栏**：关键字（code/名称）、`provider_type`、状态（启用/停用）、「查询 / 重置」
3. **列表**：

| 列 | 说明 |
|----|------|
| 编码 / 名称 | `code` / `name` |
| 类型 | `provider_type` 标签（MINIO / AWS_S3 / …） |
| endpoint / 桶 | 可截断悬浮全文 |
| region / path-style | 连接参数摘要 |
| 状态 | 启用/停用胶囊；**默认**标记 |
| 密钥 | `secretKeyMasked`（只读展示） |
| 操作 | 测试连通 / 编辑 / 设为默认 / 启用停用 / 删除 |

4. **表单字段（D-Form）**：

| 字段 | 控件 | 校验 |
|------|------|------|
| code | 输入框 | 必填、唯一、格式建议小写连字符 |
| name | 输入框 | 必填 |
| provider_type | 下拉 | 必填 |
| endpoint | 输入框 | 必填，http(s) |
| region | 输入框 | 按类型必填 |
| bucket | 输入框 | 必填 |
| access_key | 输入框 | 必填 |
| secret_key | 密码框 | 新建必填；编辑留空=不修改 |
| path_style | 开关/单选 | 必填；类型可给默认 |
| status | 开关 | 启用/停用 |
| is_default | 开关 | 设默认时提示将影响**新上传** |
| description | 多行文本 | 可选 |

5. **测试连通**：按钮触发 `POST /storage/providers/{id}/test`；结果 toast（成功/失败原因）；写审计。
6. **删除/停用规则**（前端提示与后端一致）：
   - 停用：立即禁止新上传；存量可读
   - 删除：无 `deleted=0` 文件引用才允许；否则提示改停用
   - 设为默认：确认文案说明「仅影响新上传，历史文件仍回源原服务商」

### 7.4 前端工程约定

- 请求只经 `src/api`（`file.ts` / `storageProvider.ts`）；`id` 一律 `string`；axios 统一解包 `{code,msg,data}`，按 `CLAUDE.md` §5.3 分流
- 表单提交可带 `Idempotent-Key`；按钮权限走 `hasPermission`
- 禁止把 SK 明文写入 localStorage/控制台；编辑回显只用掩码

---

## 8. 管理 API（形态约定）

> 路径版本前缀 `/api/v1`；RESTful；方法名 `getXxx / listXxx / createXxx / updateXxx / deleteXxx`。权限码软校验/按钮显隐，网关鉴权。

### 8.1 文件

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/files` | `{system}:file:upload` | multipart 上传；可选 `bizType`/`bizId`/`storageCode` |
| GET | `/files` | `{system}:file:list` | 分页；筛选 keyword/contentType/storageCode/bizType |
| GET | `/files/{id}` | `{system}:file:read` | 元数据详情 |
| GET | `/files/{id}/url` | `{system}:file:read` | 预签名 `{url, expireAt}` |
| DELETE | `/files/{id}` | `{system}:file:delete` | 逻辑删除；`@Idempotent` |

### 8.2 存储服务商

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/storage/providers` | `{system}:storage-provider:list` | 分页列表（SK 掩码） |
| GET | `/storage/providers/{id}` | `{system}:storage-provider:list` | 详情（SK 掩码） |
| POST | `/storage/providers` | `{system}:storage-provider:create` | 新建 |
| PUT | `/storage/providers/{id}` | `{system}:storage-provider:update` | 更新；`secretKey` 空=不改 |
| POST | `/storage/providers/{id}/test` | `{system}:storage-provider:test` | 连通性测试 |
| POST | `/storage/providers/{id}/default` | `{system}:storage-provider:update` | 设为默认 |
| PUT | `/storage/providers/{id}/status` | `{system}:storage-provider:update` | 启用/停用 |
| DELETE | `/storage/providers/{id}` | `{system}:storage-provider:delete` | 逻辑删除；无活跃文件引用 |

**业务/校验失败**：HTTP 200 + `2xxxxx`/`3xxxx`；**限流**：HTTP 429 + 系统段「请求过于频繁」；**未登录** 401 / **无权限** 403（见 `CLAUDE.md` §5.3）。

---

## 9. 安全约定

- **密钥分级**（唯一权威 `CLAUDE.md` §6.7）：生产 AK/SK/连接串禁止入仓；测试/开发账密仅 `docs/test-env.md` / `application-local|test.yml`，须标注环境。
- **SK 存储**：DB 落**密文**（AES-GCM 或同级）；主密钥走环境变量/Nacos，禁止入仓；接口只收明文、只回掩码。
- **上传安全**：白名单 + 大小上限双校验；对象名 UUID，防路径穿越与覆盖；`Content-Type` 与白名单一致。
- **访问安全**：预签名短 TTL；不把预签名完整 URL 打进 info 日志；泄露后可依赖 TTL 自然过期 + 对象逻辑删除。
- **权限**：上传/删除/预签名/服务商配置接口仍走网关登录态 + 权限码软校验；预签名不替代业务鉴权。
- **审计**：上传/删除文件、服务商增删改/设默认/测连通记审计；`detail` 禁密码、完整令牌、Secret。
- **网关/Nginx**：不配置对象存储代理改写大包 body；CORS 不在此放行对象存储域名上的任意跨域写（若浏览器直传，需单独收敛）。
- **错误信息**：连通性/签名失败返回可读原因，禁止回显 AK/SK 或完整 endpoint 带凭证 query。

---

## 10. 部署、自检与验收

### 10.1 部署要点

1. 准备任一 S3 兼容存储：建桶、网络策略仅内网/必要出口；测试账密入 `docs/test-env.md` 或 Nacos。
2. 冷启动：yml 引导默认服务商或先在配置页建 `sys_storage_provider`；配置 SK 加密主密钥来源。
3. 服务健康检查不依赖对象存储强可用（避免启动即失败；客户端可懒加载）。
4. 联调：上传 → 列表（按服务商筛选）→ 预签名访问 → 逻辑删除 → 配置页测连通/切默认（确认仅影响新上传）。
5. 运维侧准备**清理/补偿脚本**（§4.5）：按 `storage_code` 分组、dry-run、保留窗口、失败清单。

### 10.2 提交前自检

```bash
cd backend/<system> && mvn -q compile
# 管理端文件页 / 服务商配置页
cd frontend/<system> && npm run lint && npm run type-check
```

- [ ] S3/MinIO SDK 只出现在 `{system}-object-storage`（或等价唯一封装模块）
- [ ] 版本与 `docs/version-baseline.md` 一致（SDK 坐标已登记）
- [ ] 仅一套 SDK 底座（AWS S3 SDK v2 或 MinIO SDK），无双栈
- [ ] `allowed-content-types` 非空；`upload-max-bytes` / `presign-ttl` 已配
- [ ] `sys_storage_provider.secret_key` 密文存储；出参掩码；无生产 AK/SK 入仓
- [ ] 无完整预签名 URL / Secret 进日志
- [ ] 业务删除为逻辑删除；未引入分布式定时清理
- [ ] 大文件未走网关/Nginx 中转 body；下载未由业务服务反向代理
- [ ] 预签名响应 `expireAt` 为 epoch 毫秒（number）
- [ ] 存量文件按 `storage_code` 回源；切默认不影响历史
- [ ] 运维清理/补偿脚本具备 dry-run 与失败清单（§4.5）
- [ ] 文件页与服务商配置页权限码已进 RBAC 菜单/按钮

### 10.3 验收清单

- [ ] 上传合法文件成功且元数据完整（含 UUID objectKey + storageCode）
- [ ] 超限 / 非白名单 MIME 被拒绝
- [ ] 预签名 URL 在 TTL 内可访问，过期后不可用；响应含 `expireAt`（epoch 毫秒）
- [ ] 多服务商：可配置 ≥2 家（或 MinIO + CUSTOM_S3）并分别上传/下载成功
- [ ] 服务商测试连通成功/失败原因可读；失败配置无法用于新上传
- [ ] 设为默认后**新上传**走新默认，**历史文件**预签名仍指向原服务商
- [ ] 逻辑删除后列表不可见；物理清理策略有运维说明（§4.5）
- [ ] 重复删除/重复提交（幂等键）返回首次 `Result`
- [ ] 限流触发返回 HTTP 429 + 系统段「请求过于频繁」
- [ ] 服务商接口不返回明文 SK；审计无 Secret

---

## 11. 与系统设计文档的关系

| 文档 | 职责 | 不要放什么 |
|------|------|------------|
| **本文件** | 对象存储通用策略、独立模块、S3 多服务商、表形态、配置、管理端双页、安全与验收 | 具体系统权限码全表、生产密钥、端口 |
| 各系统 `*-design.md` | 只写**裁剪取值**（默认服务商 code、桶、白名单差异、接口权限码、是否暴露对内 Feign） | 复制 SDK 封装细节、复制本表字段全表 |
| `CLAUDE.md` §6.11 | 上传总约束（大小/批量/UUID/逻辑删除） | 实现代码 |
| `docs/version-baseline.md` | SDK 版本唯一登记 | 在设计正文复制版本号 |
| `gateway-design.md` | 预签名与不经中转 body 的边缘约束 | 对象存储业务 API |
| `admin-ui-design.md` | 视觉令牌与 P-List/D-Form 壳 | 存储业务字段语义 |

**系统设计引用示例：**  
「对象存储按 `object-storage-design.md` 裁剪；独立模块名 = `{system}-object-storage`；默认服务商 code = `{code}`；MIME 白名单 / 预签名 TTL 若与默认不同，在此登记差异。」

---

## 12. 相关文档

| 文档 | 内容 |
|------|------|
| `CLAUDE.md` | 项目全局契约（§2 / §2.1 / §5.4 / §6.7 / §6.11） |
| `docs/version-baseline.md` | S3/MinIO SDK 等组件版本 |
| `docs/test-env.md` | 测试环境对象存储连接与测试账密 |
| `docs/port-registry.md` | 对象存储端口登记 |
| `gateway-design.md` | 大文件预签名、不经网关中转 |
| `admin-ui-design.md` | 管理端视觉与页面骨架 |
| `example-system-design.md` | 系统内文件能力演示与 API 形态参照 |
| `docs/template/fixbug/2026-10-19-minio-signature-object-storage.md` | MinIO `SignatureDoesNotMatch` 排查与 region/配置对齐 |
| `rbac-design.md` | 文件/服务商接口权限标识如何进菜单/按钮 |
| **本目录 `prototype.html` / `assets/proto-*.svg`** | 管理端双页设计原型（展示页 + 服务商配置页） |
