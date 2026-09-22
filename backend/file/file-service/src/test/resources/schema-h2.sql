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

CREATE TABLE IF NOT EXISTS file_object (
  id BIGINT PRIMARY KEY,
  object_key VARCHAR(255) NOT NULL,
  bucket VARCHAR(64),
  url VARCHAR(512),
  content_type VARCHAR(128),
  size BIGINT,
  owner_id BIGINT,
  biz_type VARCHAR(64),
  biz_id VARCHAR(64),
  scene VARCHAR(64),
  create_time TIMESTAMP,
  update_time TIMESTAMP,
  create_by BIGINT,
  update_by BIGINT,
  deleted TINYINT DEFAULT 0
);
