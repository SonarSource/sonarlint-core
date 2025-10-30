-- Flyway migration: create AI_CODEFIX_SETTINGS table for H2
-- Initial schema includes per-connection scoping via connection_id
CREATE TABLE IF NOT EXISTS AI_CODEFIX_SETTINGS (
  id INT NOT NULL,
  connection_id VARCHAR(255),
  supported_rules VARCHAR(200) ARRAY,
  organization_eligible BOOLEAN,
  enablement VARCHAR(64),
  enabled_project_keys VARCHAR(400) ARRAY,
  updated_at TIMESTAMP,
  CONSTRAINT pk_ai_codefix_settings PRIMARY KEY (id)
);

-- Index to speed up lookups by connection_id
CREATE INDEX IF NOT EXISTS idx_ai_codefix_settings_connection_id
  ON AI_CODEFIX_SETTINGS (connection_id);


