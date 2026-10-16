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

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.h2.jdbcx.JdbcConnectionPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class H2DatabaseStartupTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();
  @TempDir
  Path root;
  private final JdbcConnectionPool pool = mock(JdbcConnectionPool.class);
  private final Connection connection = mock(Connection.class);
  private final FakeTime time = new FakeTime();
  private MockedStatic<DatabaseExceptionReporter> reporter;

  @BeforeEach
  void setUp() throws SQLException {
    reporter = mockStatic(DatabaseExceptionReporter.class);
    when(pool.getConnection()).thenReturn(connection);
  }

  @AfterEach
  void tearDown() {
    Thread.interrupted();
    reporter.close();
  }

  @Test
  void immediately_connects_and_returns_the_connection() throws SQLException {
    startup().connect();
    verify(pool).getConnection();
    verify(connection).close();
    assertThat(time.sleeps).isEmpty();
    reporter.verifyNoInteractions();
  }

  @Test
  void retries_contention_with_exact_backoff_and_recovers() throws SQLException {
    when(pool.getConnection()).thenThrow(locked(), locked(), locked(), locked()).thenReturn(connection);
    startup().connect();
    assertThat(time.sleeps).containsExactly(250L, 500L, 1000L, 1000L);
    verify(pool, times(5)).getConnection();
    verify(connection).close();
    reporter.verifyNoInteractions();
    assertThat(logTester.logs()).anyMatch(log -> log.contains("recovered after 5 attempts in 2750 ms"));
    assertThat(logTester.logs(LogOutput.Level.ERROR)).isEmpty();
  }

  @Test
  void clamps_final_backoff_and_never_attempts_at_the_deadline() throws SQLException {
    var failure = locked();
    when(pool.getConnection()).thenThrow(failure);
    assertThatThrownBy(() -> startup().connect()).hasCause(failure);
    assertThat(time.sleeps).containsExactly(250L, 500L, 1000L, 1000L, 1000L, 1000L, 1000L, 1000L, 1000L, 1000L, 1000L, 250L);
    verify(pool, times(12)).getConnection();
    var diagnostics = diagnostics(failure);
    assertThat(diagnostics.attempts()).isEqualTo(12);
    assertThat(diagnostics.elapsedMillis()).isEqualTo(10_000);
    assertThat(logTester.logs(LogOutput.Level.ERROR)).singleElement().asString().contains("Close other IDE instances").doesNotContain("delete");
  }

  @Test
  void does_not_retry_a_slow_failed_attempt_past_deadline() throws SQLException {
    var failure = locked();
    when(pool.getConnection()).thenAnswer(invocation -> {
      time.advance(12_000);
      throw failure;
    });
    assertThatThrownBy(() -> startup().connect()).hasCause(failure);
    verify(pool).getConnection();
    assertThat(time.sleeps).isEmpty();
    assertThat(diagnostics(failure).elapsedMillis()).isEqualTo(12_000);
  }

  @Test
  void accounts_for_time_in_acquisition_when_clamping_backoff() throws SQLException {
    var failure = locked();
    when(pool.getConnection()).thenAnswer(invocation -> {
      time.advance(9_900);
      throw failure;
    });
    assertThatThrownBy(() -> startup().connect()).hasCause(failure);
    assertThat(time.sleeps).containsExactly(100L);
    verify(pool).getConnection();
    assertThat(diagnostics(failure).elapsedMillis()).isEqualTo(10_000);
  }

  @Test
  void never_retries_when_sleep_overshoots_the_deadline() throws SQLException {
    var failure = locked();
    when(pool.getConnection()).thenThrow(failure);
    var startup = new H2DatabaseStartup(pool, lockFile(), () -> time.nanos, nanos -> time.advance(11_000));
    assertThatThrownBy(startup::connect).hasCause(failure);
    verify(pool).getConnection();
    assertThat(diagnostics(failure).elapsedMillis()).isEqualTo(11_000);
  }

  @Test
  void accepts_a_late_successful_in_flight_attempt() throws SQLException {
    when(pool.getConnection()).thenThrow(locked()).thenAnswer(invocation -> {
      time.advance(12_000);
      return connection;
    });
    startup().connect();
    verify(connection).close();
    reporter.verifyNoInteractions();
  }

  @Test
  void does_not_retry_an_unrelated_error_or_infer_contention_from_messages_or_suppressed_errors() throws SQLException {
    var failure = new SQLException("90020 database in use", "28000", 28000);
    failure.addSuppressed(locked());
    when(pool.getConnection()).thenThrow(failure);
    assertThatThrownBy(() -> startup().connect()).hasCause(failure);
    verify(pool).getConnection();
    diagnostics(failure);
    assertThat(logTester.logs(LogOutput.Level.ERROR)).isEmpty();
  }

  @Test
  void follows_wrapped_and_chained_sql_exceptions_with_cycles() throws SQLException {
    var outer = new SQLException("outer");
    var contention = locked();
    outer.setNextException(contention);
    contention.initCause(outer);
    var wrapper = new IllegalStateException("wrapper", outer);
    when(pool.getConnection()).thenThrow(wrapper).thenReturn(connection);
    startup().connect();
    assertThat(time.sleeps).containsExactly(250L);
    assertThat(SqlExceptions.chain(wrapper)).containsExactly(wrapper, outer, contention);
    reporter.verifyNoInteractions();
  }

  @Test
  void reports_unchecked_acquisition_failure_without_wrapping_it() throws SQLException {
    var failure = new IllegalArgumentException("invalid connection");
    when(pool.getConnection()).thenThrow(failure);
    assertThatThrownBy(() -> startup().connect()).isSameAs(failure);
    diagnostics(failure);
  }

  @Test
  void never_retries_connection_return_even_for_contention() throws SQLException {
    var failure = locked();
    doThrow(failure).when(connection).close();
    assertThatThrownBy(() -> startup().connect()).hasCause(failure);
    verify(pool).getConnection();
    verify(connection).close();
    assertThat(time.sleeps).isEmpty();
    diagnostics(failure);
  }

  @Test
  void cancels_before_acquisition() throws SQLException {
    Thread.currentThread().interrupt();
    assertThatThrownBy(() -> startup().connect()).hasCauseInstanceOf(InterruptedException.class);
    verify(pool, never()).getConnection();
    assertCancelled();
  }

  @Test
  void cancels_during_backoff_preserving_the_interrupted_exception() throws SQLException {
    when(pool.getConnection()).thenThrow(locked());
    var interrupted = new InterruptedException("cancelled during sleep");
    var startup = new H2DatabaseStartup(pool, lockFile(), () -> time.nanos, nanos -> {
      throw interrupted;
    });
    assertThatThrownBy(startup::connect).hasCause(interrupted);
    verify(pool).getConnection();
    assertCancelled();
  }

  @Test
  void checks_interrupt_flag_before_retrying() throws SQLException {
    when(pool.getConnection()).thenThrow(locked());
    var startup = new H2DatabaseStartup(pool, lockFile(), () -> time.nanos, nanos -> Thread.currentThread().interrupt());
    assertThatThrownBy(startup::connect).hasCauseInstanceOf(InterruptedException.class);
    verify(pool).getConnection();
    assertCancelled();
  }

  @Test
  void cancels_when_acquisition_failure_has_an_interrupted_cause() throws SQLException {
    var failure = new SQLException("failed", "90020", 90020, new InterruptedException("interrupted"));
    when(pool.getConnection()).thenThrow(failure);
    assertThatThrownBy(() -> startup().connect()).hasCause(failure);
    assertThat(time.sleeps).isEmpty();
    assertCancelled();
  }

  @Test
  void cancels_and_returns_connection_when_acquisition_sets_interrupt() throws SQLException {
    when(pool.getConnection()).thenAnswer(invocation -> {
      Thread.currentThread().interrupt();
      return connection;
    });
    var cleanup = new SQLException("return failed");
    doThrow(cleanup).when(connection).close();
    assertThatThrownBy(() -> startup().connect()).cause().isInstanceOf(InterruptedException.class).hasSuppressedException(cleanup);
    verify(connection).close();
    assertCancelled();
  }

  @Test
  void cancels_after_returning_connection() throws SQLException {
    doAnswer(invocation -> {
      Thread.currentThread().interrupt();
      return null;
    }).when(connection).close();
    assertThatThrownBy(() -> startup().connect()).hasCauseInstanceOf(InterruptedException.class);
    assertCancelled();
  }

  @Test
  void preserves_original_database_failure_when_reporting_fails() throws SQLException {
    var failure = new SQLException("bad credentials", "28000", 28000);
    when(pool.getConnection()).thenThrow(failure);
    reporter.when(() -> DatabaseExceptionReporter.captureStartup(same(failure), any())).thenThrow(new IllegalStateException("reporting failed"));
    assertThatThrownBy(() -> startup().connect()).hasCause(failure);
    assertThat(failure.getSuppressed()).isEmpty();
  }

  @Test
  void captures_first_and_final_failure_lock_observations_without_sensitive_values() throws SQLException, IOException {
    var first = locked();
    var last = new SQLException("last failure", "28000", 28000);
    when(pool.getConnection()).thenAnswer(invocation -> {
      Files.writeString(lockFile(), "method=file\nid=secret-id\nhostName=private-host\n");
      throw first;
    }).thenAnswer(invocation -> {
      Files.writeString(lockFile(), "method=private-method\nserver=private-address:123\nid=secret-id\n");
      throw last;
    });
    assertThatThrownBy(() -> startup().connect()).hasCause(last);
    var diagnostics = diagnostics(last);
    assertThat(diagnostics.firstLock().method()).isEqualTo("file");
    assertThat(diagnostics.firstLock().serverPresent()).isFalse();
    assertThat(diagnostics.finalLock().method()).isEqualTo("other");
    assertThat(diagnostics.finalLock().serverPresent()).isTrue();
    assertThat(diagnostics.toString()).doesNotContain("private", "secret", root.toString());
  }

  @Test
  void observes_missing_malformed_oversized_and_future_dated_lock_files() throws IOException {
    assertThat(H2DatabaseStartup.observeLock(lockFile()).state()).isEqualTo("missing");
    Files.writeString(lockFile(), "method=\\uBAD!\nserver=private-host\n");
    assertThat(H2DatabaseStartup.observeLock(lockFile()).state()).isEqualTo("malformed");
    Files.writeString(lockFile(), "server=private-host\n");
    assertThat(H2DatabaseStartup.observeLock(lockFile()).state()).isEqualTo("malformed");
    Files.writeString(lockFile(), "method=file\n" + "a".repeat(16 * 1024));
    assertThat(H2DatabaseStartup.observeLock(lockFile()).state()).isEqualTo("unavailable");
    Files.writeString(lockFile(), "method=socket\nserver=private-host\n");
    Files.setLastModifiedTime(lockFile(), FileTime.fromMillis(System.currentTimeMillis() + 60_000));
    var observation = H2DatabaseStartup.observeLock(lockFile());
    assertThat(observation.state()).isEqualTo("readable");
    assertThat(observation.ageMillis()).isZero();
    assertThat(observation.toString()).doesNotContain("private-host");
  }

  @Test
  void distinguishes_unreadable_and_unavailable_diagnostics_without_exception_messages() throws IOException {
    try (var files = mockStatic(Files.class)) {
      files.when(() -> Files.readAttributes(lockFile(), BasicFileAttributes.class)).thenThrow(new AccessDeniedException("private-path"));
      assertThat(H2DatabaseStartup.observeLock(lockFile()).state()).isEqualTo("unreadable");
      files.when(() -> Files.readAttributes(lockFile(), BasicFileAttributes.class)).thenThrow(new IOException("private-path"));
      var observation = H2DatabaseStartup.observeLock(lockFile());
      assertThat(observation.state()).isEqualTo("unavailable");
      assertThat(observation.toString()).doesNotContain("private-path");
    }
  }

  @Test
  void preserves_database_failure_when_lock_diagnostics_are_unavailable() throws SQLException {
    var failure = new SQLException("bad credentials", "28000", 28000);
    when(pool.getConnection()).thenThrow(failure);
    try (var files = mockStatic(Files.class)) {
      files.when(() -> Files.readAttributes(lockFile(), BasicFileAttributes.class)).thenThrow(new SecurityException("private-path"));
      assertThatThrownBy(() -> startup().connect()).hasCause(failure);
      assertThat(diagnostics(failure).finalLock().state()).isEqualTo("unavailable");
      assertThat(failure.getSuppressed()).isEmpty();
    }
  }

  private H2DatabaseStartup startup() {
    return new H2DatabaseStartup(pool, lockFile(), () -> time.nanos, time::sleep);
  }

  private Path lockFile() {
    return root.resolve("sq-ide.lock.db");
  }

  private H2DatabaseStartup.Diagnostics diagnostics(Throwable failure) {
    var captor = ArgumentCaptor.forClass(H2DatabaseStartup.Diagnostics.class);
    reporter.verify(() -> DatabaseExceptionReporter.captureStartup(same(failure), captor.capture()));
    reporter.verifyNoMoreInteractions();
    return captor.getValue();
  }

  private void assertCancelled() {
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
    reporter.verifyNoInteractions();
    assertThat(logTester.logs(LogOutput.Level.ERROR)).isEmpty();
  }

  private static SQLException locked() {
    return new SQLException("database already in use", "90020", 90020);
  }

  private static class FakeTime {
    long nanos;
    final List<Long> sleeps = new ArrayList<>();

    void advance(long millis) {
      nanos += TimeUnit.MILLISECONDS.toNanos(millis);
    }

    void sleep(long duration) {
      sleeps.add(TimeUnit.NANOSECONDS.toMillis(duration));
      nanos += duration;
    }
  }
}
