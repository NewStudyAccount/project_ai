-- 00_create_databases.sql
-- 建库脚本（在执行各 migration 之前于实例级执行一次）
-- 用法示例：
--   mysql -h <host> -u <user> -p < deploy/db/init/00_create_databases.sql
-- 说明：
--   1. 本脚本只建库；表结构见 migration/<db>/V*.sql
--   2. system_db 为其他业务库模板，按系统名再建 {system}_db 时复制本段改名
--   3. 字符集统一 utf8mb4；禁止把生产密码写入本文件

CREATE DATABASE IF NOT EXISTS `auth_db`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

CREATE DATABASE IF NOT EXISTS `file_db`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

CREATE DATABASE IF NOT EXISTS `blog_db`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

-- 模板库：其他业务系统按需复制改名（如 demo_db）
CREATE DATABASE IF NOT EXISTS `system_db`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;
