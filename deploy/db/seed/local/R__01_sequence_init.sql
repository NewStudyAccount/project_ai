-- R__01_sequence_init.sql
-- 仅 local / test 执行；禁止在 prod 执行
-- 用途：发号行初始化（当日取号前 upsert 一行即可）

-- auth_db
INSERT INTO `sys_sequence` (`id`, `seq_name`, `seq_date`, `current_val`, `create_time`, `update_time`, `create_by`, `update_by`, `deleted`)
VALUES (1, 'default', DATE_FORMAT(CURDATE(), '%Y%m%d'), 0, NOW(), NOW(), 0, 0, 0)
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- {system}_db 在对应库执行相同语句
