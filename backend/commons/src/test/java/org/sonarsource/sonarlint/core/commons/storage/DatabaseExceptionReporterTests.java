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

import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseExceptionReporterTests {

  @RegisterExtension
  static SonarLintLogTester logTester = new SonarLintLogTester();

  @BeforeEach
  void setUp() {
    DatabaseExceptionReporter.clearRecentExceptions();
  }

  @AfterEach
  void tearDown() {
    DatabaseExceptionReporter.clearRecentExceptions();
  }

  @Test
  void should_capture_generic_exception() {
    var exception = new RuntimeException("Test database error");

    DatabaseExceptionReporter.capture(exception, "runtime", "jooq.execute", "SELECT * FROM test");

    assertThat(logTester.logs()).anyMatch(log -> log.contains("Reporting database exception to Sentry"));
  }

  @Test
  void should_capture_sql_exception_with_details() {
    var sqlException = new SQLException("SQL error", "42000", 1234);

    DatabaseExceptionReporter.capture(sqlException, "startup", "flyway.migrate", null);

    assertThat(logTester.logs()).anyMatch(log -> log.contains("Reporting database exception to Sentry"));
  }

  @Test
  void should_capture_exception_without_sql() {
    var exception = new RuntimeException("Pool creation failed");

    DatabaseExceptionReporter.capture(exception, "startup", "h2.pool.create");

    assertThat(logTester.logs()).anyMatch(log -> log.contains("Reporting database exception to Sentry"));
  }

  @Test
  void should_deduplicate_same_message_within_window() {
    var exception = new RuntimeException("Duplicate error");

    DatabaseExceptionReporter.capture(exception, "runtime", "jooq.execute", "SELECT 1");
    DatabaseExceptionReporter.capture(exception, "runtime", "jooq.execute", "SELECT 1");

    var reportingLogs = logTester.logs().stream()
      .filter(log -> log.contains("Reporting database exception to Sentry"))
      .count();
    var skippingLogs = logTester.logs().stream()
      .filter(log -> log.contains("Skipping duplicate database exception report"))
      .count();

    assertThat(reportingLogs).isEqualTo(1);
    assertThat(skippingLogs).isEqualTo(1);
  }

  @Test
  void should_not_deduplicate_different_exceptions() {
    var exception1 = new RuntimeException("Error 1");
    var exception2 = new RuntimeException("Error 2");

    DatabaseExceptionReporter.capture(exception1, "runtime", "jooq.execute", "SELECT 1");
    DatabaseExceptionReporter.capture(exception2, "runtime", "jooq.execute", "SELECT 2");

    var reportingLogs = logTester.logs().stream()
      .filter(log -> log.contains("Reporting database exception to Sentry"))
      .count();

    assertThat(reportingLogs).isEqualTo(2);
  }

  @Test
  void should_deduplicate_same_message_even_with_different_phase() {
    var exception = new RuntimeException("Same error");

    DatabaseExceptionReporter.capture(exception, "startup", "h2.pool.create");
    DatabaseExceptionReporter.capture(exception, "shutdown", "h2.pool.dispose");

    var reportingLogs = logTester.logs().stream()
      .filter(log -> log.contains("Reporting database exception to Sentry"))
      .count();
    var skippingLogs = logTester.logs().stream()
      .filter(log -> log.contains("Skipping duplicate database exception report"))
      .count();

    assertThat(reportingLogs).isEqualTo(1);
    assertThat(skippingLogs).isEqualTo(1);
  }

  @Test
  void should_always_report_null_message_exceptions_without_deduplication() {
    var exception1 = new RuntimeException((String) null);
    var exception2 = new RuntimeException((String) null);

    DatabaseExceptionReporter.capture(exception1, "runtime", "jooq.execute");
    DatabaseExceptionReporter.capture(exception2, "runtime", "jooq.execute");

    var reportingLogs = logTester.logs().stream()
      .filter(log -> log.contains("Reporting database exception to Sentry"))
      .count();

    assertThat(reportingLogs).isEqualTo(2);
  }

  @Test
  void should_truncate_long_sql() {
    var longSql = "SELECT " + "a".repeat(2000) + " FROM test";
    var exception = new RuntimeException("SQL error");

    DatabaseExceptionReporter.capture(exception, "runtime", "jooq.execute", longSql);

    assertThat(logTester.logs()).anyMatch(log -> log.contains("Reporting database exception to Sentry"));
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
}
