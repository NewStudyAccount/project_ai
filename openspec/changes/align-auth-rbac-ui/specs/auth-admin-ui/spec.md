# auth-admin-ui · 认证中心管理端视觉与页面布局

## Purpose

约束 auth-admin 的视觉基线与页面骨架，使各系统管理端同构可复制，覆盖 App Shell、列表/树管理/弹窗布局与登录门面边界。

## ADDED Requirements

### Requirement: 应用壳与视觉基线统一
auth-admin SHALL 使用统一 App Shell：左侧栏固定 220px、顶栏 60px、主内容区 `.page-card`（页边距/内边距 16px、圆角 8px）；页底色 `#f5f7fa`、表面白底、分割线 `#ebeef5`，主色跟随 Element Plus，字体 `Inter, 'Microsoft YaHei', sans-serif`（`admin-ui-design.md` §1–§2）。

#### Scenario: 进入管理端主界面
- **WHEN** 操作员登录后进入任意业务页
- **THEN** 页面呈现侧栏 + 顶栏 + 主内容三栏壳，侧栏由已授权菜单渲染

### Requirement: 页面布局模式收敛
业务页 SHALL 落在下列模式之一：P-List（工具栏+表格+分页）、P-Tree（树形表）、D-Form（表单弹窗）、D-Panel（授权/详情弹窗）。菜单管理 SHALL 为 P-Tree + D-Form；角色管理 SHALL 为 P-List + D-Form + D-Panel（授权树）。

#### Scenario: 菜单管理页布局
- **WHEN** 管理员打开菜单管理
- **THEN** 呈现工具栏（新建）+ 树形表；新建/编辑为弹窗表单，按 type 联动显隐 path/component/permission，且不提供 id 输入框

#### Scenario: 角色管理页布局
- **WHEN** 管理员打开角色管理并进入授权
- **THEN** 列表 + 表单弹窗 + 授权树弹窗（勾选回显 menuIds），保存为全量覆盖提交

### Requirement: 管理端不做账密登录
auth-admin SHALL NOT 内嵌账密登录表单；未登录/401 SHALL 跳转唯一登录门面 `auth-portal`。

#### Scenario: 会话失效
- **WHEN** 访问管理接口返回 401
- **THEN** 清理本地令牌并跳转登录门面，不在本应用内展示登录框
