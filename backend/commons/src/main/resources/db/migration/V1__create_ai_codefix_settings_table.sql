-- Flyway migration: create AI_CODEFIX_SETTINGS table for H2
-- Initial schema includes per-connection scoping via connection_id
CREATE TABLE IF NOT EXISTS AI_CODEFIX_SETTINGS (
  connection_id VARCHAR(255) NOT NULL PRIMARY KEY,
  supported_rules VARCHAR(200) ARRAY,
  organization_eligible BOOLEAN,
  enablement VARCHAR(64),
  enabled_project_keys VARCHAR(400) ARRAY,
  CONSTRAINT pk_ai_codefix_settings PRIMARY KEY (connection_id)
);


