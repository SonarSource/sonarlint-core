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

CREATE TABLE IF NOT EXISTS KNOWN_FINDINGS (
    -- UUID
    id UUID NOT NULL PRIMARY KEY,
    configuration_scope_id VARCHAR(255) NOT NULL,
    ide_relative_file_path VARCHAR(255) NOT NULL,
    server_key VARCHAR(255),
    rule_key VARCHAR(255) NOT NULL,
    message VARCHAR(255) NOT NULL,
    introduction_date TIMESTAMP NOT NULL,
    finding_type VARCHAR(255) NOT NULL,
    -- TextRangeWithHash
    start_line INT,
    start_line_offset INT,
    end_line INT,
    end_line_offset INT,
    text_range_hash VARCHAR(255),
    -- LineWithHash
    line INT,
    line_hash VARCHAR(255)
);

