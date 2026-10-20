# 后台管理系统原型（HTML · Mock 数据）

> 定位：**交互/视觉原型样张**，纯静态 HTML + 原生 JS，零构建、零依赖，双击 `index.html` 即可预览。
> 视觉基线：「云白 Cloud Paper」——严格按 [`../template/admin-ui-design.md`](../template/admin-ui-design.md)（色板/字体/间距/壳布局/四类页面模式）实现，Design Tokens 与 [`../template/tokens-cloud-paper.css`](../template/tokens-cloud-paper.css) 一致，**未新增任何色值**。
> 工程契约：交互/权限/字段契约见 [`../template/generic-admin-page-design.md`](../template/generic-admin-page-design.md) 与 [`../template/rbac-design.md`](../template/rbac-design.md)；**本目录仅为样张，不作为工程契约**。
> 正式实现落点：`frontend/{system}-admin`（Vue3 + Element Plus），本原型用于方案沟通与评审。

## 页面清单

| 页面 | 模式 | 覆盖内容 |
|------|------|----------|
| `login.html` | 登录门面（§4.5） | 居中白卡片 380px、全浅底；对应 `auth-portal` |
| `index.html` | Dashboard（§4.1） | 系统说明卡、统计卡片、快捷入口、最近操作 |
| `user-list.html` | P-List（§3.1）+ D-Form | 筛选（关键词/角色/状态/时间范围）、分页、新建/编辑弹窗（必填校验、编辑锁用户名）、重置密码、删除二次确认 |
| `role-list.html` | P-List + D-Form + D-Panel（§3.4） | 角色 CRUD、数据范围、**授权树弹窗**（父子复选联动） |
| `menu-list.html` | P-Tree（§3.2）+ D-Form | 树形表（展开/收起、层级缩进）、类型 1 目录/2 菜单/3 按钮/4 接口点、**字段联动**（§4.2 权限/路由/组件显隐与必填）、仅叶子可删 |
| `audit-log.html` | P-List（审计形态） | 操作人/模块/结果/时间范围筛选、详情弹窗（含 TraceId 示意） |
| `client-list.html` | P-List + D-Panel | OAuth2/OIDC Client 登记、**密钥查看/轮换**（默认打码、可复制） |

## 目录结构

```text
admin-system-prototype/
├── index.html            首页
├── login.html            登录
├── user-list.html        账号管理
├── role-list.html        角色管理
├── menu-list.html        菜单管理
├── audit-log.html        操作审计
├── client-list.html      Client 管理
└── assets/
    ├── css/proto.css     云白 token + 壳/组件样式（唯一样式源）
    └── js/
        ├── app.js        应用壳（侧栏/顶栏）、弹窗、Toast、二次确认、分页
        └── mock-data.js  Mock 数据（账号/角色/菜单树/审计/Client）
```

## Mock 数据约定

- **全部为演示数据**，非真实业务数据；页面右上角有「原型演示」标识。
- 主键 `id` 一律字符串，16 位发号示意（`yyyyMMdd` + 8 位序列），对齐 `CLAUDE.md` §6.4.5/§6.4.7 Long 序列化契约。
- RBAC 四表主键用段内短号（`rbac-design.md` §2.0），如角色 `1001`、菜单 `100101`。
- 权限标识格式 `system:resource:action` 全小写；菜单树中的按钮/接口点权限串与前端按钮渲染使用同一字符串。
- 枚举示意值（状态/数据范围/授权模式等）以后端真实枚举为准，前端不自造。

## 交互说明

- 表格分页 `current` 从 1 起；筛选变更重置回第 1 页；删除有二次确认（danger 文案）。
- 表单校验失败在字段下方红字提示，不弹窗打断；保存按钮成功后 Toast ≤ 2s 并刷新当前页。
- 菜单表单字段随「类型」联动（对齐 `admin-ui-design.md` §4.2）：目录隐藏权限标识；按钮/接口点必填权限标识且无路由/组件；菜单必填路由与组件。
- 菜单删除仅限叶子节点，存在子节点时 Toast 拒绝（对齐 §3.2）。
- 无权限按钮在真实实现中**整钮不渲染**（`hasPermission`）；原型展示全量按钮用于评审。
