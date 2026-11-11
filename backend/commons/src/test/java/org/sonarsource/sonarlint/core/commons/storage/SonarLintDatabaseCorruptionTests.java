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

import java.nio.channels.ClosedByInterruptException;
import java.sql.SQLException;
import org.flywaydb.core.internal.exception.FlywaySqlException;
import org.h2.api.ErrorCode;
import org.h2.mvstore.DataUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SonarLintDatabaseCorruptionTests {

  @Test
  void should_detect_corruption_errors() {
    assertThat(SonarLintDatabase.isCorruption(new FlywaySqlException("Unable to obtain connection",
      new SQLException("File corrupted while reading record", "HY000", ErrorCode.FILE_CORRUPTED_1)))).isTrue();
    assertThat(SonarLintDatabase.isCorruption(new IllegalStateException(
      DataUtils.newMVStoreException(DataUtils.ERROR_FILE_CORRUPT, "Unable to read the page at position {0}", 42)))).isTrue();
    assertThat(SonarLintDatabase.isCorruption(new IllegalStateException(
      DataUtils.newMVStoreException(DataUtils.ERROR_CHUNK_NOT_FOUND, "Chunk {0} not found", 42)))).isTrue();
  }

  @Test
  void should_detect_corruption_from_wrapped_message_only() {
    // From SONARLINT-CORE-4PQ, H2 does not always keep the MVStoreException as a cause
    assertThat(SonarLintDatabase.isCorruption(new FlywaySqlException("Unable to determine the original schema for the connection",
      new SQLException("General error: \"General error: \"\"org.h2.mvstore.MVStoreException: Unable to read the page at position 0x23e7c00004149a, offset "
        + "0xorg.h2.message.DbException: File corrupted while reading record: \"\"\"\"type: 161\"\"\"\". Possible solution: use the recovery tool "
        + "[90030-252] [2.5.252/6]\"\" [50000-252]\" [50000-252]", "HY000", ErrorCode.GENERAL_ERROR_1)))).isTrue();
  }

  @Test
  void should_not_detect_errors_on_a_healthy_file_as_corruption() {
    var locked = DataUtils.newMVStoreException(DataUtils.ERROR_FILE_LOCKED, "The file is locked: {0}", "sq-ide.mv.db");
    assertThat(SonarLintDatabase.isCorruption(new FlywaySqlException("Unable to obtain connection",
      new SQLException("Database may be already in use", "HY000", ErrorCode.DATABASE_ALREADY_OPEN_1, locked)))).isFalse();

    var closed = DataUtils.newMVStoreException(DataUtils.ERROR_CLOSED, "This map is closed");
    assertThat(SonarLintDatabase.isCorruption(new SQLException("Database is already closed", "90121", ErrorCode.DATABASE_IS_CLOSED, closed))).isFalse();

    // From SONARLINT-CORE-29G, e.g. when the disk is full
    assertThat(SonarLintDatabase.isCorruption(new FlywaySqlException("Unable to obtain connection",
      new SQLException("General error: \"General error: \"\"org.h2.mvstore.MVStoreException: Writing to sun.nio.ch.FileChannelImpl@75f3b225 failed; "
        + "length 12288 at 8192 [2.5.252/2]\"\" [50000-252]\" [50000-252]", "HY000", ErrorCode.GENERAL_ERROR_1)))).isFalse();

    assertThat(SonarLintDatabase.isCorruption(new FlywaySqlException("Unable to execute statement",
      new SQLException("General error: \"java.lang.OutOfMemoryError\"", "HY000", ErrorCode.GENERAL_ERROR_1)))).isFalse();
    assertThat(SonarLintDatabase.isCorruption(new IllegalStateException("Failed to initialize H2Database"))).isFalse();
  }

  @Test
  void should_not_detect_read_failures_caused_by_an_interrupted_thread_as_corruption() {
    // From SONARLINT-CORE-28N
    var readFailure = DataUtils.newMVStoreException(DataUtils.ERROR_READING_FAILED, "Reading from file {0} failed at {1}", "sun.nio.ch.FileChannelImpl", 19808183,
      new ClosedByInterruptException());

    assertThat(SonarLintDatabase.isCorruption(new SQLException("General error: \"" + readFailure + "\"", "HY000", ErrorCode.GENERAL_ERROR_1, readFailure))).isFalse();
  }

}
