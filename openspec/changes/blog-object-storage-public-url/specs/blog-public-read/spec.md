## Purpose

约束 blog 用户端公开读（文章正文与插图）的访问方式：使用稳定公开 URL 直连对象存储，不依赖预签名过期与重签。

## ADDED Requirements

### Requirement: 公开插图与正文使用稳定公开 URL

用户端公开读获取的文章正文、封面与 Markdown 插图 SHALL 使用稳定公开 `url`（或业务侧已写入正文的同一 `url`），浏览器直连对象存储/公开地址，不经网关或业务服务中转对象 body。

#### Scenario: 公开文章详情含图

- **WHEN** 匿名用户请求已发布文章详情
- **THEN** 返回的封面/正文中的图片地址为公开稳定 `url`（`publicBaseUrl + objectKey` 或已写入 Markdown 的该地址），跨日仍可访问

#### Scenario: 正文取址无预签名

- **WHEN** 客户端请求正文取址（`content-url` 等价能力）或直接使用文内插图地址
- **THEN** 响应不包含 `expireAt`，不要求短 TTL 重签；地址在对象未物理删除前长期有效

#### Scenario: 公开插图接口与公开 URL 一致

- **WHEN** 匿名访问公开插图稳定地址（如 `/api/v1/public/files/{id}` 若仍保留）
- **THEN** 行为与直接访问公开 `url` 一致（仅 `image/*`），或设计/文档明确改为「正文内直接写公开 `url`」后废弃该代理路径——二者择一且与 `object-storage-design.md` 不中转 body 不冲突
