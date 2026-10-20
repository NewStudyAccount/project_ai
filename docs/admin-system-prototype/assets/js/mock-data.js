/**
 * 原型 Mock 数据（仅演示用，非真实数据）
 * 约定对齐 CLAUDE.md：
 *  - 主键 id 一律字符串（Long 序列化契约），发号 16 位 = yyyyMMdd + 8 位序列
 *  - RBAC 四表（sys_menu/sys_role/...）按 rbac-design.md §2.0 用段内短号
 *  - 权限标识 `system:resource:action` 全小写
 *  - 枚举值以后端为准（此处仅模拟常见取值）
 */
window.MOCK = (function () {
  /* ---------- 账号（sys_user） ---------- */
  const users = [
    { id: '2026101200000101', username: 'zhangwei', name: '张伟', dept: '平台研发部', role: '超级管理员', roleName: 'admin', phone: '138****2031', status: 1, createTime: '2026-10-12 09:32:11', lastLogin: '2026-10-20 08:57:03' },
    { id: '2026101200000102', username: 'liufang', name: '刘芳', dept: '平台研发部', role: '运维工程师', roleName: 'ops', phone: '139****8842', status: 1, createTime: '2026-10-12 09:45:26', lastLogin: '2026-10-20 09:12:47' },
    { id: '2026101300000103', username: 'chenjing', name: '陈静', dept: '质量管理部', role: '审计员', roleName: 'auditor', phone: '137****1190', status: 1, createTime: '2026-10-13 14:02:38', lastLogin: '2026-10-19 17:40:22' },
    { id: '2026101300000104', username: 'wanglei', name: '王磊', dept: '内容运营部', role: '内容管理员', roleName: 'content-admin', phone: '135****6621', status: 1, createTime: '2026-10-13 15:28:52', lastLogin: '2026-10-20 10:03:15' },
    { id: '2026101400000105', username: 'zhaomin', name: '赵敏', dept: '内容运营部', role: '内容编辑', roleName: 'editor', phone: '186****3307', status: 1, createTime: '2026-10-14 10:11:09', lastLogin: '2026-10-20 11:26:58' },
    { id: '2026101400000106', username: 'sunhao', name: '孙浩', dept: '平台研发部', role: '运维工程师', roleName: 'ops', phone: '188****7754', status: 0, createTime: '2026-10-14 16:37:44', lastLogin: '2026-10-16 18:02:19' },
    { id: '2026101500000107', username: 'zhouxue', name: '周雪', dept: '人力资源部', role: '访客', roleName: 'guest', phone: '150****4428', status: 1, createTime: '2026-10-15 09:05:33', lastLogin: '2026-10-18 13:48:26' },
    { id: '2026101600000108', username: 'wudi', name: '吴迪', dept: '平台研发部', role: '内容管理员', roleName: 'content-admin', phone: '159****9012', status: 1, createTime: '2026-10-16 11:52:20', lastLogin: '2026-10-20 09:44:37' },
    { id: '2026101600000109', username: 'zhengshuang', name: '郑爽', dept: '财务管理部', role: '审计员', roleName: 'auditor', phone: '187****2265', status: 1, createTime: '2026-10-16 14:19:05', lastLogin: '2026-10-19 16:22:41' },
    { id: '2026101700000110', username: 'mayun', name: '马芸', dept: '内容运营部', role: '内容编辑', roleName: 'editor', phone: '133****8873', status: 0, createTime: '2026-10-17 08:41:17', lastLogin: '2026-10-17 19:35:52' },
    { id: '2026101800000111', username: 'huangbo', name: '黄博', dept: '平台研发部', role: '运维工程师', roleName: 'ops', phone: '176****5540', status: 1, createTime: '2026-10-18 10:26:48', lastLogin: '2026-10-20 08:31:12' },
    { id: '2026101900000112', username: 'linxi', name: '林曦', dept: '市场运营部', role: '访客', roleName: 'guest', phone: '132****6608', status: 1, createTime: '2026-10-19 15:08:59', lastLogin: '2026-10-20 10:52:26' }
  ];

  /* ---------- 角色（sys_role，段内短号） ---------- */
  const roles = [
    { id: '1001', code: 'admin', name: '超级管理员', dataScope: '全部数据', userCount: 1, status: 1, remark: '系统内置角色，拥有全部菜单与按钮权限', createTime: '2026-10-12 09:00:00' },
    { id: '1002', code: 'ops', name: '运维工程师', dataScope: '全部数据', userCount: 3, status: 1, remark: '负责系统配置、Client 与运行维护', createTime: '2026-10-12 09:10:22' },
    { id: '1003', code: 'auditor', name: '审计员', dataScope: '全部数据', userCount: 2, status: 1, remark: '只读审计与日志查询，不开放写操作', createTime: '2026-10-13 11:24:36' },
    { id: '1004', code: 'content-admin', name: '内容管理员', dataScope: '本部门及以下', userCount: 2, status: 1, remark: '内容域管理权限（文章/分类/文件）', createTime: '2026-10-13 15:40:18' },
    { id: '1005', code: 'editor', name: '内容编辑', dataScope: '仅本人', userCount: 2, status: 1, remark: '内容创作与投稿', createTime: '2026-10-14 10:30:45' },
    { id: '1006', code: 'guest', name: '访客', dataScope: '仅本人', userCount: 2, status: 0, remark: '默认只读角色，已停用', createTime: '2026-10-15 09:15:02' }
  ];

  /* ---------- 菜单（sys_menu，type: 1 目录 / 2 菜单 / 3 按钮 / 4 接口点） ---------- */
  const menus = [
    {
      id: '10', name: '系统管理', type: 1, permission: '', path: '/system', component: '', sort: 1, status: 1,
      children: [
        {
          id: '1001', name: '账号管理', type: 2, permission: 'system:user:list', path: '/system/user', component: 'views/system/user/index', sort: 1, status: 1,
          children: [
            { id: '100101', name: '新建账号', type: 3, permission: 'system:user:create', path: '', component: '', sort: 1, status: 1, children: [] },
            { id: '100102', name: '编辑账号', type: 3, permission: 'system:user:update', path: '', component: '', sort: 2, status: 1, children: [] },
            { id: '100103', name: '删除账号', type: 3, permission: 'system:user:delete', path: '', component: '', sort: 3, status: 1, children: [] },
            { id: '100104', name: '账号分页查询', type: 4, permission: 'system:user:page', path: '', component: '', sort: 4, status: 1, children: [] }
          ]
        },
        {
          id: '1002', name: '角色管理', type: 2, permission: 'system:role:list', path: '/system/role', component: 'views/system/role/index', sort: 2, status: 1,
          children: [
            { id: '100201', name: '新建角色', type: 3, permission: 'system:role:create', path: '', component: '', sort: 1, status: 1, children: [] },
            { id: '100202', name: '角色授权', type: 3, permission: 'system:role:assign', path: '', component: '', sort: 2, status: 1, children: [] },
            { id: '100203', name: '删除角色', type: 3, permission: 'system:role:delete', path: '', component: '', sort: 3, status: 0, children: [] }
          ]
        },
        {
          id: '1003', name: '菜单管理', type: 2, permission: 'system:menu:list', path: '/system/menu', component: 'views/system/menu/index', sort: 3, status: 1,
          children: [
            { id: '100301', name: '新建菜单', type: 3, permission: 'system:menu:create', path: '', component: '', sort: 1, status: 1, children: [] },
            { id: '100302', name: '删除菜单', type: 3, permission: 'system:menu:delete', path: '', component: '', sort: 2, status: 1, children: [] }
          ]
        }
      ]
    },
    {
      id: '20', name: '审计中心', type: 1, permission: '', path: '/audit', component: '', sort: 2, status: 1,
      children: [
        {
          id: '2001', name: '操作审计', type: 2, permission: 'audit:log:list', path: '/audit/log', component: 'views/audit/log/index', sort: 1, status: 1,
          children: [
            { id: '200101', name: '审计详情', type: 3, permission: 'audit:log:detail', path: '', component: '', sort: 1, status: 1, children: [] }
          ]
        }
      ]
    },
    {
      id: '30', name: '认证中心', type: 1, permission: '', path: '/auth', component: '', sort: 3, status: 1,
      children: [
        {
          id: '3001', name: 'Client 管理', type: 2, permission: 'auth:client:list', path: '/auth/client', component: 'views/auth/client/index', sort: 1, status: 1,
          children: [
            { id: '300101', name: '新建 Client', type: 3, permission: 'auth:client:create', path: '', component: '', sort: 1, status: 1, children: [] },
            { id: '300102', name: '查看密钥', type: 3, permission: 'auth:client:secret', path: '', component: '', sort: 2, status: 1, children: [] }
          ]
        }
      ]
    }
  ];

  /* ---------- 操作审计（audit_log） ---------- */
  const audits = [
    { id: '2026102000000901', time: '2026-10-20 11:26:58', operator: 'zhaomin', module: '内容管理', action: '更新文章', ip: '10.20.3.51', result: 1, detail: '更新文章《云白设计规范落地要点》（articleId=2026101800000521）' },
    { id: '2026102000000902', time: '2026-10-20 11:02:41', operator: 'liufang', module: '认证中心', action: '轮换 Client 密钥', ip: '10.20.3.12', result: 1, detail: 'Client「blog-admin」密钥轮换，旧密钥进入 24 小时宽限期' },
    { id: '2026102000000903', time: '2026-10-20 10:52:26', operator: 'linxi', module: '认证中心', action: '登录', ip: '10.20.7.88', result: 1, detail: 'OIDC 授权码模式登录成功' },
    { id: '2026102000000904', time: '2026-10-20 10:41:07', operator: 'unknown', module: '认证中心', action: '登录', ip: '203.0.113.42', result: 0, detail: '密码连续错误 5 次，账号锁定 15 分钟（用户名=hacker01）' },
    { id: '2026102000000905', time: '2026-10-20 10:03:15', operator: 'wanglei', module: '内容管理', action: '发布文章', ip: '10.20.3.57', result: 1, detail: '发布文章《对象存储多服务商适配方案》' },
    { id: '2026102000000906', time: '2026-10-20 09:44:37', operator: 'wudi', module: '文件管理', action: '上传文件', ip: '10.20.3.61', result: 1, detail: '上传 objectKey=blog/2026/10/cover-8f2c.png（2.3MB）' },
    { id: '2026102000000907', time: '2026-10-20 09:12:47', operator: 'liufang', module: '系统管理', action: '更新角色授权', ip: '10.20.3.12', result: 1, detail: '角色「ops」新增菜单权限 audit:log:list' },
    { id: '2026102000000908', time: '2026-10-20 08:57:03', operator: 'zhangwei', module: '认证中心', action: '登录', ip: '10.20.3.4', result: 1, detail: 'OIDC 授权码模式登录成功' },
    { id: '2026101900000909', time: '2026-10-19 17:40:22', operator: 'chenjing', module: '审计中心', action: '导出审计记录', ip: '10.20.5.23', result: 1, detail: '导出 2026-10-01 ~ 2026-10-19 审计记录（812 条）' },
    { id: '2026101900000910', time: '2026-10-19 16:22:41', operator: 'zhengshuang', module: '审计中心', action: '查询审计记录', ip: '10.20.5.31', result: 1, detail: '按模块=认证中心、结果=失败筛选' },
    { id: '2026101900000911', time: '2026-10-19 15:18:36', operator: 'sunhao', module: '系统管理', action: '停用账号', ip: '10.20.3.19', result: 1, detail: '停用账号 mayun（id=2026101700000110）' },
    { id: '2026101900000912', time: '2026-10-19 14:02:11', operator: 'unknown', module: '认证中心', action: '刷新令牌', ip: '198.51.100.7', result: 0, detail: 'refresh_token 已过期（7 天轮转期外）' },
    { id: '2026101900000913', time: '2026-10-19 11:35:52', operator: 'zhangwei', module: '系统管理', action: '新建菜单', ip: '10.20.3.4', result: 1, detail: '新建按钮权限 auth:client:secret（menuId=300102）' },
    { id: '2026101800000914', time: '2026-10-18 13:48:26', operator: 'zhouxue', module: '认证中心', action: '登录', ip: '10.20.8.14', result: 1, detail: 'OIDC 授权码模式登录成功' },
    { id: '2026101800000915', time: '2026-10-18 10:26:48', operator: 'huangbo', module: '系统管理', action: '重置密码', ip: '10.20.3.66', result: 1, detail: '重置账号 linxi 的登录密码（触发型重置）' }
  ];

  /* ---------- OAuth2 Client（auth_client） ---------- */
  const clients = [
    { id: '2026101000000201', clientId: 'auth-admin', name: '认证中心管理端', grantTypes: 'authorization_code, refresh_token', scope: 'openid profile', redirectUri: 'https://admin.example.com/callback', status: 1, secret: '', createTime: '2026-10-10 10:00:00' },
    { id: '2026101000000202', clientId: 'user-admin', name: '用户中心管理端', grantTypes: 'authorization_code, refresh_token', scope: 'openid profile', redirectUri: 'https://user.example.com/callback', status: 1, secret: '', createTime: '2026-10-10 10:05:12' },
    { id: '2026101100000203', clientId: 'blog-admin', name: '博客管理端', grantTypes: 'authorization_code, refresh_token', scope: 'openid profile blog.read blog.write', redirectUri: 'https://blog.example.com/callback', status: 1, secret: '', createTime: '2026-10-11 09:22:47' },
    { id: '2026101200000204', clientId: 'ci-pipeline', name: 'CI 流水线', grantTypes: 'client_credentials', scope: 'deploy.trigger', redirectUri: '', status: 1, secret: '', createTime: '2026-10-12 14:48:30' },
    { id: '2026101300000205', clientId: 'legacy-report', name: '旧报表系统', grantTypes: 'password', scope: 'report.read', redirectUri: '', status: 0, secret: '', createTime: '2026-10-13 16:31:09' }
  ];

  /* ---------- 首页统计 ---------- */
  const stats = [
    { label: '账号总数', value: '128', trend: '较上周 +12', down: false },
    { label: '启用角色', value: '5', trend: '较上周 +1', down: false },
    { label: '今日操作', value: '231', trend: '较昨日 +18%', down: false },
    { label: '异常登录', value: '2', trend: '较昨日 -3', down: true }
  ];

  return { users, roles, menus, audits, clients, stats };
})();
