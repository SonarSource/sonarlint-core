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

import io.sentry.IScope;
import io.sentry.Sentry;
import io.sentry.ScopeCallback;
import io.sentry.logger.ILoggerApi;
import java.sql.SQLException;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DatabaseExceptionReporterTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private MockedStatic<Sentry> sentryMock;

  @BeforeEach
  void setUp() {
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
  void should_capture_generic_exception() {
    var exception = new RuntimeException("Test database error");

    DatabaseExceptionReporter.capture(exception, "runtime", "jooq.execute", "SELECT * FROM test");

    var exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
    sentryMock.verify(() -> Sentry.captureException(exceptionCaptor.capture(), any(ScopeCallback.class)));
    assertThat(exceptionCaptor.getValue()).isSameAs(exception);
    assertThat(exceptionCaptor.getValue().getMessage()).isEqualTo("Test database error");
  }

  @Test
  void should_capture_sql_exception_with_details() {
    var sqlException = new SQLException("SQL error", "42000", 1234);

    DatabaseExceptionReporter.capture(sqlException, "startup", "flyway.migrate", null);

    var exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
    sentryMock.verify(() -> Sentry.captureException(exceptionCaptor.capture(), any(ScopeCallback.class)));
    assertThat(exceptionCaptor.getValue()).isInstanceOf(SQLException.class);
    assertThat(((SQLException) exceptionCaptor.getValue()).getSQLState()).isEqualTo("42000");
  }

  @Test
  void should_capture_exception_without_sql() {
    var exception = new RuntimeException("Pool creation failed");

    DatabaseExceptionReporter.capture(exception, "startup", "h2.pool.create");

    var exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
    sentryMock.verify(() -> Sentry.captureException(exceptionCaptor.capture(), any(ScopeCallback.class)));
    assertThat(exceptionCaptor.getValue()).isSameAs(exception);
  }

  @Test
  void should_deduplicate_same_message_within_window() {
    var exception = new RuntimeException("Duplicate error");

    DatabaseExceptionReporter.capture(exception, "runtime", "jooq.execute", "SELECT 1");
    DatabaseExceptionReporter.capture(exception, "runtime", "jooq.execute", "SELECT 1");

    var exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
    sentryMock.verify(() -> Sentry.captureException(exceptionCaptor.capture(), any(ScopeCallback.class)), times(1));
    assertThat(exceptionCaptor.getValue()).isSameAs(exception);
  }

  @Test
  void should_not_deduplicate_different_exceptions() {
    var exception1 = new RuntimeException("Error 1");
    var exception2 = new RuntimeException("Error 2");

    DatabaseExceptionReporter.capture(exception1, "runtime", "jooq.execute", "SELECT 1");
    DatabaseExceptionReporter.capture(exception2, "runtime", "jooq.execute", "SELECT 2");

    var exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
    sentryMock.verify(() -> Sentry.captureException(exceptionCaptor.capture(), any(ScopeCallback.class)), times(2));
    assertThat(exceptionCaptor.getAllValues()).containsExactly(exception1, exception2);
  }

  @Test
  void should_deduplicate_same_message_even_with_different_phase() {
    var exception = new RuntimeException("Same error");

    DatabaseExceptionReporter.capture(exception, "startup", "h2.pool.create");
    DatabaseExceptionReporter.capture(exception, "shutdown", "h2.pool.dispose");

    var exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
    sentryMock.verify(() -> Sentry.captureException(exceptionCaptor.capture(), any(ScopeCallback.class)), times(1));
    assertThat(exceptionCaptor.getValue()).isSameAs(exception);
  }

  @Test
  void should_always_report_null_message_exceptions_without_deduplication() {
    var exception1 = new RuntimeException();
    var exception2 = new RuntimeException();

    DatabaseExceptionReporter.capture(exception1, "runtime", "jooq.execute");
    DatabaseExceptionReporter.capture(exception2, "runtime", "jooq.execute");

    var exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
    sentryMock.verify(() -> Sentry.captureException(exceptionCaptor.capture(), any(ScopeCallback.class)), times(2));
    assertThat(exceptionCaptor.getAllValues()).containsExactly(exception1, exception2);
  }

  @Test
  void should_truncate_long_sql() {
    var longSql = "SELECT " + "a".repeat(2000) + " FROM test";
    var exception = new RuntimeException("SQL error");

    DatabaseExceptionReporter.capture(exception, "runtime", "jooq.execute", longSql);

    var exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
    sentryMock.verify(() -> Sentry.captureException(exceptionCaptor.capture(), any(ScopeCallback.class)));
    assertThat(exceptionCaptor.getValue()).isSameAs(exception);
  }

  @Test
  void should_cleanup_old_entries_after_dedup_window() throws InterruptedException {
    System.setProperty(DatabaseExceptionReporter.DEDUP_WINDOW_PROPERTY, "50");
    try {
      DatabaseExceptionReporter.capture(new RuntimeException("Error 1"), "runtime", "op1");
      DatabaseExceptionReporter.capture(new RuntimeException("Error 2"), "runtime", "op2");
      DatabaseExceptionReporter.capture(new RuntimeException("Error 3"), "runtime", "op3");

      assertThat(DatabaseExceptionReporter.getRecentExceptionsCount()).isEqualTo(3);

      Thread.sleep(100);

      DatabaseExceptionReporter.capture(new RuntimeException("Error 4"), "runtime", "op4");

      assertThat(DatabaseExceptionReporter.getRecentExceptionsCount()).isEqualTo(1);
    } finally {
      System.clearProperty(DatabaseExceptionReporter.DEDUP_WINDOW_PROPERTY);
    }
  }

  @Test
  void should_set_scope_tags_for_generic_exception() {
    var scope = mock(IScope.class);
    sentryMock.when(() -> Sentry.captureException(any(Throwable.class), any(ScopeCallback.class)))
      .thenAnswer(invocation -> {
        var callback = invocation.getArgument(1, ScopeCallback.class);
        callback.run(scope);
        return null;
      });

    var exception = new RuntimeException("Test error");
    DatabaseExceptionReporter.capture(exception, "startup", "h2.pool.create");

    verify(scope).setTag("component", "database");
    verify(scope).setTag("db.phase", "startup");
    verify(scope).setTag("db.operation", "h2.pool.create");
  }

  @Test
  void should_set_scope_tags_for_sql_exception_with_sql_state() {
    var scope = mock(IScope.class);
    sentryMock.when(() -> Sentry.captureException(any(Throwable.class), any(ScopeCallback.class)))
      .thenAnswer(invocation -> {
        var callback = invocation.getArgument(1, ScopeCallback.class);
        callback.run(scope);
        return null;
      });

    var sqlException = new SQLException("SQL error", "42000", 1234);
    DatabaseExceptionReporter.capture(sqlException, "runtime", "jooq.execute");

    verify(scope).setTag("component", "database");
    verify(scope).setTag("db.phase", "runtime");
    verify(scope).setTag("db.operation", "jooq.execute");
    verify(scope).setTag("db.sqlState", "42000");
    verify(scope).setTag("db.errorCode", "1234");
  }

  @Test
  void should_not_set_sql_state_tag_when_null() {
    var scope = mock(IScope.class);
    sentryMock.when(() -> Sentry.captureException(any(Throwable.class), any(ScopeCallback.class)))
      .thenAnswer(invocation -> {
        var callback = invocation.getArgument(1, ScopeCallback.class);
        callback.run(scope);
        return null;
      });

    var sqlException = new SQLException("SQL error", null, 5678);
    DatabaseExceptionReporter.capture(sqlException, "runtime", "jooq.execute");

    verify(scope).setTag("component", "database");
    verify(scope).setTag("db.errorCode", "5678");
    verify(scope, times(0)).setTag("db.sqlState", null);
  }

  @Test
  void should_set_sql_extra_when_provided() {
    var scope = mock(IScope.class);
    sentryMock.when(() -> Sentry.captureException(any(Throwable.class), any(ScopeCallback.class)))
      .thenAnswer(invocation -> {
        var callback = invocation.getArgument(1, ScopeCallback.class);
        callback.run(scope);
        return null;
      });

    var exception = new RuntimeException("Test error");
    DatabaseExceptionReporter.capture(exception, "runtime", "jooq.execute", "SELECT * FROM test");

    verify(scope).setExtra("db.sql", "SELECT * FROM test");
  }

  @Test
  void should_not_set_sql_extra_when_empty() {
    var scope = mock(IScope.class);
    sentryMock.when(() -> Sentry.captureException(any(Throwable.class), any(ScopeCallback.class)))
      .thenAnswer(invocation -> {
        var callback = invocation.getArgument(1, ScopeCallback.class);
        callback.run(scope);
        return null;
      });

    var exception = new RuntimeException("Test error");
    DatabaseExceptionReporter.capture(exception, "runtime", "jooq.execute", "");

    verify(scope, times(0)).setExtra(any(), any());
  }

  @Test
  void should_truncate_sql_in_extra_when_exceeds_1000_chars() {
    var scope = mock(IScope.class);
    sentryMock.when(() -> Sentry.captureException(any(Throwable.class), any(ScopeCallback.class)))
      .thenAnswer(invocation -> {
        var callback = invocation.getArgument(1, ScopeCallback.class);
        callback.run(scope);
        return null;
      });

    var longSql = "SELECT " + "a".repeat(2000) + " FROM test";
    var exception = new RuntimeException("SQL error");
    DatabaseExceptionReporter.capture(exception, "runtime", "jooq.execute", longSql);

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(scope).setExtra(any(), sqlCaptor.capture());
    assertThat(sqlCaptor.getValue()).hasSize(1000 + "... [truncated]".length());
    assertThat(sqlCaptor.getValue()).endsWith("... [truncated]");
  }

  @Test
  void should_use_default_dedup_window_when_property_is_invalid() {
    System.setProperty(DatabaseExceptionReporter.DEDUP_WINDOW_PROPERTY, "not-a-number");
    try {
      var exception1 = new RuntimeException("Error");
      var exception2 = new RuntimeException("Error");

      DatabaseExceptionReporter.capture(exception1, "runtime", "op1");
      DatabaseExceptionReporter.capture(exception2, "runtime", "op2");

      // Should still deduplicate using default window (not crash)
      sentryMock.verify(() -> Sentry.captureException(any(Throwable.class), any(ScopeCallback.class)), times(1));
    } finally {
      System.clearProperty(DatabaseExceptionReporter.DEDUP_WINDOW_PROPERTY);
    }
  }
}
