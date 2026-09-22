-- checks/after_migrate.sql
-- 建库后校验：无物理外键、无 AUTO_INCREMENT、无业务库密码列

-- 1) 不应存在外键
SELECT TABLE_NAME, CONSTRAINT_NAME
FROM information_schema.TABLE_CONSTRAINTS
WHERE CONSTRAINT_TYPE = 'FOREIGN KEY'
  AND TABLE_SCHEMA IN ('auth_db', DATABASE());

-- 2) 不应存在自增列
SELECT TABLE_NAME, COLUMN_NAME
FROM information_schema.COLUMNS
WHERE EXTRA LIKE '%auto_increment%'
  AND TABLE_SCHEMA IN ('auth_db', DATABASE());

-- 3) 业务库不应出现 password_hash（在 system 库执行）
SELECT TABLE_NAME, COLUMN_NAME
FROM information_schema.COLUMNS
WHERE COLUMN_NAME LIKE '%password%'
  AND TABLE_SCHEMA = DATABASE();
-- 期望：空结果（auth_db 执行时允许 sys_user.password_hash）

-- 4) 唯一键抽查
SHOW INDEX FROM sys_user WHERE Key_name = 'uk_sys_user_username';
SHOW INDEX FROM sys_role WHERE Key_name = 'uk_sys_role_system_code';
SHOW INDEX FROM sys_permission WHERE Key_name = 'uk_sys_permission_code';
SHOW INDEX FROM sys_user_role WHERE Key_name = 'uk_sys_user_role_user_role';
SHOW INDEX FROM sys_role_permission WHERE Key_name = 'uk_sys_role_permission_rp';
SHOW INDEX FROM sys_sequence WHERE Key_name = 'uk_sys_sequence_name_date';

-- 5) 唯一键拒绝重复（应用侧或手工）：
-- INSERT INTO sys_user (id, username, password_hash, ...) 两次同 username 应报错
