# Tasks: add-file-service

## 1. 工程与数据

- [x] 1.1 创建 `backend/file/file-service` Maven 骨架（Boot 3.3 / JDK21）
- [x] 1.2 `file_db` 迁移：`sys_sequence`、`file_object`
- [x] 1.3 OSS/MinIO 配置项与本地 profile（密钥不入仓，test 可写文档）

## 2. 上传能力

- [x] 2.1 上传接口：类型/大小白名单、元数据落库、返回 key/url
- [x] 2.2 覆盖上传（同 key 幂等）与按 id/key 删除
- [x] 2.3 按 biz_type/biz_id 列表查询

## 3. 安全与验证

- [x] 3.1 JWT 鉴权接入（与 auth 同 iss）；未授权 401
- [x] 3.2 单测：白名单拒绝、元数据、覆盖 key
- [x] 3.3 确认生产密钥不入仓、业务模块无 OSS 密钥
