# Design: add-file-service

## Context

- 已有统一认证 JWT；blog 需「文章内容文件 + 图片进 OSS」。
- 决策 F2：file **独立服务**，不是 blog 内模块。
- 文章策略：MD 原文在 blog 库；`content.md` / `content.html` / assets 均为 file 管理的对象。

## Goals / Non-Goals

**Goals:** 上传与对象生命周期、元数据表、对象键规范、与 blog 的引用契约。  
**Non-Goals:** 缩略图/水印、杀毒、完整直传 STS、CDN 运维。

## Decisions

### D1. 部署形态
单体 `file-service` + `file_db`；Nginx 直反代；不经业务网关混部。

### D2. 对象键规范
```
{biz}/{bizId}/{scene}/{uuid}.{ext}
示例：blog/posts/26092200000001/content.html
```

### D3. 内容与权限
- 凭证只在 file 配置/密钥管理。
- 默认登录可上传；公开读按 bucket 配置。
- 删除：元数据逻辑删 + 对象物理删（可异步）。

### D4. 与 blog 契约
返回 `object_key` + `url`；发布可覆盖同 key（幂等）。

## Risks / Trade-offs

- [OSS 故障] → 先写库后写文件，失败同 key 重试。  
- [孤儿对象] → `biz_type/biz_id` 对账清理。  
- [密钥泄露] → 禁止密钥入仓。

## Migration Plan
1. 建 `file_db` 与测试桶。 2. 部署 file-service。 3. blog 只存 object_key。

## Open Questions
1. 匿名是否允许上传（默认否）。 2. 公开读 vs 签名 URL。 3. 单文件上限（建议 ≤10MB）。
