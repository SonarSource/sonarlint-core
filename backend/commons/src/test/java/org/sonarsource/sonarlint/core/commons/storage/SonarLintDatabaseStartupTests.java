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

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.h2.jdbcx.JdbcConnectionPool;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SonarLintDatabaseStartupTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();
  @TempDir
  Path root;
  private final JdbcConnectionPool pool = mock(JdbcConnectionPool.class);
  private final Connection connection = mock(Connection.class);
  private final FluentConfiguration config = mock(FluentConfiguration.class, RETURNS_SELF);
  private final Flyway flyway = mock(Flyway.class);
  private final DSLContext dsl = mock(DSLContext.class);
  private MockedStatic<JdbcConnectionPool> pools;
  private MockedStatic<Flyway> flyways;
  private MockedStatic<DSL> dsls;
  private MockedStatic<DatabaseExceptionReporter> reporter;

  @BeforeEach
  void setUp() throws SQLException {
    pools = mockStatic(JdbcConnectionPool.class);
    flyways = mockStatic(Flyway.class);
    dsls = mockStatic(DSL.class);
    reporter = mockStatic(DatabaseExceptionReporter.class);
    pools.when(() -> JdbcConnectionPool.create(anyString(), anyString(), anyString())).thenReturn(pool);
    when(pool.getConnection()).thenReturn(connection);
    flyways.when(Flyway::configure).thenAnswer(invocation -> {
      verify(connection).close();
      return config;
    });
    when(config.load()).thenReturn(flyway);
    dsls.when(() -> DSL.using(any(Configuration.class))).thenReturn(dsl);
  }

  @AfterEach
  void tearDown() {
    Thread.interrupted();
    reporter.close();
    dsls.close();
    flyways.close();
    pools.close();
  }

  @Test
  void warms_pool_before_flyway_and_migrates_once_before_publishing() throws SQLException {
    var database = new SonarLintDatabase(root);
    var order = inOrder(pool, connection, config, flyway);
    order.verify(pool).getConnection();
    order.verify(connection).close();
    order.verify(config).dataSource(pool);
    order.verify(config).load();
    order.verify(flyway).migrate();
    assertThat(database.dsl()).isSameAs(dsl);
    verify(pool, never()).dispose();
    database.shutdown();
    verify(pool).dispose();
    reporter.verifyNoInteractions();
  }

  @Test
  void disposes_after_connection_failure_without_starting_flyway_or_reporting_twice() throws SQLException {
    var failure = new SQLException("bad credentials", "28000", 28000);
    when(pool.getConnection()).thenThrow(failure);
    assertThatThrownBy(() -> new SonarLintDatabase(root)).hasCause(failure);
    verify(pool).dispose();
    flyways.verifyNoInteractions();
    reporter.verify(() -> DatabaseExceptionReporter.captureStartup(same(failure), any()));
    reporter.verifyNoMoreInteractions();
  }

  @Test
  void disposes_after_flyway_configuration_failure() {
    var failure = new IllegalStateException("configure failed");
    flyways.when(Flyway::configure).thenThrow(failure);
    assertThatThrownBy(() -> new SonarLintDatabase(root)).isSameAs(failure);
    verify(pool).dispose();
    reporter.verify(() -> DatabaseExceptionReporter.capture(failure, "startup", "flyway.configure"));
  }

  @Test
  void disposes_after_flyway_load_failure() {
    var failure = new IllegalStateException("load failed");
    when(config.load()).thenThrow(failure);
    assertThatThrownBy(() -> new SonarLintDatabase(root)).isSameAs(failure);
    verify(pool).dispose();
    verify(flyway, never()).migrate();
    reporter.verify(() -> DatabaseExceptionReporter.capture(failure, "startup", "flyway.configure"));
  }

  @Test
  void disposes_after_migration_failure_without_retrying_migration() {
    var failure = new IllegalStateException("migrate failed", new SQLException("locked", "90020", 90020));
    when(flyway.migrate()).thenThrow(failure);
    assertThatThrownBy(() -> new SonarLintDatabase(root)).isSameAs(failure);
    verify(pool).dispose();
    verify(flyway).migrate();
    reporter.verify(() -> DatabaseExceptionReporter.capture(failure, "startup", "flyway.migrate"));
    reporter.verifyNoMoreInteractions();
  }

  @Test
  void disposes_after_jooq_failure_and_suppresses_cleanup_failure() {
    var failure = new IllegalStateException("jooq failed");
    var cleanup = new IllegalStateException("dispose failed");
    dsls.when(() -> DSL.using(any(Configuration.class))).thenThrow(failure);
    doThrow(cleanup).when(pool).dispose();
    assertThatThrownBy(() -> new SonarLintDatabase(root)).isSameAs(failure).hasSuppressedException(cleanup);
    verify(pool).dispose();
  }

  @Test
  void disposes_after_cancellation_without_migrations_reporting_or_advice() throws SQLException {
    when(pool.getConnection()).thenAnswer(invocation -> {
      Thread.currentThread().interrupt();
      return connection;
    });
    assertThatThrownBy(() -> new SonarLintDatabase(root)).hasCauseInstanceOf(InterruptedException.class);
    verify(connection).close();
    verify(pool).dispose();
    flyways.verifyNoInteractions();
    reporter.verifyNoInteractions();
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
    assertThat(logTester.logs(LogOutput.Level.ERROR)).isEmpty();
  }

  @Test
  void migration_reporting_failure_does_not_replace_original_failure() {
    var failure = new IllegalStateException("migration failed");
    when(flyway.migrate()).thenThrow(failure);
    reporter.when(() -> DatabaseExceptionReporter.capture(failure, "startup", "flyway.migrate")).thenThrow(new IllegalStateException("report failed"));
    assertThatThrownBy(() -> new SonarLintDatabase(root)).isSameAs(failure);
    verify(pool).dispose();
  }
}
