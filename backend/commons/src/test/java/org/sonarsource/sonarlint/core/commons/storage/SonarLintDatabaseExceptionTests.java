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
import java.nio.file.Path;
import java.sql.SQLException;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

class SonarLintDatabaseExceptionTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private SonarLintDatabase db;
  private MockedStatic<Sentry> sentryMock;

  @BeforeEach
  void setUp() {
    DatabaseExceptionReporter.clearRecentExceptions();
    sentryMock = mockStatic(Sentry.class);
    sentryMock.when(Sentry::logger).thenReturn(mock(ILoggerApi.class));
  }

  @AfterEach
  void tearDown() {
    if (db != null) {
      db.shutdown();
    }
    sentryMock.close();
    DatabaseExceptionReporter.clearRecentExceptions();
  }

  @Test
  void should_report_runtime_sql_exception_via_listener(@TempDir Path tempDir) {
    var storageRoot = tempDir.resolve("storage");
    db = new SonarLintDatabase(storageRoot);

    assertThatThrownBy(() -> db.dsl().execute("SELECT * FROM non_existent_table"))
      .isInstanceOf(DataAccessException.class);

    var exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
    sentryMock.verify(() -> Sentry.captureException(exceptionCaptor.capture(), any(ScopeCallback.class)));

    var capturedException = exceptionCaptor.getValue();
    assertThat(capturedException).isInstanceOf(SQLException.class);
    var sqlException = (SQLException) capturedException;
    assertThat(sqlException.getSQLState()).isEqualTo("42S02");
    assertThat(sqlException.getMessage()).contains("NON_EXISTENT_TABLE");
  }

  @Test
  void should_report_invalid_sql_syntax_exception(@TempDir Path tempDir) {
    var storageRoot = tempDir.resolve("storage");
    db = new SonarLintDatabase(storageRoot);

    assertThatThrownBy(() -> db.dsl().execute("INVALID SQL SYNTAX HERE"))
      .isInstanceOf(DataAccessException.class);

    var exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
    sentryMock.verify(() -> Sentry.captureException(exceptionCaptor.capture(), any(ScopeCallback.class)));

    var capturedException = exceptionCaptor.getValue();
    assertThat(capturedException).isInstanceOf(SQLException.class);
    var sqlException = (SQLException) capturedException;
    assertThat(sqlException.getSQLState()).isEqualTo("42001");
  }

  @Test
  void should_report_constraint_violation_exception(@TempDir Path tempDir) {
    var storageRoot = tempDir.resolve("storage");
    db = new SonarLintDatabase(storageRoot);

    db.dsl().execute("CREATE TABLE IF NOT EXISTS test_table (id INT PRIMARY KEY, name VARCHAR(100))");
    db.dsl().execute("INSERT INTO test_table (id, name) VALUES (1, 'test')");

    assertThatThrownBy(() -> db.dsl().execute("INSERT INTO test_table (id, name) VALUES (1, 'duplicate')"))
      .isInstanceOf(DataAccessException.class);

    var exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
    sentryMock.verify(() -> Sentry.captureException(exceptionCaptor.capture(), any(ScopeCallback.class)));

    var capturedException = exceptionCaptor.getValue();
    assertThat(capturedException).isInstanceOf(SQLException.class);
    var sqlException = (SQLException) capturedException;
    assertThat(sqlException.getSQLState()).isEqualTo("23505");
    assertThat(sqlException.getMessage()).contains("Unique index or primary key violation");
  }

  @Test
  void should_initialize_database_successfully(@TempDir Path tempDir) {
    var storageRoot = tempDir.resolve("storage");
    db = new SonarLintDatabase(storageRoot);

    assertThat(db.dsl()).isNotNull();
    sentryMock.verify(() -> Sentry.captureException(any(Throwable.class), any(ScopeCallback.class)), never());
  }

  @Test
  void should_shutdown_database_successfully(@TempDir Path tempDir) {
    var storageRoot = tempDir.resolve("storage");
    db = new SonarLintDatabase(storageRoot);

    db.shutdown();
    db = null;

    sentryMock.verify(() -> Sentry.captureException(any(Throwable.class), any(ScopeCallback.class)), never());
  }

  @Test
  void should_execute_valid_queries_without_exception_reporting(@TempDir Path tempDir) {
    var storageRoot = tempDir.resolve("storage");
    db = new SonarLintDatabase(storageRoot);

    db.dsl().execute("CREATE TABLE IF NOT EXISTS valid_table (id INT, name VARCHAR(100))");
    db.dsl().execute("INSERT INTO valid_table (id, name) VALUES (1, 'test')");
    var result = db.dsl().fetch("SELECT * FROM valid_table WHERE id = 1");

    assertThat(result).hasSize(1);
    sentryMock.verify(() -> Sentry.captureException(any(Throwable.class), any(ScopeCallback.class)), never());
  }

}
