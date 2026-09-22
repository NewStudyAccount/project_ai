# Spec: user-identity-projection

## ADDED Requirements

### Requirement: 子系统不存完整用户账号

业务系统 SHALL NOT 存储用户密码等凭证，SHALL NOT 将本地表作为用户权威源；配权限 SHALL 通过 user_id 引用认证中心用户。

#### Scenario: 投影字段边界

- **WHEN** 子系统保存用户投影
- **THEN** 仅含 user_id、username、real_name、status、dept 等展示字段，不含 password_hash

### Requirement: 轻量用户投影表

子系统 MAY 保存 `sys_user_ref` 只读投影以支持列表展示；该表 SHALL 可删除并可由认证中心重建。

#### Scenario: 按 id 存在投影

- **WHEN** 投影中已有 user_id
- **THEN** 成员列表可直接展示用户名/姓名而无需每次远程查询

#### Scenario: 无投影仍可赋权

- **WHEN** 子系统未启用 sys_user_ref
- **THEN** 仍可通过 user_id 赋权，并通过批量用户 API 获取展示名

### Requirement: 投影同步触发

子系统 SHALL 在下列时机对投影做 UPSERT：用户进入该系统（登录/SSO 签发后）、赋权时快照所选用户、列表发现缺失 id 时通过批量查询补洞。

#### Scenario: 登录进入系统时 upsert

- **WHEN** 用户通过 SSO 或登录进入系统 A
- **THEN** 系统 A 将该用户展示信息 UPSERT 至本地投影

#### Scenario: 列表补洞

- **WHEN** 角色成员列表存在本地缺失的 user_id
- **THEN** 调用批量查询后 UPSERT，再展示；单次补洞失败不阻断已知成员展示

### Requirement: 授权不依赖投影新鲜度

鉴权与赋权结果 SHALL NOT 以 `sys_user_ref.status` 或投影内容为放行依据；停用用户 SHALL 在认证中心/令牌层失效。

#### Scenario: 停用用户

- **WHEN** 认证中心停用用户且令牌已按策略失效
- **THEN** 即使本地投影仍为启用显示，该用户也无法通过受保护接口

#### Scenario: 投影同步失败

- **WHEN** 赋权写入 user_role 成功但投影 UPSERT 失败
- **THEN** 赋权仍然有效，投影可由后续补洞修复

### Requirement: 选人实时查询

用户选择控件 SHALL 通过认证中心搜索 API 获取候选；SHALL NOT 依赖本地投影作为全量人员目录。

#### Scenario: 搜索用户

- **WHEN** 管理员在系统内搜索并选择用户赋权
- **THEN** 候选列表来自认证中心实时查询结果
