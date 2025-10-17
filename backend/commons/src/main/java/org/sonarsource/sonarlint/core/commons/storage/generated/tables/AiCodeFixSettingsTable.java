/*
 * Minimal stub of a jOOQ generated Table for AI_CODEFIX_SETTINGS.
 * This is only to make the project compile in environments where the jOOQ
 * code generation step is not executed. A normal Maven build should generate
 * a richer version of this class.
 */
package org.sonarsource.sonarlint.core.commons.storage.generated.tables;

import java.sql.Timestamp;
import org.jooq.Record;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;

public final class AiCodeFixSettingsTable extends TableImpl<Record> {
  private static final long serialVersionUID = 1L;

  public static final AiCodeFixSettingsTable AI_CODEFIX_SETTINGS = new AiCodeFixSettingsTable();

  public final TableField<Record, Integer> ID = createField(DSL.name("id"), SQLDataType.INTEGER.nullable(false), this, "");
  public final TableField<Record, String> SUPPORTED_RULES = createField(DSL.name("supported_rules"), SQLDataType.CLOB, this, "");
  public final TableField<Record, Boolean> ORGANIZATION_ELIGIBLE = createField(DSL.name("organization_eligible"), SQLDataType.BOOLEAN, this, "");
  public final TableField<Record, String> ENABLEMENT = createField(DSL.name("enablement"), SQLDataType.VARCHAR.length(64), this, "");
  public final TableField<Record, String> ENABLED_PROJECT_KEYS = createField(DSL.name("enabled_project_keys"), SQLDataType.CLOB, this, "");
  public final TableField<Record, Timestamp> UPDATED_AT = createField(DSL.name("updated_at"), SQLDataType.TIMESTAMP, this, "");

  private AiCodeFixSettingsTable() {
    super(DSL.name("AI_CODEFIX_SETTINGS"));
  }
}
