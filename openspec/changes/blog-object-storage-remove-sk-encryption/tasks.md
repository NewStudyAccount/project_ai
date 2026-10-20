# Tasks: 移除对象存储 SK 加密层

> 实施顺序：§1 后端代码 → §2 配置文件 → §3 运维 → §4 设计文档 → §5 自检。每个任务项实施后勾选 `[x]`。

## 1. 后端代码改动（blog-object-storage + blog-service）

- [x] 1.1 删除 `backend/blog/blog-object-storage/src/main/java/com/qjj/blog/objectstorage/SecretKeyCipher.java`（整个文件）
- [x] 1.2 删除 `backend/blog/blog-object-storage/src/main/java/com/qjj/blog/objectstorage/ObjectStorageProperties.java` 中的 `SecretCrypto` 内嵌类（line 50-57）以及 `private SecretCrypto secretCrypto = new SecretCrypto();` 字段（line 34）
- [x] 1.3 修改 `backend/blog/blog-object-storage/src/main/java/com/qjj/blog/objectstorage/ObjectStorageAutoConfiguration.java`：**仅删 `secretKeyCipher` Bean 方法 + 相关 import**（`SecretKeyCipher`、`ConditionalOnMissingBean`）；**保留类壳 + `@EnableConfigurationProperties(ObjectStorageProperties.class)` 注解**（`YmlStorageProviderSupplier` 依赖 properties bean，注解失效则注入失败）
- [x] 1.4 grep 验证 `ObjectStorageProperties` 仍被 `YmlStorageProviderSupplier`（`@Component`）注入；`@EnableConfigurationProperties(ObjectStorageProperties.class)` 在 `ObjectStorageAutoConfiguration` 类上保留
- [x] 1.5 修改 `backend/blog/blog-service/src/main/java/com/qjj/blog/service/service/impl/StorageProviderServiceImpl.java`（**写路径**）：
  - 删除字段 `private final SecretKeyCipher cipher;`（line 53）
  - 删除方法 `private String encryptSafely(String plaintext) {...}`（line 310-321）
  - `create()` line 110：`entity.setSecretKey(encryptSafely(request.getSecretKey()))` → `entity.setSecretKey(request.getSecretKey())`
  - `update()` line 141：`entity.setSecretKey(encryptSafely(request.getSecretKey()))` → `entity.setSecretKey(request.getSecretKey())`
  - 删除 `import com.qjj.blog.objectstorage.SecretKeyCipher;`（line 15）；**保留** `import com.qjj.blog.objectstorage.ObjectStorageException;`（`test()` 方法 line 167 仍用）
- [x] 1.5b 修改 `backend/blog/blog-service/src/main/java/com/qjj/blog/service/service/impl/SysStorageProviderConfigSupplier.java`（**读路径**——apply 阶段 grep 发现的额外清理点）：
  - 删除字段 `private final SecretKeyCipher cipher;`（line 29）
  - `toConfig()` line 82：`cfg.setSecretKey(cipher.decrypt(entity.getSecretKey()))` → `cfg.setSecretKey(entity.getSecretKey())`
  - 删除 try-catch 块（不再抛 `ObjectStorageException`）
  - 删除 `import com.qjj.blog.objectstorage.SecretKeyCipher;` 与 `import com.qjj.blog.objectstorage.ObjectStorageException;`（均不再使用）
- [x] 1.6 grep 验证 `backend/blog` 整个目录不再出现 `cipher`、`encryptSafely`、`MASTER_KEY_MISSING`、`SecretKeyCipher`、`SecretCrypto` 字样（除 javadoc 注释中变更说明引用）
- [x] 1.7 grep 验证整个 `backend/blog` 不再 import `SecretKeyCipher`、不再引用 `SecretCrypto`

## 2. 配置文件改动

- [x] 2.1 `backend/blog/blog-service/src/main/resources/application.yml`：删除 `blog.object-storage.secret-crypto:` 配置块（line 52-55，共 4 行：`secret-crypto:` + 注释 + `key-source: env` + `key-env: OBJECT_STORAGE_SK_KEY`）
- [x] 2.2 `application-local.yml` / `application-test.yml`：grep `secret-crypto`，若有同键一并清理（预期：无）

## 3. 运维步骤（dev/test DB 已存在密文时——理论上不存在但需说明）

- [x] 3.1 dev/test DB 中若 `sys_storage_provider.secret_key` 列存在历史密文（Base64 字符串而非明文 SK），运维侧执行 `UPDATE sys_storage_provider SET secret_key='' WHERE secret_key<>'';` 重置为空串占位；后续经管理端重新录入明文 SK
- [x] 3.2 验证：dev/test 库中 `sys_storage_provider.secret_key` 列要么为空串（种子占位），要么为明文 SK（用户经管理端录入后）；**不应**存在 Base64 密文格式

## 4. 设计文档同步修改（按 design.md D2 精确到行）

- [x] 4.1 `docs/object-storage-design/object-storage-design.md` §3.2.3 表 line ~142：`accessKey`/`secretKey` 行说明改为"凭证；SK 直接存储（dev/test；生产另开加密变更），出参掩码"
- [x] 4.2 §5.2 表 line ~438：`secret_key` 行说明改为"SK，**明文存储**（dev/test 阶段；生产另开加密变更）"
- [x] 4.3 §5.3 line ~467：改为"传空表示不修改；非空则直接存储"
- [x] 4.4 §6.1 表 line ~480：删除"或 SK 加密主密钥"短句
- [x] 4.5 §6.2 line ~512-515：删除 `secret-crypto:` 配置块
- [x] 4.6 §6.2 表 line ~524：删除 `secret-crypto` 行
- [x] 4.7 §9 line ~667：改为"SK 存储：DB 落**明文**（dev/test 阶段；生产部署前须经独立加密变更重引入）；接口只收明文、只回掩码"
- [x] 4.8 §10.1 line ~682：删除"配置 SK 加密主密钥来源"短句
- [x] 4.9 §10.2 line ~699：改为"`sys_storage_provider.secret_key` 明文存储（dev/test；生产另开加密变更）"
- [x] 4.10 `docs/test-env.md` §2.4 line ~95-102：删除"SK 加密主密钥（blog 对象存储多服务商）"整段表项（含表头与 4 行内容）
- [x] 4.11 `docs/object-storage-design/README.md`：grep `加密|secret-crypto|AES|主密钥`，若目录索引或概览提及则同步

## 5. 自检与验证

- [x] 5.1 编译：`cd backend/blog && mvn -q compile` 通过
- [x] 5.2 grep 验证：`backend/blog` 内不再出现 `SecretKeyCipher`、`SecretCrypto`、`encryptSafely`、`MASTER_KEY_MISSING` 命中（`ObjectStorageException.Reason` 枚举值保留不删，但不应被代码引用）
- [x] 5.3 grep 验证：`backend/blog/blog-service/src/main/resources/application*.yml` 内不再出现 `secret-crypto` 键
- [ ] 5.4 重启 `blog-service`（无需设环境变量 `OBJECT_STORAGE_SK_KEY`），`actuator/health` UP，启动日志无 SK 加密相关 WARN
  - **PAUSED**：需用户在 IDEA 中重启 `Spring Boot.BlogApplication` Run Config（本会话不能重启用户进程）。代码已通过 `mvn -q clean compile` exit 0；重启后预期 `actuator/health` UP 且无 SK 加密 WARN。
- [ ] 5.5 行为验收（依赖前端已部署）：
  - **PAUSED**：需起活体前后端栈（重启后端 + vite dev + 已种子 RBAC 菜单的账号登录）做点击验证，本会话无法执行。代码已覆盖全部 specs 场景的对应分支，待联调后勾选。
  - 在管理端「存储服务商配置」页编辑 `minio-local`，填入明文 SK（`minio123456`），点保存——**保存成功**，不再报"SK 主密钥未配置"
  - DB 中 `sys_storage_provider.secret_key` 列值 == `minio123456`（明文）
  - 列表/详情接口响应中 `secretKeyMasked` == `"****"`，不回显明文
  - 测试连通成功
- [x] 5.6 提交规范：提交自查清单全过；提交信息 `refactor(blog-object-storage): 移除 SK 加密层（dev/test 阶段直存明文，生产另开加密变更）`
