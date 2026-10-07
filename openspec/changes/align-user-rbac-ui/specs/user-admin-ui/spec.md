# user-admin-ui · 用户中心管理端视觉与页面布局

## Purpose

约束 user-admin 的视觉基线与页面骨架，使各系统管理端同构可复制，覆盖 App Shell、列表/树管理/弹窗布局模式。

## ADDED Requirements

### Requirement: 应用壳与视觉基线统一
user-admin SHALL 使用统一 App Shell：左侧栏固定 220px、顶栏 60px、主内容区 `.page-card`（页边距/内边距 16px、圆角 8px）；页底色 `#f5f7fa`、表面白底、分割线 `#ebeef5`，主色跟随 Element Plus，字体 `Inter, 'Microsoft YaHei', sans-serif`（`admin-ui-design.md` §1–§2）。

#### Scenario: 进入管理端主界面
- **WHEN** 操作员登录后进入任意业务页
- **THEN** 页面呈现侧栏 + 顶栏 + 主内容三栏壳，侧栏由已授权菜单渲染

### Requirement: 页面布局模式收敛
业务页 SHALL 落在 P-List / P-Tree / D-Form / D-Panel 之一。菜单管理 SHALL 为 P-Tree + D-Form；角色管理 SHALL 为 P-List + D-Form + D-Panel；账号与审计 SHALL 为 P-List。

#### Scenario: 菜单管理页布局
- **WHEN** 管理员打开菜单管理
- **THEN** 呈现工具栏 + 树形表；表单弹窗按 type 联动字段，不提供 id 输入框

#### Scenario: 角色管理页布局
- **WHEN** 管理员打开角色管理并进入授权
- **THEN** 列表 + 表单弹窗 + 授权树弹窗（回显 menuIds，全量覆盖保存）

#### Scenario: 账号列表布局
- **WHEN** 管理员打开账号管理
- **THEN** P-List：工具栏筛选 + 表格 + 分页；无权限主操作按钮不渲染
