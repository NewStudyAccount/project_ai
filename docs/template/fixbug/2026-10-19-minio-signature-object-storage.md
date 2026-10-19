# 2026-10-19 MinIO SignatureDoesNotMatch（对象存储上传）缺陷记录

> 范围：blog `POST /api/v1/files` → `ObjectStorageClient.put` → MinIO `putObject`。  
> 原则：只记已复现并定位的问题；验证方式可复跑。  
> 配套：`docs/template/object-storage-design.md`、`docs/test-env.md` §2.4、`docs/blog-system/README.md`「对象存储裁剪取值」。

---

## 1. 上传接口 403 SignatureDoesNotMatch

| 项 | 内容 |
|----|------|
| 现象 | `POST /api/v1/files` 失败；日志 `MinIO putObject failed bucket=blog key=…jpg`，`ErrorResponse(code = SignatureDoesNotMatch, …)`，HTTP 403 |
| 实际失败请求 | minio-java 在 PutObject 前的 **`GET /blog?location=`**（GetBucketLocation）；`User-Agent: minio-java/8.5.17` |
| 根因 | **SecretKey 错误**。AccessKey `minio_admin` 在服务端**存在**（错密 → `SignatureDoesNotMatch`；不存在的 AK → `InvalidAccessKeyId`）；`application-local.yml` 误配 `secret-key: 123456`，服务端实际为 **`minio123456`** |
| 判别方法 | 对 `GET /{bucket}?location=` 做 SigV4：`InvalidAccessKeyId` = AK 不存在；`SignatureDoesNotMatch` = AK 存在但 SK/签名不对；`200` = 凭证可用 |
| 排除 | 时钟偏差约 23s（在 15 分钟容差内）；桶不存在通常报 `NoSuchBucket`；region 空 `LocationConstraint`（us-east-1 默认）可工作 |
| 改动 | ① `application-local.yml` `blog.minio.secret-key` → **`minio123456`**；② `docs/test-env.md` §2.4 同步；③ blog 对象存储裁剪取值登记 `docs/blog-system/README.md` |
| 验证 | 同凭证 SigV4：`GET /blog?location=` 200、`PUT/GET/DELETE /blog/probe.txt` 200/200/204 |

---

## 2. 排查顺序（同类现象直接按此走）

1. **先看日志里的实际请求**：`SignatureDoesNotMatch` 常出现在 PutObject 前的 `?location=`（GetBucketLocation），不是对象键写错。
2. **用错误码分流**：
   - `InvalidAccessKeyId` → AK 不存在（换/建账号）
   - `SignatureDoesNotMatch` → AK 存在，**SK 或签名输入不对**（先核对 Secret）
3. **核对三方是否一致**：服务端真实 AK/SK/桶 ↔ `application-local.yml` 的 `*.minio.*` ↔ `docs/test-env.md` §2.4。
4. **Secret 长度与特殊字符**：过短（如 `123456`）或含未转义字符时易配错；以服务端实际为准回写配置。
5. **region**：`MinioClient.builder()` 未显式 `.region(...)` 时，SDK 先取 bucket location 再按 region 重签；服务端 `MINIO_REGION` 非默认时需显式对齐。
6. **禁止**为绕过签名问题改成浏览器直传或放宽 CORS（`object-storage-design.md` §4.1 / §7）；也不要把 SK 写进日志。

---

## 3. 关联文档动作

| 文档 | 动作 |
|------|------|
| `docs/test-env.md` §2.4 | 与 `application-local.yml` 对齐（已同步） |
| `docs/blog-system/README.md` | 补「对象存储裁剪取值」登记 |
| `docs/template/object-storage-design.md` | 契约不变；连接信息仍指向 `docs/test-env.md` |

**遗留（文档/配置，非本条代码修复）：**

- `application-test.yml` 若跑 test profile，需补齐 `blog.minio`（endpoint/AK/SK/bucket），否则会静默用公共默认值。
- 预签名 `expireAt` 当前实现为时间字符串，设计 §4.2 锁定为 **number / epoch 毫秒**——契约偏差，待另开变更对齐或在系统裁剪中登记差异。
