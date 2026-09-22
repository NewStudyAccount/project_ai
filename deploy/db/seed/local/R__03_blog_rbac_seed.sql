-- R__03_blog_rbac_seed.sql
-- 仅 local/test：blog 内置角色与权限码。禁止生产执行。
-- 日期常量用固定值便于重跑；id 用可重复的固定 14 位风格值

-- 权限码（与 BlogPermissions 对齐）
INSERT IGNORE INTO `sys_permission` (
  `id`,`system_code`,`parent_id`,`permission_type`,`permission_code`,`permission_name`,
  `path`,`component`,`icon`,`sort`,`visible`,`status`,`method`,`api_path`,`permission_str`,`remark`,
  `create_time`,`update_time`,`create_by`,`update_by`,`deleted`
) VALUES
 (26092200000001,'blog',0,3,'blog:post:create','创建文章','','','',10,1,1,'POST','/api/v1/posts','blog:post:create','',NOW(),NOW(),0,0,0),
 (26092200000002,'blog',0,3,'blog:post:publish','发布文章','','','',11,1,1,'PUT','/api/v1/posts/{id}/publish','blog:post:publish','',NOW(),NOW(),0,0,0),
 (26092200000003,'blog',0,3,'blog:post:manage','管理文章','','','',12,1,1,'PUT','/api/v1/posts/**','blog:post:manage','',NOW(),NOW(),0,0,0),
 (26092200000004,'blog',0,3,'blog:category:manage','分类管理','','','',20,1,1,'POST','/api/v1/categories','blog:category:manage','',NOW(),NOW(),0,0,0),
 (26092200000005,'blog',0,3,'blog:tag:manage','标签管理','','','',21,1,1,'POST','/api/v1/tags','blog:tag:manage','',NOW(),NOW(),0,0,0),
 (26092200000006,'blog',0,3,'blog:comment:moderate','评论审核','','','',30,1,1,'PUT','/api/v1/comments/**','blog:comment:moderate','',NOW(),NOW(),0,0,0),
 (26092200000007,'blog',0,3,'blog:rbac:role','角色权限管理','','','',40,1,1,'POST','/api/v1/rbac/**','blog:rbac:role','',NOW(),NOW(),0,0,0),
 (26092200000008,'blog',0,3,'blog:rbac:user-role','用户授权','','','',41,1,1,'POST','/api/v1/rbac/user-roles','blog:rbac:user-role','',NOW(),NOW(),0,0,0);

-- 内置角色
INSERT IGNORE INTO `sys_role` (
  `id`,`system_code`,`role_code`,`role_name`,`sort`,`status`,`data_scope`,`remark`,
  `create_time`,`update_time`,`create_by`,`update_by`,`deleted`
) VALUES
 (26092200000101,'blog','blog_admin','博客管理员',1,1,1,'内置',NOW(),NOW(),0,0,0),
 (26092200000102,'blog','blog_author','博客作者',2,1,4,'内置',NOW(),NOW(),0,0,0);

-- blog_admin 拥有全部权限
INSERT IGNORE INTO `sys_role_permission` (`id`,`role_id`,`permission_id`,`system_code`,`create_time`,`create_by`,`deleted`) VALUES
 (26092200000201,26092200000101,26092200000001,'blog',NOW(),0,0),
 (26092200000202,26092200000101,26092200000002,'blog',NOW(),0,0),
 (26092200000203,26092200000101,26092200000003,'blog',NOW(),0,0),
 (26092200000204,26092200000101,26092200000004,'blog',NOW(),0,0),
 (26092200000205,26092200000101,26092200000005,'blog',NOW(),0,0),
 (26092200000206,26092200000101,26092200000006,'blog',NOW(),0,0),
 (26092200000207,26092200000101,26092200000007,'blog',NOW(),0,0),
 (26092200000208,26092200000101,26092200000008,'blog',NOW(),0,0);

-- blog_author：发文/发标签相关
INSERT IGNORE INTO `sys_role_permission` (`id`,`role_id`,`permission_id`,`system_code`,`create_time`,`create_by`,`deleted`) VALUES
 (26092200000211,26092200000102,26092200000001,'blog',NOW(),0,0),
 (26092200000212,26092200000102,26092200000002,'blog',NOW(),0,0);
