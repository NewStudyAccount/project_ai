# 用户中心 · 数据库设计

> 配套：`docs/template/user-center-design.md`  
> **不含任何密码字段**；凭证在 `auth_db.sys_credential`  
> 约束：`CLAUDE.md` §6.4.5 / §6.5  
> 建议库名：**`user_db`**（用户域独占；禁止其他服务直连）

---

## 1. 设计原则

| 项 | 约定 |
|----|------|
| 表名 / 字段 | `snake_case`；表名单数 |
| 主键 `id` | `BIGINT` 14 位（yyMMdd + 当日序号）；**= OIDC `sub`** |
| 外键 | 禁止物理外键 |
| 逻辑删除 | `deleted` 0/1 |
| 审计 | `create_time` / `update_time` / `create_by` / `update_by` |
| 密码 | **禁止** `password_hash` 或等价字段 |
| 索引 | `idx_表名_字段` / `uk_表名_字段` |

**表清单：**

| 表 | 职责 |
|----|------|
| `sys_sequence` | 按日发号 |
| `sys_user` | 用户主数据（账号权威） |
| `sys_user_profile` | 资料扩展（可选，字段多时拆出） |
| `user_audit_log` | 用户管理审计 |

---

## 2. `sys_sequence` — 发号

与认证库同构：`seq_date` 唯一 + `current_val`。

- **唯一：** `uk_sys_sequence_seq_date (seq_date)`

---

## 3. `sys_user` — 用户主数据（权威）

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | — | 全局用户 id（`sub`） |
| username | VARCHAR(64) | N | — | 登录名，**全局唯一** |
| real_name | VARCHAR(64) | N | `''` | |
| nickname | VARCHAR(64) | N | `''` | |
| email | VARCHAR(128) | N | `''` | |
| phone | VARCHAR(32) | N | `''` | 出参脱敏 |
| avatar | VARCHAR(255) | N | `''` | |
| status | TINYINT | N | 1 | 1 正常 / 0 停用 / 2 锁定 |
| dept_id | BIGINT | Y | NULL | 预留组织 |
| last_login_time | DATETIME | Y | NULL | 可由认证侧回写或事件更新（可选） |
| remark | VARCHAR(255) | N | `''` | |
| create_time | DATETIME | N | | |
| update_time | DATETIME | N | | |
| create_by | BIGINT | N | 0 | |
| update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | |

- **唯一：** `uk_sys_user_username (username)`
- **索引：** `idx_sys_user_status (status)`；`idx_sys_user_dept_id (dept_id)`
- **禁止字段：** `password_hash` / `password` / 任何凭证密文。

---

## 4. `sys_user_profile` — 资料扩展（可选）

首期可与 `sys_user` 合并；字段增多后再拆。

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | — | |
| user_id | BIGINT | N | — | 同 `sys_user.id` |
| gender | TINYINT | N | 0 | |
| birthday | DATETIME | Y | NULL | |
| address | VARCHAR(255) | N | `''` | |
| extra_json | VARCHAR(1024) | N | `''` | 轻量扩展；避免滥用 TEXT |
| create_time | DATETIME | N | | |
| update_time | DATETIME | N | | |
| create_by | BIGINT | N | 0 | |
| update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | |

- **唯一：** `uk_sys_user_profile_user_id (user_id)`

---

## 5. `user_audit_log` — 管理审计

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | — | |
| action | VARCHAR(64) | N | — | `CREATE` / `UPDATE` / `STATUS_CHANGE`… |
| actor_user_id | BIGINT | Y | NULL | 操作者 |
| target_user_id | BIGINT | Y | NULL | |
| detail | VARCHAR(512) | N | `''` | |
| ip | VARCHAR(64) | N | `''` | |
| create_time | DATETIME | N | | |
| update_time | DATETIME | N | | |
| create_by | BIGINT | N | 0 | |
| update_by | BIGINT | N | 0 | |
| deleted | TINYINT | N | 0 | |

- **索引：** `idx_user_audit_log_target_user_id (target_user_id)`；`idx_user_audit_log_create_time (create_time)`

---

## 6. 逻辑关系

```text
sys_user 1 ──── 0..1 sys_user_profile
sys_user 1 ──── * user_audit_log

（逻辑）sys_user.id ──► auth_db.sys_credential.user_id
```

---

## 7. 查询与维护

- 登录：`uk_sys_user_username` 点查。
- 列表：按 `status` / `dept_id` / 模糊 username（注意索引）。
- 禁止 `SELECT *`；手机号/邮箱出参脱敏（CLAUDE.md §6.7）。
- 迁移：`deploy/db/migration/user_db/`；连接信息 `docs/test-env.md`。
- 同步给业务系统：事件或拉取后写**本地投影**，不共享本库 Entity。
