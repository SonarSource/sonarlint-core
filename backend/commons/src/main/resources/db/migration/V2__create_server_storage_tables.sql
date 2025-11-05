-- Flyway migration: create server storage tables for H2
-- These tables migrate protobuf-based storage to H2 database
-- Used only in dogfood environment

CREATE TABLE IF NOT EXISTS PROJECTS (
  connection_id VARCHAR(255) NOT NULL,
  project_key VARCHAR(255) NOT NULL,
  last_smart_notification_poll_date TIMESTAMP,
  settings JSON,
  CONSTRAINT pk_projects PRIMARY KEY (connection_id, project_key)
);

CREATE TABLE IF NOT EXISTS ACTIVE_RULESETS (
  connection_id VARCHAR(255) NOT NULL,
  project_key VARCHAR(255) NOT NULL,
  language_key VARCHAR(50) NOT NULL,
  last_modified TIMESTAMP NOT NULL,
  rules JSON NOT NULL,
  CONSTRAINT pk_active_rulesets PRIMARY KEY (connection_id, project_key, language_key)
);

CREATE TABLE IF NOT EXISTS PROJECT_BRANCHES (
  connection_id VARCHAR(255) NOT NULL,
  project_key VARCHAR(255) NOT NULL,
  name VARCHAR(255) NOT NULL,
  is_default BOOLEAN NOT NULL,
  CONSTRAINT pk_project_branches PRIMARY KEY (connection_id, project_key, name)
);

CREATE TABLE IF NOT EXISTS PLUGINS (
  connection_id VARCHAR(255) NOT NULL,
  "key" VARCHAR(255) NOT NULL,
  hash VARCHAR(255) NOT NULL,
  filename VARCHAR(255) NOT NULL,
  CONSTRAINT pk_plugins PRIMARY KEY (connection_id, "key")
);

CREATE TABLE IF NOT EXISTS SERVERS (
  connection_id VARCHAR(255) NOT NULL PRIMARY KEY,
  version VARCHAR(50),
  id VARCHAR(255),
  global_settings JSON NOT NULL
);

CREATE TABLE IF NOT EXISTS SERVER_FEATURES (
  connection_id VARCHAR(255) NOT NULL PRIMARY KEY,
  features VARCHAR(200) ARRAY NOT NULL
);

CREATE TABLE IF NOT EXISTS ORGANIZATIONS (
  connection_id VARCHAR(255) NOT NULL PRIMARY KEY,
  id VARCHAR(255) NOT NULL,
  uuidv4 VARCHAR(36) NOT NULL
);

CREATE TABLE IF NOT EXISTS "USERS" (
  connection_id VARCHAR(255) NOT NULL PRIMARY KEY,
  id VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS NEW_CODE_DEFINITIONS (
  connection_id VARCHAR(255) NOT NULL,
  project_key VARCHAR(255) NOT NULL,
  mode VARCHAR(50) NOT NULL,
  days INT,
  threshold_date DATE,
  version VARCHAR(50),
  reference_branch VARCHAR(255),
  CONSTRAINT pk_new_code_definitions PRIMARY KEY (connection_id, project_key)
);

