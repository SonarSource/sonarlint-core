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

import java.nio.file.Path;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SonarLintDatabaseExceptionTests {

  @RegisterExtension
  static SonarLintLogTester logTester = new SonarLintLogTester();

  private SonarLintDatabase db;

  @BeforeEach
  void setUp() {
    DatabaseExceptionReporter.clearRecentExceptions();
  }

  @AfterEach
  void tearDown() {
    if (db != null) {
      db.shutdown();
    }
    DatabaseExceptionReporter.clearRecentExceptions();
  }

  @Test
  void should_report_runtime_sql_exception_via_listener(@TempDir Path tempDir) {
    var storageRoot = tempDir.resolve("storage");
    db = new SonarLintDatabase(storageRoot);

    assertThatThrownBy(() -> db.dsl().execute("SELECT * FROM non_existent_table"))
      .isInstanceOf(DataAccessException.class);

    assertThat(logTester.logs()).anyMatch(log -> log.contains("Reporting database exception to Sentry"));
  }

  @Test
  void should_report_invalid_sql_syntax_exception(@TempDir Path tempDir) {
    var storageRoot = tempDir.resolve("storage");
    db = new SonarLintDatabase(storageRoot);

    assertThatThrownBy(() -> db.dsl().execute("INVALID SQL SYNTAX HERE"))
      .isInstanceOf(DataAccessException.class);

    assertThat(logTester.logs()).anyMatch(log -> log.contains("Reporting database exception to Sentry"));
  }

  @Test
  void should_report_constraint_violation_exception(@TempDir Path tempDir) {
    var storageRoot = tempDir.resolve("storage");
    db = new SonarLintDatabase(storageRoot);

    db.dsl().execute("CREATE TABLE IF NOT EXISTS test_table (id INT PRIMARY KEY, name VARCHAR(100))");
    db.dsl().execute("INSERT INTO test_table (id, name) VALUES (1, 'test')");

    assertThatThrownBy(() -> db.dsl().execute("INSERT INTO test_table (id, name) VALUES (1, 'duplicate')"))
      .isInstanceOf(DataAccessException.class);

    assertThat(logTester.logs()).anyMatch(log -> log.contains("Reporting database exception to Sentry"));
  }

  @Test
  void should_initialize_database_successfully(@TempDir Path tempDir) {
    var storageRoot = tempDir.resolve("storage");
    db = new SonarLintDatabase(storageRoot);

    assertThat(db.dsl()).isNotNull();
    assertThat(logTester.logs()).noneMatch(log -> log.contains("startup") && log.contains("h2.pool.create"));
  }

  @Test
  void should_shutdown_database_successfully(@TempDir Path tempDir) {
    var storageRoot = tempDir.resolve("storage");
    db = new SonarLintDatabase(storageRoot);

    db.shutdown();
    db = null;

    assertThat(logTester.logs()).anyMatch(log -> log.contains("H2Database disposed"));
    assertThat(logTester.logs()).noneMatch(log -> log.contains("shutdown") && log.contains("h2.pool.dispose") && log.contains("Reporting"));
  }

  @Test
  void should_execute_valid_queries_without_exception_reporting(@TempDir Path tempDir) {
    var storageRoot = tempDir.resolve("storage");
    db = new SonarLintDatabase(storageRoot);

    db.dsl().execute("CREATE TABLE IF NOT EXISTS valid_table (id INT, name VARCHAR(100))");
    db.dsl().execute("INSERT INTO valid_table (id, name) VALUES (1, 'test')");
    var result = db.dsl().fetch("SELECT * FROM valid_table WHERE id = 1");

    assertThat(result).hasSize(1);
    assertThat(logTester.logs()).noneMatch(log -> log.contains("Reporting database exception to Sentry"));
  }

}
