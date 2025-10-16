-- Flyway migration: create AI_CODEFIX_SETTINGS table with quoted, case-sensitive identifiers
CREATE TABLE IF NOT EXISTS "AI_CODEFIX_SETTINGS" (
  "id" INTEGER NOT NULL,
  "supported_rules" CLOB,
  "organization_eligible" BOOLEAN,
  "enablement" VARCHAR(64),
  "enabled_project_keys" CLOB,
  "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_ai_codefix_settings PRIMARY KEY ("id")
);


