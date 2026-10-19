## Context

见 `proposal.md`（动机与范围）。`backend/blog` 当前：

- `blog-framework/.../ObjectStorageClient`：`put` / `getBytes` / `presignGet` / `remove` / `exists`（MinIO SDK）
- `FileServiceImpl.presign` → `PresignVO{url, expireAt}`（10min）；`ArticleContentStorageService.presignGet` 供正文
- `FileServiceImpl.logicalDelete`：仅 `deleted=1` + 审计，**无**引用检查
- 配置：`blog.minio.presign-ttl: 10m`；无 `public-base-url`
- 契约源：`docs/object-storage-design.md` v3；错误码/返回体/幂等限流对齐 `CLAUDE.md`

## Goals / Non-Goals

**Goals:**

- 读路径全部改为稳定公开 `url`（拼接规则与元数据见 object-storage-design §3.5–4.2）
- 上传写 `Content-Disposition`；删除前引用检查可测、可拒绝
- 改动最小集：storage 封装 + File/Article/Public 读路径 + 配置；不顺手重构

**Non-Goals:**

- 不改 `sys_file` 表结构、不引入 Flyway、不批量上传/图片加工
- 不做「强制删除」；不强制 HTTPS；不迁出 MinIO SDK 到新平行封装
- 不改其它系统；不在此变更内归档其它 openspec change

## Decisions

| 决策 | 选择 | 备选与理由 |
|------|------|------------|
| 访问 API 形态 | `GET /files/{id}/url` 改为 `{ url }`（无 `expireAt`）；VO 列表内嵌 `url` | 备选：删除该接口仅列表带 `url`。保留路径便于管理端「刷新地址」与兼容已生成前端调用，破坏性更小 |
| VO 类型 | 将 `PresignVO` 改为 `FileUrlVO`（仅 `url`）或原类去掉 `expireAt` 字段 | 改名可消除预签名误导；若前端已依赖类名，可暂留类名并去字段（tasks 验收编译+接口形态） |
| `publicUrl` 落点 | `ObjectStorageClient.publicUrl(objectKey)`，用 `BlogMinioProperties.publicBaseUrl` 拼接 | 备选：业务拼接。禁止——必须唯一拼装点（object-storage-design §4.2） |
| Content-Disposition | `put` 内按 contentType 决定 `inline`/`attachment`，`originalName` 经 Service 传入或 put 增加参数 | 备选：响应时改头——公开读不经业务，不可行 |
| 引用检查扩展点 | `FileReferenceChecker` 接口 + blog 实现（Article 封面/正文/草稿）；Spring `List<…>` | 备选：写死在 `FileServiceImpl`。SPI 便于其它业务复用且对齐设计 §3.5 |
| 公开插图 | **优先**：Markdown/正文写入公开 `url`（上传成功即得）；`GET /public/files/{id}` 保留为兼容跳转/读元数据或标废弃 | 备选：继续业务代理回源——违反不中转 body，弃 |
| 正文 content-url | 返回公开 `url`（或正文 HTML/MD 内已是公开 url 时可废弃独立取址） | 与 `blog-public-read` spec 对齐 |
| 配置键 | `public-base-url`（kebab-case）挂在现有 minio/`object-storage` 配置前缀下 | 对齐 object-storage-design §6.2；连接信息仍 `docs/test-env.md` |
| 错误码 | 有引用删除失败使用既有业务段（如 `FILE_IN_USE` 或等价）；先查 `docs/error-code-ranges.md` / blog 错误码枚举再占用 | 禁止跨系统撞号（CLAUDE.md §6.4.2） |

## Risks / Trade-offs

- [公开可读、URL 外泄] → 已裁决公共读；禁止敏感文件入桶；撤权靠删对象/改桶策略（文档已声明）
- [删除后物理清理前 URL 仍可访问] → 确认框文案强调逻辑删除；脚本窗口 ≥7 天
- [无 Checker 时删除不拦截] → blog 必须注册封面/正文/草稿 Checker；无引用业务可不注册
- [前端仍按 `expireAt` 写刷新逻辑] → 接口去字段 + 前端 tasks 同步改；响应破坏性已在 proposal 标明
- [MinIO 公共读未配导致 403] → 部署验收：匿名 GET 200、匿名 PUT 失败（object-storage-design §4.2）
- [http 混合内容] → 用户裁决不强制 HTTPS；内网 http 管理端可接受

## Migration Plan

1. 配置：增加 `public-base-url`（local/test 取 `docs/test-env.md`）；桶策略公共读
2. 封装与服务：`publicUrl` + put Disposition + 引用检查 + API/VO（可一次 PR）
3. 前端 blog-admin：预览/下载/缩略图用 `url`；去掉过期重签（随 tasks 或既有 UI 变更）
4. 文档：`docs/blog-system/*` 预签名表述改公开 URL
5. 回滚：还原接口字段与 `presignGet` 需回退代码+配置；对象已写入的 Disposition 无害可留

## Open Questions

（无——引用检查、URL 拼接、公共读、HTTP 均已裁决；错误码具体枚举值在实施时对齐现有 `BlogErrorCodeEnum` / `error-code-ranges.md` 即可，不改变本设计结构。）
