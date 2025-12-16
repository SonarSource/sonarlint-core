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

import io.sentry.Sentry;
import io.sentry.ScopeCallback;
import io.sentry.logger.ILoggerApi;
import java.sql.SQLException;
import org.jooq.ExecuteContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class JooqDatabaseExceptionListenerTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private JooqDatabaseExceptionListener listener;
  private MockedStatic<Sentry> sentryMock;

  @BeforeEach
  void setUp() {
    listener = new JooqDatabaseExceptionListener();
    DatabaseExceptionReporter.clearRecentExceptions();
    sentryMock = mockStatic(Sentry.class);
    sentryMock.when(Sentry::logger).thenReturn(mock(ILoggerApi.class));
  }

  @AfterEach
  void tearDown() {
    sentryMock.close();
    DatabaseExceptionReporter.clearRecentExceptions();
  }

  @Test
  void should_report_sql_exception_from_context() {
    var ctx = mock(ExecuteContext.class);
    var sqlException = new SQLException("SQL syntax error", "42000", 1064);
    var runtimeException = new RuntimeException("Wrapped exception", sqlException);

    when(ctx.exception()).thenReturn(runtimeException);
    when(ctx.sqlException()).thenReturn(sqlException);
    when(ctx.sql()).thenReturn("SELECT * FROM invalid_table");

    listener.exception(ctx);

    var exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
    sentryMock.verify(() -> Sentry.captureException(exceptionCaptor.capture(), any(ScopeCallback.class)));
    assertThat(exceptionCaptor.getValue()).isInstanceOf(SQLException.class);
    assertThat(((SQLException) exceptionCaptor.getValue()).getSQLState()).isEqualTo("42000");
  }

  @Test
  void should_report_runtime_exception_when_no_sql_exception() {
    var ctx = mock(ExecuteContext.class);
    var runtimeException = new RuntimeException("jOOQ execution failed");

    when(ctx.exception()).thenReturn(runtimeException);
    when(ctx.sqlException()).thenReturn(null);
    when(ctx.sql()).thenReturn("INSERT INTO test VALUES (1)");

    listener.exception(ctx);

    var exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
    sentryMock.verify(() -> Sentry.captureException(exceptionCaptor.capture(), any(ScopeCallback.class)));
    assertThat(exceptionCaptor.getValue()).isSameAs(runtimeException);
  }

  @Test
  void should_not_report_when_no_exception() {
    var ctx = mock(ExecuteContext.class);

    when(ctx.exception()).thenReturn(null);

    listener.exception(ctx);

    sentryMock.verify(() -> Sentry.captureException(any(Throwable.class), any(ScopeCallback.class)), never());
  }

  @Test
  void should_handle_null_sql() {
    var ctx = mock(ExecuteContext.class);
    var exception = new RuntimeException("Error without SQL");

    when(ctx.exception()).thenReturn(exception);
    when(ctx.sqlException()).thenReturn(null);
    when(ctx.sql()).thenReturn(null);

    listener.exception(ctx);

    var exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
    sentryMock.verify(() -> Sentry.captureException(exceptionCaptor.capture(), any(ScopeCallback.class)));
    assertThat(exceptionCaptor.getValue()).isSameAs(exception);
  }

  @Test
  void should_prefer_sql_exception_over_runtime_exception() {
    var ctx = mock(ExecuteContext.class);
    var sqlException = new SQLException("Constraint violation", "23000", 1062);
    var runtimeException = new RuntimeException("Wrapper", sqlException);

    when(ctx.exception()).thenReturn(runtimeException);
    when(ctx.sqlException()).thenReturn(sqlException);
    when(ctx.sql()).thenReturn("INSERT INTO test (id) VALUES (1)");

    listener.exception(ctx);

    var exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
    sentryMock.verify(() -> Sentry.captureException(exceptionCaptor.capture(), any(ScopeCallback.class)));
    assertThat(exceptionCaptor.getValue()).isSameAs(sqlException);
  }
}
