-- Flyway migration: create LOCAL_ONLY_ISSUES table for H2
-- This table stores local-only issues (issues detected locally but not yet on the server)

CREATE TABLE IF NOT EXISTS LOCAL_ONLY_ISSUES (
  id UUID NOT NULL PRIMARY KEY,
  configuration_scope_id VARCHAR(255) NOT NULL,
  server_relative_path VARCHAR(1000) NOT NULL,
  rule_key VARCHAR(255) NOT NULL,
  message VARCHAR(255) NOT NULL,
  -- Resolution fields (nullable when issue is not resolved)
  resolution_status VARCHAR(50),
  resolution_date TIMESTAMP,
  comment VARCHAR(255),
  -- TextRangeWithHash fields (nullable)
  start_line INT,
  start_line_offset INT,
  end_line INT,
  end_line_offset INT,
  text_range_hash VARCHAR(255),
  -- LineWithHash fields (nullable)
  line INT,
  line_hash VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_local_only_issues_config_scope_file
    ON LOCAL_ONLY_ISSUES(configuration_scope_id, server_relative_path);

CREATE INDEX IF NOT EXISTS idx_local_only_issues_resolution_date
    ON LOCAL_ONLY_ISSUES(resolution_date);

