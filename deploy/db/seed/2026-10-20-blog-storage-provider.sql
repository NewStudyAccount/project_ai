-- blog_db · 对象存储服务商种子 + RBAC 菜单（blog-object-storage-multi-provider）
-- 对齐 docs/object-storage-design/§5.2/§8.2
-- 环境：仅测试/开发；生产执行前须按 CLAUDE.md §7 征询
-- 执行：USE blog_db 后人工执行；INSERT IGNORE 幂等
-- SK：明文 SK 禁止进 SQL；管理员用 SK 主密钥经管理端录入后保存为密文
--       部署步骤：先设环境变量 OBJECT_STORAGE_SK_KEY（Base64 的 32 字节），
--       再经管理端 PUT /api/v1/storage/providers/{id} 把 minio-local 的 SK 改为加密落库
--       （seed 阶段 secret_key 留空占位，引导服务商可先用 yml 兜底运行）

USE blog_db;

-- ============================================================
-- 1) 引导默认服务商 minio-local（与 application-local.yml 一致）
--    部署连接信息真相源：docs/test-env.md；prod 走 Nacos
-- ============================================================
INSERT IGNORE INTO sys_storage_provider
  (id, code, name, provider_type, endpoint, region, bucket, access_key, secret_key,
   path_style, public_base_url, status, is_default, description,
   create_time, update_time, create_by, update_by, deleted)
VALUES
  -- 16 位发号占位（部署时由管理端强制 create 或运维直接 UPDATE，避免与生产发号冲突）
  (2026102000000001, 'minio-local', '本地 MinIO（开发）', 'MINIO',
   'http://192.168.99.100:9000', '', 'blog', 'minio_admin', '',
   1, 'http://192.168.99.100:9000/blog', 1, 1, '引导默认服务商；SK 部署后经管理端加密录入',
   NOW(), NOW(), 0, 0, 0);

-- 注：minio_admin / minio123456 AK/SK 见 docs/test-env.md；
--     secret_key 留空字符串占位（应用运行时按需要求经管理端补录密文）。
--     若 DB 启用 SK 解密校验失败，应通过管理端重录 SK 触发加密落库。

-- ============================================================
-- 2) RBAC 菜单/按钮：对象存储服务商配置（挂系统管理目录 1001）
--    号段：菜单 d=2 200x；按钮 90000x；接口点 500x
-- ============================================================
INSERT IGNORE INTO sys_menu (id, parent_id, type, name, permission, path, component, icon, hidden, requires_auth, sort, status) VALUES
  -- 服务商配置页（d=2 菜单；挂在系统管理目录下）
  (2005, 1001, 2, '存储服务商', 'blog:storage-provider:list', '/storage/providers', 'views/storage/StorageProviderList.vue', '', 0, 1, 5, 1),
  -- 按钮（d=3）
  (90012, 2005, 3, '新建服务商', 'blog:storage-provider:create', '', '', '', 0, 1, 1, 1),
  (90013, 2005, 3, '编辑服务商', 'blog:storage-provider:update', '', '', '', 0, 1, 2, 1),
  (90014, 2005, 3, '删除服务商', 'blog:storage-provider:delete', '', '', '', 0, 1, 3, 1),
  (90015, 2005, 3, '测试连通', 'blog:storage-provider:test', '', '', '', 0, 1, 4, 1);

-- 3) admin 角色绑定新增菜单/按钮
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id) VALUES
  (6018, 9001, 2005),
  (6019, 9001, 90012),
  (6020, 9001, 90013),
  (6021, 9001, 90014),
  (6022, 9001, 90015);

-- 4) 幂等备注：扩展服务商/按钮续号另开 seed；修改已有用按 id 的 UPDATE；禁止改历史 id