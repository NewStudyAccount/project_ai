-- U1__auth_db_foundation_down.sql
-- 回滚 V1__auth_db_foundation.sql（按依赖逆序）
-- 警告：DROP 不可恢复；仅用于开发/测试回滚演练

DROP TABLE IF EXISTS `sys_sso_client`;
DROP TABLE IF EXISTS `sys_login_log`;
DROP TABLE IF EXISTS `sys_user`;
DROP TABLE IF EXISTS `sys_dept`;
DROP TABLE IF EXISTS `sys_sequence`;
