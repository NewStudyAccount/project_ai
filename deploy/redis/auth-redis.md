# Redis 持久化与认证键约定（auth）

> 对应 unified-auth-center 任务 5.5。生产/测试 Redis 须开启 AOF everysec + RDB；认证临时键一律带 TTL。

## 1. 持久化要求

| 项 | 值 | 说明 |
|----|-----|------|
| `appendonly` | `yes` | 开启 AOF |
| `appendfsync` | `everysec` | 每秒刷盘，兼顾吞吐与丢失窗口 |
| `save` | `900 1` / `300 10` / `60 10000` | 保留 RDB 快照 |
| `maxmemory-policy` | `noeviction` 或 `volatile-ltl` | 认证键均有 TTL，避免把无 TTL 台账键挤掉 |

配置落在 Redis 服务器（见 `docs/test-env.md`），不把生产密钥写进本仓库。

## 2. 认证中心 Redis 键（均须 TTL）

| 键 | 用途 | TTL |
|----|------|-----|
| `auth:refresh-token:{sha256}` | 有效 Refresh Token 哈希 | 与 RT 一致（默认 7 天） |
| `auth:refresh-token:rotated:{sha256}` | 已轮转旧键（重用检测） | 7 天或原过期时间 |
| `auth:authorization:{id}` | SAS 授权元数据索引 | 8 小时 |
| `auth:sso-session:{sessionId}` | SSO 会话 | `sso-session-ttl-seconds` |
| `auth:user-sessions:{userId}` | 用户→会话二级索引 | 与会话一致 |
| `auth:grant-refresh-tokens:{grantId}` | grant→RT 哈希索引 | 与 RT 一致 |
| `auth:user-grants:{userId}` | 用户→grant 索引 | 7 天 |
| `auth:login-attempt:user:{username}` | 登录失败计数 | `login-lock-seconds` |
| `auth:login-attempt:ip:{ip}` | IP 失败计数 | `login-lock-seconds` |
| `auth:login-rate:ip:{ip}` | 登录 IP 限流 | 60 秒 |

检查原则：不允许存在无 TTL 的认证临时键；`auth_grant` 台账在 MySQL，不存 Token 本体。
