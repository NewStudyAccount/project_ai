# 提案：移除对象存储 SK 加密层（dev/test 阶段直存明文）

## Why（动机）

对象存储服务商 SecretKey（SK）落库加密设计（`object-storage-design.md` §9 / §6.2 `secret-crypto`）要求后端启动环境注入环境变量 `OBJECT_STORAGE_SK_KEY` 作为 AES-GCM 主密钥，且主密钥本身禁止入仓（`CLAUDE.md` §6.7）。

实际运行中暴露的痛点：

- 后端通过 IDEA Run Config 启动，未注入该环境变量；管理端「存储服务商配置页」保存修改时，`SecretKeyCipher` 懒加载触发主密钥解析，环境变量缺失即抛 `MASTER_KEY_MISSING`，业务层翻译为 `PROVIDER_CONFIG_INVALID · "SK 主密钥未配置：环境变量 OBJECT_STORAGE_SK_KEY 未设置..."`，**保存动作直接失败**。
- 启动期间懒加载只解决了"启动读 DB 不报错"——种子 SQL `sys_storage_provider.secret_key=''` 空串占位不触发解密；但**写路径（创建/更新服务商）必须加密 SK 入库，必须有主密钥**，懒加载对此无法绕开。
- 当前阶段**未上生产**，`docs/test-env.md` §6.1 明确"测试/开发/本地账号密码：允许写入本仓库"——dev/test 库的 SK 本身就可明文入库，加密层对当前阶段无收益反而阻碍联调。

## What（变更内容）

**移除** `blog-object-storage` 模块的 SK 加密层，SK 直接以明文落库；出参仍走掩码（`secretKeyMasked = "****"`）；前端「密码框只写」语义保持不变。

### 移除范围

| 资产 | 动作 |
|------|------|
| `SecretKeyCipher.java` | **删除** |
| `ObjectStorageProperties.SecretCrypto` 内嵌类 | **删除** |
| `ObjectStorageAutoConfiguration.secretKeyCipher` Bean | **删除**（若该类仅此一 Bean 则整个类删除） |
| `ObjectStorageException.Reason.MASTER_KEY_MISSING` | **保留枚举值**（避免破坏枚举序；运行时不再产生） |
| `ObjectStorageException.Reason.SECRET_DECRYPT_FAILED` | 同上，保留 |
| `StorageProviderServiceImpl` | 删除 `cipher` 字段、`encryptSafely()`、`import SecretKeyCipher`；`create/update` 中 `entity.setSecretKey(...)` 直接赋值明文 |
| `application.yml` | 删除 `blog.object-storage.secret-crypto` 配置块 |
| `application-local.yml` / `application-test.yml` | 若有同键则一并清理 |
| `docs/object-storage-design/object-storage-design.md` | §3.2.3 / §5.2 / §5.3 / §6.1 / §6.2 / §9 / §10.1 / §10.2 同步修改（详见 design.md D2） |
| `docs/test-env.md` | §2.4「SK 加密主密钥」行删除 |
| `docs/object-storage-design/README.md` | 若提及加密则同步 |
| 种子 SQL `2026-10-20-blog-storage-provider.sql` | **无需迁移**（`secret_key=''` 空串占位，明文/密文语义下都合规） |

### Capabilities（能力影响）

- **ADDED**：管理端保存服务商配置不再依赖环境变量；dev/test 阶段无需任何启动前置配置即可 CRUD 服务商。
- **REMOVED**：SK 落库加密能力（AES-GCM 主密钥体系）；dev/test DB 中 `sys_storage_provider.secret_key` 字段存明文 SK。
- **UNCHANGED**：出参仍走 `secretKeyMasked` 掩码；前端 SK 密码框 `autocomplete=new-password` 只写语义；审计 `detail` 禁含 SK；接口不返回明文 SK。
- **UNCHANGED**：上传/下载/删除/连通性测试/设默认/启停/引用检查等其余能力完全不变。

## Impact（影响与边界）

### 受影响系统

- `blog-service`：删除 `SecretKeyCipher` Bean 依赖；`StorageProviderServiceImpl` 简化；启动不再读环境变量。
- `blog-object-storage` 模块：瘦身，仅保留 S3 客户端封装与服务商配置解析。
- 管理端前端 `blog-admin`：**零改动**（VO 字段不变，掩码语义不变）。
- DB schema：**零迁移**（`secret_key` 列类型/长度不变，仅语义从密文→明文）。

### 安全影响（必须在提案中明示）

- **dev/test 阶段**：库被拖库 = SK 泄露。但 dev/test 账密本就允许入仓（`docs/test-env.md` §6.1），泄露影响面有限。
- **生产阶段**：**禁止**直接套用本提案落地生产。生产部署前必须经独立加密变更重新引入 SK 加密层（推荐方案：KMS/密钥管理服务托管主密钥，而非环境变量）。
- 本提案 **保留为生产加密层重引入的回滚锚点**：删除范围清单即重引入清单。

### Non-goals（明确不做）

- 不引入新的加密方案（KMS、JASYPT、配置中心加密等）——生产加密方案另开变更评估。
- 不修改前端 `StorageProviderList.vue` / `FileList.vue` / `storageProvider.ts` / `types/index.ts`（VO 契约不变）。
- 不修改 RBAC 菜单/权限码（已登记的 `blog:storage-provider:*` 不变）。
- 不修改 `ObjectStorageException` 其他 `Reason` 枚举值（避免破坏其他错误路径翻译）。
- 不修改 `sys_storage_provider` 表结构与种子 SQL。
- 不修改 `ProviderClientRegistry` / `ObjectStorageClient` 等运行时客户端逻辑（它们读 SK 走 `StorageProviderServiceImpl.toVO` 之外的内部解析路径，与加密层解耦）。

## 关联

- 上游设计：`docs/object-storage-design/object-storage-design.md`（§3.2.3 / §5.2 / §5.3 / §6 / §9 / §10）
- 上游变更（已落地）：`blog-object-storage-multi-provider`、`blog-object-storage-public-url`、`blog-object-storage-frontend`
- 安全铁律：`CLAUDE.md` §6.7（密钥分级）、§7.9（不凭猜测改代码）
- 测试环境登记：`docs/test-env.md` §2.4 / §6.1
