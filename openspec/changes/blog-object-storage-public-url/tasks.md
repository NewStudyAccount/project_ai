## 1. 配置与封装（framework）

- [x] 1.1 `BlogMinioProperties`（或等价）增加 `publicBaseUrl`（`public-base-url`，必配语义）；读路径不再依赖 `presignTtl`（`presignPut` 可留默认不用）
- [x] 1.2 `application.yml` / `application-local.yml` / `application-test.yml` 增加 `public-base-url` 结构键（无密钥；具体值对齐 `docs/test-env.md`）
- [x] 1.3 `ObjectStorageClient`：新增 `publicUrl(objectKey)`（`trimRight(publicBaseUrl,'/') + '/' + objectKey`）；`put` 写入 `Content-Type` + `Content-Disposition`（image/*、application/pdf → `inline`；其它 → `attachment` + RFC 5987 原名）；废弃/删除读路径 `presignGet`（确认无调用后移除）
- [x] 1.4 `docs/test-env.md` 登记 blog 公开读基址与桶公共读说明（无生产密钥）

## 2. 删除前引用检查

- [x] 2.1 定义 `FileReferenceChecker`（`check(fileId) → referenced + refs`），放 blog-service（或 framework 能力位，按 design 不硬编码业务表）
- [x] 2.2 实现 Article 引用 Checker：`blog_article.cover_file_id`、`blog_article_detail.content_file_id` / `draft_file_id`（及必要的 `sys_file.biz_type`/`biz_id`）
- [x] 2.3 `FileServiceImpl.logicalDelete`：删前调全部 Checker；有引用抛业务错误（`msg` 含引用摘要，错误码对齐 `BlogErrorCodeEnum` / `docs/error-code-ranges.md`）；无引用才 `deleted=1` + 审计
- [x] 2.4 登记运维物理清理二次引用检查要求（脚本契约/README 或 ops 说明；本变更不实现调度）

## 3. API 与 VO（公开 URL）

- [x] 3.1 `GET /api/v1/files/{id}/url` 返回稳定 `{url}`（`FileUrlVO` 或 `PresignVO` 去 `expireAt`）；`FileVO`/列表内嵌 `url`
- [x] 3.2 `FileServiceImpl.presign` → 语义改为 `publicUrl`（方法名可 `getFileUrl`）；去掉 10min 签发逻辑；`@RateLimit` 保留查询维度
- [x] 3.3 `ArticleContentStorageService`：`presignGet` 改为公开 `url`；`ArticleService` 正文取址不再返回短 TTL
- [x] 3.4 `PublicController`：`content-url` / 公开插图改为稳定公开 `url`（或文内直写 url + 按 spec 二择一收敛 `public/files/{id}`）；不中转对象 body
- [x] 3.5 确认出参无 `expireAt`、无签名 query；编译通过 `mvn -q compile`（`backend/blog`）

## 4. 文档与前端对齐

- [x] 4.1 `docs/blog-system/*`：预签名/TTL/`{url,expireAt}` 表述改为公开永久 URL + Content-Disposition + 引用检查（不改 object-storage-design 已裁决正文）
- [x] 4.2 `frontend/blog-admin` 文件页与编辑器：预览/下载/缩略图用 VO.`url`；删除失败展示引用摘要；去掉过期重签
- [x] 4.3 提交前自检：`cd backend/blog && mvn -q compile`；涉及前端则 `npm run lint && npm run type-check`（CLAUDE.md §3.2）
