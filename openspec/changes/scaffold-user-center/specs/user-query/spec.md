# user-query · 对内用户查询契约

## ADDED Requirements

### Requirement: 对内查询契约（user-api）
系统 SHALL 经 `user-api` 契约构件（DTO/VO + Feign + Fallback，**无** Entity/Mapper/实现）提供对内查询：按 id 查基础信息、按 username 查（返回 `id` + `status`，供认证中心登录解析）、按 id 查 profile（OIDC/展示用，脱敏）。Feign MUST 配置 Fallback 与固定连接/读取超时；调用方 MUST NOT 在事务内调用。

#### Scenario: 按 id 查询基础信息
- **WHEN** 合法调用方按 `id` 请求基础信息
- **THEN** 返回该用户基础字段 VO，`id` 为 String

#### Scenario: 按 username 登录解析
- **WHEN** 认证中心按 `username` 调用登录解析接口
- **THEN** 返回 `id` 与 `status`；账号不存在时返回语义明确的失败（非系统异常），不泄露给终端用户账号是否存在由认证侧文案控制

#### Scenario: 按 id 查询 profile
- **WHEN** 合法调用方按 `id` 请求 profile
- **THEN** 返回资料 VO，手机号/邮箱已脱敏

### Requirement: 对内接口仅服务间可达
对内查询接口 SHALL 仅供服务间（内网）调用，不对公网暴露（不被 Nginx/网关路由至外部）；`user-api` 不含任何 Entity/Mapper/Service 实现（`CLAUDE.md` §5.4 / §5.6.1）。

#### Scenario: 公网路径不可达
- **WHEN** 外部请求经 Nginx/网关尝试访问对内查询接口
- **THEN** 不可达（路由不转发或拒绝）；服务间 Feign 调用正常

#### Scenario: 下游故障降级
- **WHEN** 用户中心不可用导致调用方 Feign 失败
- **THEN** 触发 Fallback 降级（语义明确的兜底返回），不向调用方抛裸异常/堆栈
