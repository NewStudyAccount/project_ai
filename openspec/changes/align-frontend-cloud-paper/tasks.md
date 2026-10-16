# Tasks · align-frontend-cloud-paper

> 实施顺序：先壳后页；每系统完成后跑 `npm run lint && npm run type-check`。不以测试栈为门禁（CLAUDE.md §2.2）。

## 1. Token 与全局样式

- [x] 1.1 以 `docs/template/tokens-cloud-paper.css` 与 `admin-ui-design.md` §1 为源，核对 token 清单（色板/圆角/阴影/EP 主色映射）
- [x] 1.2 更新 `frontend/user-admin/src/styles/main.css`：云白 `:root`、`.page-card`、`.toolbar`、`.status-pill`，删除旧 `#f5f7fa/#ebeef5` 体系
- [x] 1.3 更新 `frontend/auth-admin/src/styles/main.css`（同 1.2）
- [x] 1.4 更新 `frontend/blog-admin/src/styles/main.css`（同 1.2）
- [x] 1.5 更新 `frontend/auth-portal/src/styles/main.css`（登录页底色与卡片质感变量）

## 2. 管理端 App Shell

- [x] 2.1 更新 `frontend/user-admin/src/components/AppLayout.vue`：侧栏 240px 纸白 + 右缘细线 + 激活竖条/浅天蓝底；顶栏 56px；主内容 `--bg-page`
- [x] 2.2 更新 `frontend/auth-admin/src/components/AppLayout.vue`（同 2.1）
- [x] 2.3 更新 `frontend/blog-admin/src/components/AppLayout.vue`（同 2.1）
- [x] 2.4 抽查各系统 `Dashboard` / 列表页容器是否套 `.page-card`，主按钮是否胶囊 primary

## 3. 页面与组件质感抽查

- [x] 3.1 `user-admin`：MenuList / RoleList / UserList / AuditList — 状态软胶囊、弹窗圆角 12、行内 danger 链接
- [x] 3.2 `auth-admin`：Client / UserRole / Menu / Role / Audit / LoginAttempt / Grant 等页 — 同上
- [x] 3.3 `blog-admin`：现有列表页 — 同上
- [x] 3.4 确认无渐变、毛玻璃、重阴影、第三方彩色图标；无旧主色与云白混用

## 4. 登录门面

- [x] 4.1 更新 `auth-portal` 登录壳：页底 `--bg-page`、白卡片 380px / 圆角 12 / 轻阴影、主按钮胶囊
- [x] 4.2 确认 `*-admin` 内无账密表单（OIDC 跳转不变）

## 5. 自检与对照

- [x] 5.1 逐系统执行 `cd frontend/<system> && npm run lint && npm run type-check`，失败即修
- [x] 5.2 对照 `docs/template/admin-ui-style-final4.html` 中 **B 云白** 样张走查壳与列表页
- [x] 5.3 提交说明使用 `style(frontend): 对齐云白视觉基线` 等符合 §6.3 的范围化提交；确认无调试残留
