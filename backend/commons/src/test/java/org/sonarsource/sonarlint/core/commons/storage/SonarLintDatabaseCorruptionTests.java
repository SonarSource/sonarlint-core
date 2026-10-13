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

import io.sentry.Sentry;
import io.sentry.logger.ILoggerApi;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Random;
import org.flywaydb.core.internal.exception.FlywaySqlException;
import org.h2.api.ErrorCode;
import org.h2.mvstore.MVStoreException;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class SonarLintDatabaseCorruptionTests {

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
  void should_log_how_to_fix_a_corrupted_database_and_fail(@TempDir Path storageRoot) throws IOException {
    new SonarLintDatabase(storageRoot).shutdown();
    var dbFile = storageRoot.resolve("h2").resolve(SonarLintDatabase.SQ_IDE_DB_FILENAME + ".mv.db");
    corrupt(dbFile);

    assertThatThrownBy(() -> new SonarLintDatabase(storageRoot))
      .satisfies(e -> assertThat(SonarLintDatabase.isCorruption(e)).isTrue());
    assertThat(logTester.logs(LogOutput.Level.ERROR))
      .containsExactly("The local database seems to be corrupted. We recommend closing all IDEs, then deleting the H2 database used by SonarQube for IDE: "
        + dbFile.toAbsolutePath());
    assertThat(dbFile).exists();
  }

  @Test
  void should_detect_corruption_errors() {
    assertThat(SonarLintDatabase.isCorruption(new FlywaySqlException("Unable to obtain connection",
      new SQLException("File corrupted while reading record", "HY000", ErrorCode.FILE_CORRUPTED_1)))).isTrue();
    assertThat(SonarLintDatabase.isCorruption(new FlywaySqlException("Unable to check whether schema \"PUBLIC\" exists",
      new SQLException("General error: \"org.h2.mvstore.MVStoreException: Unable to read the page\"", "HY000", ErrorCode.GENERAL_ERROR_1)))).isTrue();
    assertThat(SonarLintDatabase.isCorruption(new IllegalStateException(new MVStoreException(0, "Unable to read the page")))).isTrue();
  }

  @Test
  void should_not_detect_other_errors_as_corruption() {
    assertThat(SonarLintDatabase.isCorruption(new FlywaySqlException("Unable to obtain connection",
      new SQLException("Database may be already in use", "HY000", ErrorCode.DATABASE_ALREADY_OPEN_1)))).isFalse();
    assertThat(SonarLintDatabase.isCorruption(new FlywaySqlException("Unable to execute statement",
      new SQLException("General error: \"java.lang.OutOfMemoryError\"", "HY000", ErrorCode.GENERAL_ERROR_1)))).isFalse();
    assertThat(SonarLintDatabase.isCorruption(new IllegalStateException("Failed to initialize H2Database"))).isFalse();
  }

  /**
   * Overwrite the two file headers, so that H2 fails with {@link ErrorCode#FILE_CORRUPTED_1} on open.
   * Damaging only the chunks is not enough: H2 then silently falls back to an empty store.
   */
  private static void corrupt(Path dbFile) throws IOException {
    var garbage = new byte[8192];
    new Random(42).nextBytes(garbage);
    try (var file = new RandomAccessFile(dbFile.toFile(), "rw")) {
      file.write(garbage);
    }
  }

}
