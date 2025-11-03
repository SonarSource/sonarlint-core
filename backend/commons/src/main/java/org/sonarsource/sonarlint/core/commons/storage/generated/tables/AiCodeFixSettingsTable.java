/*
 * SonarLint Core - Commons
 * Copyright (C) 2016-2025 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
/*
 * Minimal stub of a jOOQ generated Table for AI_CODEFIX_SETTINGS.
 * This is only to make the project compile in environments where the jOOQ
 * code generation step is not executed. A normal Maven build should generate
 * a richer version of this class.
 */
package org.sonarsource.sonarlint.core.commons.storage.generated.tables;

import org.jooq.Record;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;

public final class AiCodeFixSettingsTable extends TableImpl<Record> {
  private static final long serialVersionUID = 1L;

  public static final AiCodeFixSettingsTable AI_CODEFIX_SETTINGS = new AiCodeFixSettingsTable();

  public final TableField<Record, String> CONNECTION_ID = createField(DSL.name("CONNECTION_ID"), SQLDataType.VARCHAR.length(255), this, "");
  public final TableField<Record, String[]> SUPPORTED_RULES = createField(DSL.name("SUPPORTED_RULES"), SQLDataType.VARCHAR(64).getArrayDataType(), this, "");
  public final TableField<Record, Boolean> ORGANIZATION_ELIGIBLE = createField(DSL.name("ORGANIZATION_ELIGIBLE"), SQLDataType.BOOLEAN, this, "");
  public final TableField<Record, String> ENABLEMENT = createField(DSL.name("ENABLEMENT"), SQLDataType.VARCHAR.length(64), this, "");
  public final TableField<Record, String[]> ENABLED_PROJECT_KEYS = createField(DSL.name("ENABLED_PROJECT_KEYS"), SQLDataType.VARCHAR(400).getArrayDataType(), this, "");

  private AiCodeFixSettingsTable() {
    super(DSL.name("AI_CODEFIX_SETTINGS"));
  }
}
