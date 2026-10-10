# blog-internal-api · 对内 Feign 契约

## ADDED Requirements

### Requirement: 文件元数据对内查询
`blog-api` SHALL 提供 `GET /internal/files/{id}`（文件元数据 VO：`id` String、`bucket`、`objectKey`、`originalName`、`contentType`、`sizeBytes`）；仅服务间调用，网关 MUST NOT 路由 `/internal/**`。Feign 须配置 Fallback 与固定连接/读取超时。

#### Scenario: 命中查询
- **WHEN** 其他服务经 Feign 按 `id` 查询文件元数据
- **THEN** 返回 VO，`id` 为 String

#### Scenario: 未命中与降级
- **WHEN** 记录不存在或下游失败
- **THEN** Fallback 返回约定空/降级标记，MUST NOT 抛裸异常给调用方

### Requirement: 健康探针
`blog-api` SHALL 提供 `GET /internal/ping`（VO：`pong` + 可选时间戳）；Fallback 返回降级标记。

#### Scenario: 探针成功
- **WHEN** 调用方请求 `/internal/ping`
- **THEN** 返回 `pong` 标记

#### Scenario: 探针降级
- **WHEN** 服务不可用
- **THEN** Fallback 返回降级标记，不抛裸异常

### Requirement: 对内接口网络边界
对内接口 MUST 仅经 Nacos + OpenFeign 服务间可达；前端与公网 MUST NOT 访问 `/internal/**`。

#### Scenario: 网关不转发对内前缀
- **WHEN** 外部请求打到网关 `/internal/**` 或等价路径
- **THEN** 不转发到 `blog-service`（拒绝/无路由）

#### Scenario: 选人只读走用户中心契约
- **WHEN** 角色分配需要「选人」
- **THEN** 经用户中心 `user-api` Feign + Fallback 只读查询，禁止直连用户库表，Feign 在事务外
