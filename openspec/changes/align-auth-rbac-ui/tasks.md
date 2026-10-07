## 1. 后端 · 菜单/角色短号分配

- [x] 1.1 在 `auth-common` `AuthErrorCodeEnum` 增加业务码：层级过深、本层/本段 id 用尽、父节点非法（`202016+` 递增，语义对齐 `rbac-design.md` §2.0 / §9）
- [x] 1.2 实现 `createMenu` 短号分配：type=1/2 按深度 `d×1000+n`（d≤4，本层 `max+1`）、type=3 `[90001,100000)`、type=4 `[5001,6000)`；与插入同一临界区；禁止客户端 id
- [x] 1.3 实现 `createRole` 段内短号 `9001–9999`（`max+1`）；关联表运行时保持 framework 发号
- [x] 1.4 补创建校验：parent 存在性、type=3/4 父必须 type=2 且 `parent_id≠0`、深度上限、号段用尽

## 2. 后端 · `/me/*` 运行时语义

- [x] 2.1 `/me/menus` 仅下发 `status=1` 的 type∈{1,2}；admin 全量逻辑保持
- [x] 2.2 非 admin 授权集做祖先闭包后组树，禁止孤叶子
- [x] 2.3 `/me/permissions` 非空 `permission` 去重；停用节点不进入权限集

## 3. 前端 · auth-admin 布局对齐

- [x] 3.1 按 `admin-ui-design.md` §1–§2 收敛 `main.css` / `AppLayout`（侧栏 220、顶栏 60、page-card、色板/字号）
- [x] 3.2 菜单管理页对齐 P-Tree + D-Form：type 字段联动、隐藏 id 输入、操作列权限钮
- [x] 3.3 角色管理页对齐 P-List + D-Form + D-Panel：授权树回显与全量覆盖保存
- [x] 3.4 确认 401 跳转登录门面、无内嵌账密框、无角色名硬编码

## 4. 自检

- [x] 4.1 `cd backend/auth && mvn -q compile` 通过
- [x] 4.2 `cd frontend/auth-admin && npm run lint && npm run type-check` 通过
- [ ] 4.3 冒烟：新建目录/菜单/按钮 id 形态正确；非法挂载/过深/号段满返回业务码；`/me/menus` 不含停用节点且可导航
