# 统一认证中心 · 数据库设计

> 配套：`docs/template/unified-auth-center-design.md`（已与**用户中心**拆分）  
> 用户主数据见：`docs/template/user-center-database.md`（**`user_db`，不在本库**）  
> 约束：`CLAUDE.md` §6.4.5 / §6.5  
> 建议库名：**`auth_db`**（认证域独占；禁止业务服务直连）

---

## 1. 设计原则

| 项 | 约定 |
|----|------|
| 表名 / 字段 | `snake_case`；表名业务名词**单数** |
| 主键 `id` | `BIGINT` 14 位（yyMMdd + 当日序号）；禁止自增 |
| 外键 | **禁止物理外键**；逻辑关联 `xxx_id` |
| 逻辑删除 | `deleted`：0 未删 / 1 已删 |
| 审计 | `create_time` / `update_time` / `create_by` / `update_by` |
| 密码 | **仅** `sys_credential.secret_ref`（PASSWORD 哈希）；本库无用户资料权威表 |
| 用户主数据 | **不在本库**；`user_id` 逻辑指向用户中心 `sys_user.id` |
| 索引 | `idx_表名_字段` / `uk_表名_字段`；单表 ≤ 5 |
| SAS 存储 | 协议元数据与业务表分离（§10） |

**表清单：**

| 表 | 职责 |
|----|------|
| `sys_sequence` | 按日发号 |
| `sys_credential` | 登录凭证（首期 PASSWORD）；`user_id` → 用户中心 |
| `oauth_client` | 客户端注册与运营（驱动 `RegisteredClient`） |
| `auth_session` | SSO 会话 |
| `auth_grant` | 授权/令牌链台账 |
| `auth_refresh_token` | RT 哈希与轮转链 |
| `login_attempt` | 登录风控 |
| `auth_audit_log` | 安全审计 |
| `oauth2_authorization` 等 | SAS 协议存储（§10） |

**不在本库：** `sys_user` 用户主数据（username 唯一、资料、启停权威）→ **用户中心 `user_db`**。

---

## 2. `sys_sequence` — 发号

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | — | |
| seq_date | DATETIME | N | — | 业务日 |
| current_val | BIGINT | N | 0 | 当日序号 |
| create_time | DATETIME | N | | |
| update_time | DATETIME | N | | |
| create_by | BIGINT | N | 0 | |
| update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | |

- **唯一：** `uk_sys_sequence_seq_date (seq_date)`

---

## 3. `sys_credential` — 登录凭证

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | — | |
| user_id | BIGINT | N | — | **用户中心** `sys_user.id`（逻辑关联，非本库 FK） |
| credential_type | VARCHAR(32) | N | — | `PASSWORD` / `SMS` / `EMAIL` / `TOTP`… 首期 `PASSWORD` |
| secret_ref | VARCHAR(255) | N | `''` | PASSWORD：BCrypt/Argon2 哈希；**禁止明文** |
| verified | TINYINT | N | 0 | 1 已验证 |
| status | TINYINT | N | 1 | 1 启用 / 0 停用 |
| expires_at | DATETIME | Y | NULL | |
| pwd_update_time | DATETIME | Y | NULL | |
| create_time | DATETIME | N | | |
| update_time | DATETIME | N | | |
| create_by | BIGINT | N | 0 | |
| update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | |

- **唯一：** `uk_sys_credential_user_type (user_id, credential_type)`
- **索引：** `idx_sys_credential_user_id (user_id)`
- 登录流程：用户中心 `username → user_id + status` → 本表验 `PASSWORD` → 通过后看用户中心状态。

---

## 4. `oauth_client` — 客户端注册与运营

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | — | |
| client_id | VARCHAR(64) | N | — | |
| client_secret_hash | VARCHAR(100) | Y | NULL | 机密客户端；公开可空 |
| client_name | VARCHAR(64) | N | `''` | |
| client_type | VARCHAR(32) | N | `PUBLIC` | `PUBLIC` / `CONFIDENTIAL` |
| client_auth_method | VARCHAR(32) | N | `NONE` | |
| grant_types | VARCHAR(255) | N | — | 如 `authorization_code,refresh_token` |
| redirect_uris | VARCHAR(1024) | N | — | 精确白名单 |
| scopes | VARCHAR(255) | N | `openid,profile` | |
| require_pkce | TINYINT | N | 1 | |
| require_consent | TINYINT | N | 0 | |
| reuse_refresh_tokens | TINYINT | N | 0 | **必须 0** |
| access_token_ttl_sec | INT | N | 600 | |
| refresh_token_ttl_sec | INT | N | 604800 | |
| system_code | VARCHAR(32) | N | `''` | |
| owner | VARCHAR(64) | N | `''` | |
| env | VARCHAR(16) | N | `dev` | |
| enabled | TINYINT | N | 1 | |
| remark | VARCHAR(255) | N | `''` | |
| create_time | DATETIME | N | | |
| update_time | DATETIME | N | | |
| create_by | BIGINT | N | 0 | |
| update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | |

- **唯一：** `uk_oauth_client_client_id (client_id)`
- **索引：** `idx_oauth_client_enabled (enabled)`；`idx_oauth_client_system_code (system_code)`

---

## 5. `auth_session` — SSO 会话

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | — | |
| session_token_hash | VARCHAR(64) | N | — | |
| user_id | BIGINT | N | — | 用户中心 id |
| auth_time | DATETIME | N | — | |
| expires_at | DATETIME | N | — | |
| revoked_at | DATETIME | Y | NULL | |
| user_agent | VARCHAR(255) | N | `''` | |
| ip | VARCHAR(64) | N | `''` | |
| create_time | DATETIME | N | | |
| update_time | DATETIME | N | | |
| create_by | BIGINT | N | 0 | |
| update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | |

- **唯一：** `uk_auth_session_token_hash (session_token_hash)`
- **索引：** `idx_auth_session_user_id (user_id)`；`idx_auth_session_expires_at (expires_at)`

---

## 6. `auth_grant` — 授权/令牌链台账

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | — | |
| user_id | BIGINT | N | — | |
| client_id | VARCHAR(64) | N | — | |
| session_id | BIGINT | Y | NULL | |
| scopes | VARCHAR(255) | N | `''` | |
| status | TINYINT | N | 1 | 1 活跃 / 0 吊销 |
| revoked_at | DATETIME | Y | NULL | |
| revoke_reason | VARCHAR(64) | N | `''` | |
| create_time | DATETIME | N | | |
| update_time | DATETIME | N | | |
| create_by | BIGINT | N | 0 | |
| update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | |

- **索引：** `idx_auth_grant_user_id (user_id)`；`idx_auth_grant_client_id (client_id)`；`idx_auth_grant_status (status)`

---

## 7. `auth_refresh_token` — Refresh Token

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | — | |
| grant_id | BIGINT | N | — | |
| user_id | BIGINT | N | — | |
| client_id | VARCHAR(64) | N | — | |
| token_hash | VARCHAR(64) | N | — | 不存明文 |
| parent_id | BIGINT | Y | NULL | 轮转来源 |
| status | TINYINT | N | 1 | 1 有效 / 0 轮转作废 / 2 吊销 |
| expires_at | DATETIME | N | — | |
| rotated_at | DATETIME | Y | NULL | |
| revoked_at | DATETIME | Y | NULL | |
| create_time | DATETIME | N | | |
| update_time | DATETIME | N | | |
| create_by | BIGINT | N | 0 | |
| update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | |

- **唯一：** `uk_auth_refresh_token_hash (token_hash)`
- **索引：** `idx_auth_refresh_token_grant_id (grant_id)`；`idx_auth_refresh_token_user_id (user_id)`；`idx_auth_refresh_token_expires_at (expires_at)`
- 重用检测：非有效 RT 再次使用 → 吊销该 `grant_id` 全链。

---

## 8. `login_attempt` — 登录风控

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | — | |
| username | VARCHAR(64) | N | `''` | 尝试登录名 |
| user_id | BIGINT | Y | NULL | |
| success | TINYINT | N | 0 | |
| fail_reason | VARCHAR(64) | N | `''` | |
| ip | VARCHAR(64) | N | `''` | |
| user_agent | VARCHAR(255) | N | `''` | |
| client_id | VARCHAR(64) | N | `''` | |
| create_time | DATETIME | N | | |
| update_time | DATETIME | N | | |
| create_by | BIGINT | N | 0 | |
| update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | |

- **索引：** `idx_login_attempt_username (username)`；`idx_login_attempt_ip (ip)`；`idx_login_attempt_create_time (create_time)`

---

## 9. `auth_audit_log` — 安全审计

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | — | |
| action | VARCHAR(64) | N | — | `LOGIN` / `TOKEN_ISSUE` / `REFRESH` / `REVOKE` / `CLIENT_UPDATE`… |
| actor_user_id | BIGINT | Y | NULL | |
| target_type | VARCHAR(32) | N | `''` | |
| target_id | VARCHAR(64) | N | `''` | |
| detail | VARCHAR(512) | N | `''` | 禁止密码/完整 token |
| ip | VARCHAR(64) | N | `''` | |
| create_time | DATETIME | N | | |
| update_time | DATETIME | N | | |
| create_by | BIGINT | N | 0 | |
| update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | |

- **索引：** `idx_auth_audit_log_action (action)`；`idx_auth_audit_log_create_time (create_time)`

---

## 10. SAS 协议存储

| 数据 | 存储 |
|------|------|
| Client 定义 | **`oauth_client` 权威** → `RegisteredClientRepository` |
| 授权码 / AT / RT 协议状态 | SAS `OAuth2AuthorizationService`（如 `oauth2_authorization`） |
| 运营吊销索引 | `auth_grant` + `auth_refresh_token` |
| SSO 会话 | `auth_session` |

协议合法性以 SAS 记录为准；管理端吊销先走 SAS 失效/revoke，再更新台账。

---

## 11. 逻辑关系

```text
用户中心 user_db.sys_user.id
        │（逻辑，无 FK）
        ▼
sys_credential · auth_session · auth_grant · auth_refresh_token · login_attempt

oauth_client 1 ──── * auth_grant
```

---

## 12. 查询与维护

- 禁止 `SELECT *`；`login_attempt` / `auth_audit_log` / 过期 RT 定时清理。
- 登录解析 username **不在本库**，走用户中心 API。
- 按人踢下线：`idx_auth_grant_user_id`、`idx_auth_refresh_token_user_id`。
- 迁移目录：`deploy/db/migration/auth_db/`；连接见 `docs/test-env.md`。
