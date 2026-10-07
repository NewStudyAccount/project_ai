## 1. 文档与错误码准备

- [x] 1.1 确认 `docs/error-code-ranges.md` 已登记系统号 01（user，业务码 201xxx）；缺失则补登记（仅文档）
- [x] 1.2 在 `UserErrorCodeEnum` 增加业务码：层级过深、本层/本段 id 用尽、父节点非法（`201014+` 递增）

## 2. 后端 · 菜单/角色短号分配

- [x] 2.1 实现 `createMenu` 短号分配：type=1/2 按深度 `d×1000+n`（d≤4，本层 `max+1`）、type=3 `[90001,100000)`、type=4 `[5001,6000)`；与插入同一临界区；禁止客户端 id
- [x] 2.2 实现 `createRole` 段内短号 `9001–9999`（`max+1`）；关联表运行时保持 framework 发号
- [x] 2.3 补创建校验：parent 存在性、type=3/4 父必须 type=2 且 `parent_id≠0`、深度上限、号段用尽

## 3. 后端 · `/me/*` 运行时语义

- [x] 3.1 admin 全量逻辑保持；非 admin 查询后过滤 `status=1`
- [x] 3.2 非 admin 授权集做祖先闭包后组树，禁止孤叶子
- [x] 3.3 `/me/permissions` 非空 `permission` 去重；停用节点不进入权限集

## 4. 前端 · user-admin 布局对齐

- [x] 4.1 按 `admin-ui-design.md` §1–§2 收敛 `main.css` / `AppLayout`（侧栏 220、顶栏 60、page-card、色板/字号）
- [x] 4.2 菜单管理页对齐 P-Tree + D-Form：type 字段联动、隐藏 id 输入、操作列权限钮
- [x] 4.3 角色管理页对齐 P-List + D-Form + D-Panel：授权树回显与全量覆盖保存
- [x] 4.4 账号/审计保持 P-List；筛选/分页/空态符合 `admin-ui-design.md`
- [x] 4.5 确认按钮仅 `hasPermission` 显隐、无角色名硬编码

## 5. 自检

- [x] 5.1 `cd backend/user && mvn -q compile` 通过
- [x] 5.2 `cd frontend/user-admin && npm run lint && npm run type-check` 通过
- [ ] 5.3 冒烟：短号形态、非法挂载拒绝、非 admin `/me` 不含停用节点且可导航、admin 新建菜单立即可见
