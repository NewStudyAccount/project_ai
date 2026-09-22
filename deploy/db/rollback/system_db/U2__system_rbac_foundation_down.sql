-- U2__system_rbac_foundation_down.sql
-- 回滚 V2__system_rbac_foundation.sql

DROP TABLE IF EXISTS `sys_user_ref`;
DROP TABLE IF EXISTS `sys_role_permission`;
DROP TABLE IF EXISTS `sys_user_role`;
DROP TABLE IF EXISTS `sys_permission`;
DROP TABLE IF EXISTS `sys_role`;
DROP TABLE IF EXISTS `sys_sequence`;
