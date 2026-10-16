/*
 * SonarLint Core - Commons
 * Copyright (C) SonarSource Sàrl
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
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.h2.jdbcx.JdbcConnectionPool;
import org.jooq.Configuration;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SonarLintDatabaseExceptionTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private SonarLintDatabase db;
  private MockedStatic<Sentry> sentryMock;

  @BeforeEach
  void setUp() {
    DatabaseExceptionReporter.clearRecentExceptions();
    sentryMock = mockStatic(Sentry.class);
    ILoggerApi loggerApi = mock(ILoggerApi.class);
    sentryMock.when(Sentry::logger).thenReturn(loggerApi);
  }

  @AfterEach
  void tearDown() {
    Thread.interrupted();
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
  void should_retry_transient_connection_contention_before_migrating(@TempDir Path storageRoot) throws SQLException {
    var pool = spy(JdbcConnectionPool.create("jdbc:h2:" + storageRoot.resolve("h2/sq-ide") + ";AUTO_SERVER=TRUE", "sa", ""));
    var contention = new SQLException("Database is already in use", "90020", 90020);
    doThrow(contention).doThrow(contention).doCallRealMethod().when(pool).getConnection();
    try (var pools = mockStatic(JdbcConnectionPool.class)) {
      pools.when(() -> JdbcConnectionPool.create(anyString(), anyString(), anyString())).thenReturn(pool);

      var database = new SonarLintDatabase(storageRoot);

      assertThat(database.dsl().fetch("SELECT connection_id FROM AI_CODEFIX_SETTINGS")).isEmpty();
      sentryMock.verify(() -> Sentry.captureException(any(Throwable.class), any(ScopeCallback.class)), never());
    } finally {
      pool.dispose();
    }
  }

  @Test
  void should_report_persistent_connection_contention_after_exhausting_retries(@TempDir Path storageRoot) throws SQLException {
    var pool = spy(JdbcConnectionPool.create("jdbc:h2:" + storageRoot.resolve("h2/sq-ide") + ";AUTO_SERVER=TRUE", "sa", ""));
    var contention = new SQLException("Database is already in use", "90020", 90020);
    doThrow(contention).when(pool).getConnection();
    var scope = mock(IScope.class);
    sentryMock.when(() -> Sentry.captureException(any(Throwable.class), any(ScopeCallback.class))).thenAnswer(invocation -> {
      invocation.getArgument(1, ScopeCallback.class).run(scope);
      return null;
    });
    try (var pools = mockStatic(JdbcConnectionPool.class)) {
      pools.when(() -> JdbcConnectionPool.create(anyString(), anyString(), anyString())).thenReturn(pool);

      assertThatThrownBy(() -> new SonarLintDatabase(storageRoot)).hasCause(contention);

      verify(pool, times(4)).getConnection();
      sentryMock.verify(() -> Sentry.captureException(any(Throwable.class), any(ScopeCallback.class)), times(1));
      verify(scope).setTag("db.phase", "startup");
      verify(scope).setTag("db.operation", "h2.connect");
      verify(pool).dispose();
    } finally {
      pool.dispose();
    }
  }

  @Test
  void should_fail_non_contention_without_retrying_and_suppress_cleanup_failure(@TempDir Path storageRoot) throws SQLException {
    var pool = mock(JdbcConnectionPool.class);
    var failure = new SQLException("Authentication failed", "28000", 28000);
    var cleanup = new IllegalStateException("Disposal failed");
    when(pool.getConnection()).thenThrow(failure);
    doThrow(cleanup).when(pool).dispose();
    try (var pools = mockStatic(JdbcConnectionPool.class)) {
      pools.when(() -> JdbcConnectionPool.create(anyString(), anyString(), anyString())).thenReturn(pool);

      assertThatThrownBy(() -> new SonarLintDatabase(storageRoot)).hasCause(failure);

      assertThat(failure.getSuppressed()).containsExactly(cleanup);
      verify(pool).getConnection();
      verify(pool).dispose();
      assertThat(logTester.logs(LogOutput.Level.ERROR)).isEmpty();
    }
  }

  @Test
  void should_abort_interrupted_backoff_and_restore_interrupt_after_disposal(@TempDir Path storageRoot) throws Exception {
    var pool = mock(JdbcConnectionPool.class);
    var acquired = new CountDownLatch(1);
    when(pool.getConnection()).thenAnswer(invocation -> {
      acquired.countDown();
      throw new SQLException("Database is already in use", "90020", 90020);
    });
    var cleanup = new IllegalStateException("Disposal failed");
    doAnswer(invocation -> {
      Thread.interrupted();
      throw cleanup;
    }).when(pool).dispose();
    try (var pools = mockStatic(JdbcConnectionPool.class); var flyway = mockStatic(Flyway.class)) {
      pools.when(() -> JdbcConnectionPool.create(anyString(), anyString(), anyString())).thenReturn(pool);
      var startupThread = Thread.currentThread();
      var interrupter = CompletableFuture.runAsync(() -> {
        await().atMost(5, TimeUnit.SECONDS).until(() -> acquired.getCount() == 0 && startupThread.getState() == Thread.State.TIMED_WAITING);
        startupThread.interrupt();
      });
      try {
        assertThatThrownBy(() -> new SonarLintDatabase(storageRoot)).cause().isInstanceOf(InterruptedException.class).hasSuppressedException(cleanup);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
      } finally {
        Thread.interrupted();
        interrupter.get(5, TimeUnit.SECONDS);
      }
      verify(pool).getConnection();
      verify(pool).dispose();
      flyway.verifyNoInteractions();
      sentryMock.verify(() -> Sentry.captureException(any(Throwable.class), any(ScopeCallback.class)), never());
      assertThat(logTester.logs(LogOutput.Level.ERROR)).isEmpty();
    }
  }

  @Test
  void should_return_connection_before_aborting_interruption_after_acquisition(@TempDir Path storageRoot) throws SQLException {
    var pool = mock(JdbcConnectionPool.class);
    var connection = mock(Connection.class);
    when(pool.getConnection()).thenAnswer(invocation -> {
      Thread.currentThread().interrupt();
      return connection;
    });
    doAnswer(invocation -> Thread.interrupted()).when(pool).dispose();
    try (var pools = mockStatic(JdbcConnectionPool.class); var flyway = mockStatic(Flyway.class)) {
      pools.when(() -> JdbcConnectionPool.create(anyString(), anyString(), anyString())).thenReturn(pool);

      assertThatThrownBy(() -> new SonarLintDatabase(storageRoot)).hasCauseInstanceOf(InterruptedException.class);

      verify(connection).close();
      verify(pool).dispose();
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      flyway.verifyNoInteractions();
      sentryMock.verify(() -> Sentry.captureException(any(Throwable.class), any(ScopeCallback.class)), never());
      assertThat(logTester.logs(LogOutput.Level.ERROR)).isEmpty();
    }
  }

  @Test
  void should_not_retry_connection_return_failure(@TempDir Path storageRoot) throws SQLException {
    var pool = mock(JdbcConnectionPool.class);
    var connection = mock(Connection.class);
    var failure = new SQLException("Connection return failed", "90020", 90020);
    when(pool.getConnection()).thenReturn(connection);
    doThrow(failure).when(connection).close();
    try (var pools = mockStatic(JdbcConnectionPool.class); var flyway = mockStatic(Flyway.class)) {
      pools.when(() -> JdbcConnectionPool.create(anyString(), anyString(), anyString())).thenReturn(pool);

      assertThatThrownBy(() -> new SonarLintDatabase(storageRoot)).hasCause(failure);

      verify(pool).getConnection();
      verify(connection).close();
      verify(pool).dispose();
      flyway.verifyNoInteractions();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"configure", "load", "migrate", "jooq"})
  void should_dispose_pool_after_initialization_failure(String stage, @TempDir Path storageRoot) throws SQLException {
    var pool = mock(JdbcConnectionPool.class);
    var connection = mock(Connection.class);
    var config = mock(FluentConfiguration.class, RETURNS_SELF);
    var flyway = mock(Flyway.class);
    var failure = new IllegalStateException("Initialization failed at " + stage);
    when(pool.getConnection()).thenReturn(connection);
    when(config.load()).thenReturn(flyway);
    try (var pools = mockStatic(JdbcConnectionPool.class); var flyways = mockStatic(Flyway.class); var dsls = mockStatic(DSL.class)) {
      pools.when(() -> JdbcConnectionPool.create(anyString(), anyString(), anyString())).thenReturn(pool);
      flyways.when(Flyway::configure).thenReturn(config);
      switch (stage) {
        case "configure" -> flyways.when(Flyway::configure).thenThrow(failure);
        case "load" -> when(config.load()).thenThrow(failure);
        case "migrate" -> when(flyway.migrate()).thenThrow(failure);
        case "jooq" -> dsls.when(() -> DSL.using(any(Configuration.class))).thenThrow(failure);
        default -> throw new IllegalArgumentException(stage);
      }

      assertThatThrownBy(() -> new SonarLintDatabase(storageRoot)).isSameAs(failure);

      verify(connection).close();
      verify(pool).dispose();
      verify(flyway, times(stage.equals("migrate") || stage.equals("jooq") ? 1 : 0)).migrate();
      sentryMock.verify(() -> Sentry.captureException(any(Throwable.class), any(ScopeCallback.class)), times(stage.equals("migrate") ? 1 : 0));
    }
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
