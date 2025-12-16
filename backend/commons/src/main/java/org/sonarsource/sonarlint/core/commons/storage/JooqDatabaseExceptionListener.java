/*
 * SonarLint Core - Commons
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.commons.storage;

import org.jooq.ExecuteContext;
import org.jooq.impl.DefaultExecuteListener;

/**
 * A jOOQ ExecuteListener that intercepts SQL execution exceptions and reports them
 * to Sentry via {@link DatabaseExceptionReporter}.
 */
public class JooqDatabaseExceptionListener extends DefaultExecuteListener {

  @Override
  public void exception(ExecuteContext ctx) {
    var exception = ctx.exception();
    if (exception == null) {
      return;
    }

    var sqlException = ctx.sqlException();
    var exceptionToReport = sqlException != null ? sqlException : exception;
    var sql = ctx.sql();

    DatabaseExceptionReporter.capture(exceptionToReport, "runtime", "jooq.execute", sql);
  }
}
