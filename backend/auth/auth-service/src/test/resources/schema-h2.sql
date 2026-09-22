CREATE TABLE IF NOT EXISTS sys_sequence (
  id BIGINT PRIMARY KEY,
  seq_name VARCHAR(64) NOT NULL,
  seq_date CHAR(8) NOT NULL,
  current_val BIGINT NOT NULL DEFAULT 0,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  create_by BIGINT DEFAULT 0,
  update_by BIGINT DEFAULT 0,
  deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  real_name VARCHAR(64),
  nickname VARCHAR(64),
  email VARCHAR(128),
  phone VARCHAR(20),
  avatar VARCHAR(255),
  dept_id BIGINT,
  status TINYINT,
  last_login_time TIMESTAMP,
  last_login_ip VARCHAR(64),
  pwd_update_time TIMESTAMP,
  remark VARCHAR(255),
  create_time TIMESTAMP,
  update_time TIMESTAMP,
  create_by BIGINT,
  update_by BIGINT,
  deleted TINYINT DEFAULT 0
);
