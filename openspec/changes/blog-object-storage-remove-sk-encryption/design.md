# 设计：移除对象存储 SK 加密层

> 决策记录格式（D1–D7）。本变更**仅删除**已落地的加密层，不引入新机制；删除清单即生产重引入清单。

## D1：删除边界——只删加密层，不动客户端与 VO 契约

**决策**：删除范围严格限定在 `SecretKeyCipher` / `SecretCrypto` / `secretKeyCipher` Bean / `application.yml.secret-crypto` / `StorageProviderServiceImpl.encryptSafely` / `SysStorageProviderConfigSupplier.toConfig` 解密调用；`ObjectStorageClient` / `ProviderClientRegistry` / `StorageProviderVO` / 前端 `StorageProviderList.vue` / `storageProvider.ts` **零改动**。

**理由**：
- 加密层涉及**两条路径**：
  1. **写路径**——`StorageProviderServiceImpl.create/update` 经 `encryptSafely` 加密 SK 落库；删除 `encryptSafely` 改直接 `setSecretKey`。
  2. **读路径**——`SysStorageProviderConfigSupplier.toConfig`（DB → `ProviderConfig`）经 `cipher.decrypt` 解密 DB 密文 SK 还原明文供客户端使用；删除 `cipher` 改直接 `cfg.setSecretKey(entity.getSecretKey())`。
- `StorageProviderServiceImpl.toVO` 走 `maskSecretKey` 掩码，与加密层无关，不动。
- 验证：grep `cipher\.` 命中点 = `StorageProviderServiceImpl.encryptSafely`（写） + `SysStorageProviderConfigSupplier.toConfig`（读）；两者都需清理。
- 前端 VO `secretKeyMasked` 字段语义不变，前端无需联调。

**反例**：若一并删除 `maskSecretKey` 让 VO 直接回明文 SK——**否决**。出参掩码是基础安全约定（§9），与"是否加密落库"正交。

**注**：初版 design.md 仅列出写路径，漏列读路径 `SysStorageProviderConfigSupplier`；apply 阶段 grep `SecretKeyCipher` 发现该类也注入 `cipher`，已补充清理。

## D2：设计文档同步修改点（精确到行）

| 文件 / 章节 | 改动 |
|-------------|------|
| `object-storage-design.md` §3.2.3 表（line ~142） | `accessKey` / `secretKey` 行说明从"凭证；SK 加密存储"→"凭证；SK 直接存储（dev/test 阶段；生产另开加密变更），出参掩码" |
| `object-storage-design.md` §5.2 表（line ~438） | `secret_key` 行说明"SK，**密文存储**"→"SK，**明文存储**（dev/test 阶段；生产另开加密变更）" |
| `object-storage-design.md` §5.3（line ~467） | "传空表示不修改；非空则加密存储"→"传空表示不修改；非空则直接存储" |
| `object-storage-design.md` §6.1 表（line ~480） | "生产密钥"行职责"引导密钥或 SK 加密主密钥"→"引导密钥"（删除"或 SK 加密主密钥"） |
| `object-storage-design.md` §6.2（line ~512-515） | 删除 `secret-crypto:` 配置块（4 行） |
| `object-storage-design.md` §6.2 表（line ~524） | 删除 `secret-crypto` 行 |
| `object-storage-design.md` §9（line ~667） | "SK 存储：DB 落**密文**（AES-GCM 或同级）；主密钥走环境变量/Nacos..."→"SK 存储：DB 落**明文**（dev/test 阶段；生产部署前须经独立加密变更重引入）；接口只收明文、只回掩码" |
| `object-storage-design.md` §10.1（line ~682） | 删除"配置 SK 加密主密钥来源"短句 |
| `object-storage-design.md` §10.2（line ~699） | "`sys_storage_provider.secret_key` 密文存储"→"`sys_storage_provider.secret_key` 明文存储（dev/test；生产另开加密变更）" |
| `docs/test-env.md` §2.4（line ~95-102） | 删除"SK 加密主密钥（blog 对象存储多服务商）"整行表项 |
| `docs/object-storage-design/README.md` | 若目录索引提及加密则同步（待 grep 确认） |

## D3：枚举值保留策略——`MASTER_KEY_MISSING` / `SECRET_DECRYPT_FAILED` 不删

**决策**：`ObjectStorageException.Reason` 中的 `MASTER_KEY_MISSING` 与 `SECRET_DECRYPT_FAILED` **保留**枚举值，运行时不再产生，但枚举不收缩。

**理由**：
- 删枚举值会破坏枚举序（若有 ORM/Jackson 序列化持久化、或错误码翻译表硬编码引用序号），引入隐性 bug。
- 保留枚举值不产生运行时开销；`StorageProviderServiceImpl.encryptSafely` 删除后，`MASTER_KEY_MISSING` 路径不再可达。
- 生产重引入加密层时，枚举值原地复用，零迁移成本。

**反例**：若同步删除 `BlogErrorCodeEnum.PROVIDER_CONFIG_INVALID` 中"SK 主密钥未配置"翻译分支——**否决**。错误码翻译表是字符串匹配，未命中分支不报错；保留分支便于生产重引入时直接复用。

## D4：DB 字段语义变更——`secret_key` 从密文→明文，零迁移

**决策**：`sys_storage_provider.secret_key` 列定义（VARCHAR(255) NOT NULL DEFAULT ''）保持不变，仅语义从"Base64(IV‖密文)"→"明文 SK"。种子 SQL `2026-10-20-blog-storage-provider.sql` 中 `secret_key=''` 空串占位，明文/密文语义下都合规，**无需迁移**。

**理由**：
- 现网 dev/test 库中该列要么是空串（种子占位），要么是用户从未成功保存过的初始状态（因加密失败一直未落库）；不存在"已加密密文需解密回明文"的迁移场景。
- 若历史 DB 中确实存在密文（理论上不存在，但若曾手工注入过密钥并保存成功），运维侧需手工 `UPDATE sys_storage_provider SET secret_key='' WHERE secret_key<>'';` 重置——本提案在 tasks.md §3 注明此运维步骤，但不在代码中自动迁移。

**反例**：在代码层做"启动时探测密文格式并自动解密回明文"——**否决**。自动迁移需主密钥，回到原问题；且 dev/test 阶段直接重置更简单。

## D5：`ObjectStorageAutoConfiguration` 处理——保留类壳 + `@EnableConfigurationProperties`，仅删 `secretKeyCipher` Bean

**决策**：`ObjectStorageAutoConfiguration` 当前仅含 `secretKeyCipher` 一个 Bean 方法，但类上的 `@EnableConfigurationProperties(ObjectStorageProperties.class)` 注解**必须保留**——因为 `ObjectStorageProperties` 类上只有 `@ConfigurationProperties` 没有 `@Component`，靠 `@EnableConfigurationProperties` 注册为 Spring bean；而 `YmlStorageProviderSupplier`（`@Component`）构造器注入 `ObjectStorageProperties`，一旦该注解失效，properties bean 不存在，启动报 `NoSuchBeanDefinitionException`。

**实施**：仅删除 `secretKeyCipher` Bean 方法 + 相关 import（`SecretKeyCipher`、`ConditionalOnMissingBean`），保留类壳、`@Configuration`、`@EnableConfigurationProperties(ObjectStorageProperties.class)`。

**验证**：grep 确认 `ObjectStorageProperties` 在 `YmlStorageProviderSupplier` 中被注入（line 16/19）；`@EnableConfigurationProperties(ObjectStorageProperties.class)` 在 `ObjectStorageAutoConfiguration` 类上保留；`mvn -q compile` 通过 + 启动 `actuator/health` UP。

**反例**：整个类删除——**否决**。会导致 `YmlStorageProviderSupplier` 注入失败，启动报错。

**补救**：若未来 `ObjectStorageAutoConfiguration` 完全空壳无意义，可将 `@EnableConfigurationProperties(ObjectStorageProperties.class)` 迁到 `YmlStorageProviderSupplier` 所在配置类或新建 `ObjectStoragePropertiesConfig`——但当前最小改动原则下保留类壳即可。

## D6：`StorageProviderServiceImpl` 改动最小化

**决策**：仅做以下三处改动，不重构其他方法：

1. 删除字段 `private final SecretKeyCipher cipher;`
2. 删除方法 `private String encryptSafely(String plaintext) {...}`
3. `create()` line 110 `entity.setSecretKey(encryptSafely(request.getSecretKey()))` → `entity.setSecretKey(request.getSecretKey())`
4. `update()` line 141 `entity.setSecretKey(encryptSafely(request.getSecretKey()))` → `entity.setSecretKey(request.getSecretKey())`
5. 删除 `import com.qjj.blog.objectstorage.SecretKeyCipher;` 与 `import com.qjj.blog.objectstorage.ObjectStorageException;`（若其他方法不再用）

**理由**：最小改动 = 最小回归面。`ObjectStorageException` 在 `test()` 方法中仍被使用（line 167-172），故 `import` 保留。

**自检**：删除后 `mvn -q compile` 必须通过；`StorageProviderServiceImpl` 中不应再出现 `cipher`、`encryptSafely`、`MASTER_KEY_MISSING` 字样（grep 验证）。

## D7：生产重引入锚点——本提案 = 回滚清单

**决策**：本提案的删除清单（proposal.md "移除范围"表 + design.md D1–D6）作为**生产环境重引入 SK 加密层时的回滚锚点**。重引入步骤 = 反向执行本提案 tasks.md。

**理由**：
- 删除范围精确到文件级，可逆向恢复。
- 生产加密方案不应照搬原环境变量方案（`docs/test-env.md` §2.4 已暴露痛点）；推荐 KMS/密钥管理服务托管主密钥，但具体方案另开变更评估。
- 本提案在 `proposal.md` "Impact" 节明确标注"生产阶段禁止直接套用"。

**不做**：不在本提案中评估生产加密方案选型（KMS / JASYPT / 配置中心加密 / 应用层 AES+密钥轮转），避免范围蔓延。
