# Rollback V2 unified auth center tables
DROP TABLE IF EXISTS `oauth2_authorization`;
DROP TABLE IF EXISTS `auth_audit_log`;
DROP TABLE IF EXISTS `login_attempt`;
DROP TABLE IF EXISTS `auth_refresh_token`;
DROP TABLE IF EXISTS `auth_grant`;
DROP TABLE IF EXISTS `auth_session`;
DROP TABLE IF EXISTS `oauth_client`;
DROP TABLE IF EXISTS `sys_credential`;
