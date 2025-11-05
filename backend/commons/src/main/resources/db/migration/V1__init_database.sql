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

CREATE TABLE IF NOT EXISTS SERVER_FINDINGS (
    id UUID,
    connection_id VARCHAR(255) NOT NULL,
    sonar_project_key VARCHAR(255) NOT NULL,
    server_key VARCHAR(255) NOT NULL PRIMARY KEY,
    rule_id VARCHAR(255),
    rule_key VARCHAR(255) NOT NULL,
    message VARCHAR(255) NOT NULL,
    file_path VARCHAR(4096) NOT NULL, -- default Linux path length limit
    creation_date TIMESTAMP NOT NULL,
    user_severity VARCHAR(255),
    rule_type VARCHAR(255),
    rule_description_context_key VARCHAR(255),
    clean_code_attribute VARCHAR(255),
    finding_type VARCHAR(255) NOT NULL,
    branch_name VARCHAR(255),
    vulnerability_probability VARCHAR(255),
    assignee VARCHAR(255),
    impacts JSON(1000),
    flows JSON(10000000),
    -- Resolution
    resolved BOOLEAN,
    issue_resolution_status VARCHAR(255),
    hotspot_review_status VARCHAR(255),
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

CREATE TABLE IF NOT EXISTS SERVER_BRANCHES (
    branch_name VARCHAR(255) NOT NULL,
    connection_id VARCHAR(255) NOT NULL,
    sonar_project_key VARCHAR(255) NOT NULL,
    last_issue_sync_ts TIMESTAMP,
    last_taint_sync_ts TIMESTAMP,
    last_hotspot_sync_ts TIMESTAMP,
    last_issue_enabled_langs JSON(10000),
    last_taint_enabled_langs JSON(10000),
    last_hotspot_enabled_langs JSON(10000),
    PRIMARY KEY (branch_name, connection_id, sonar_project_key)
);

CREATE TABLE IF NOT EXISTS SERVER_DEPENDENCY_RISKS (
    id UUID NOT NULL PRIMARY KEY,
    connection_id VARCHAR(255) NOT NULL,
    sonar_project_key VARCHAR(255) NOT NULL,
    branch_name VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    severity VARCHAR(255) NOT NULL,
    software_quality VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    package_name VARCHAR(255) NOT NULL,
    package_version VARCHAR(255) NOT NULL,
    vulnerability_id VARCHAR(255),
    cvss_score VARCHAR(255),
    transitions JSON(10000) NOT NULL
);
