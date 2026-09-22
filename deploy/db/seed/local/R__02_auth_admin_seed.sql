# 仅 local/test：测试管理员与部门
# 测试密码明文仅用于开发种子：admin / admin123（禁止用于生产）
# BCrypt(admin123)

INSERT INTO sys_dept (id, parent_id, ancestors, dept_name, dept_code, sort, status, leader_id, remark, create_time, update_time, create_by, update_by, deleted)
VALUES (26092200000001, 0, '0', '总部', 'HQ', 0, 1, 0, 'local seed', NOW(), NOW(), 0, 0, 0)
ON DUPLICATE KEY UPDATE dept_name = VALUES(dept_name);

INSERT INTO sys_user (id, username, password_hash, real_name, nickname, email, phone, avatar, dept_id, status, last_login_time, last_login_ip, pwd_update_time, remark, create_time, update_time, create_by, update_by, deleted)
VALUES (
  26092200000002,
  'admin',
  '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2',
  '本地管理员', 'admin', '', '', '', 26092200000001, 1, NULL, '', NULL, 'local seed only',
  NOW(), NOW(), 0, 0, 0
)
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name);

INSERT INTO sys_sequence (id, seq_name, seq_date, current_val, create_time, update_time, create_by, update_by, deleted)
VALUES (1, 'default', DATE_FORMAT(CURDATE(), '%Y%m%d'), 100, NOW(), NOW(), 0, 0, 0)
ON DUPLICATE KEY UPDATE current_val = GREATEST(current_val, 100);
