/*
 * SonarLint Core - Medium Tests
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
package mediumtest.storage;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static mediumtest.storage.StorageSnapshot.CONFIG_SCOPE_ID;
import static mediumtest.storage.StorageSnapshot.CONNECTION_ID;
import static mediumtest.storage.StorageSnapshot.PROJECT_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Starts the backend on a damaged copy of the storage written by the latest release
 */
class StorageCorruptionMediumTests {

  private static final String DB_FILE_NAME = SonarLintDatabase.SQ_IDE_DB_FILENAME + ".mv.db";

  enum Damage {
    // Both file headers, where H2 finds the latest chunk
    OVERWRITTEN_HEADERS {
      @Override
      void apply(RandomAccessFile file) throws IOException {
        file.seek(0);
        file.write(garbage(8192));
      }
    },
    TRUNCATED_TO_HALF {
      @Override
      void apply(RandomAccessFile file) throws IOException {
        file.setLength(file.length() / 2);
      }
    },
    // Only the first file header is left
    TRUNCATED_TO_FIRST_HEADER {
      @Override
      void apply(RandomAccessFile file) throws IOException {
        file.setLength(4096);
      }
    };

    abstract void apply(RandomAccessFile file) throws IOException;

    private static byte[] garbage(int length) {
      var bytes = new byte[length];
      // Fixed seed, so that every run reads the same file
      new Random(42).nextBytes(bytes);
      return bytes;
    }
  }

  @ParameterizedTest
  @EnumSource(Damage.class)
  @ExtendWith(SonarLintTestHarness.class)
  void should_tell_how_to_recover_when_the_database_is_corrupted(Damage damage, SonarLintTestHarness harness, @TempDir Path tempDir) throws Exception {
    var snapshot = damagedCopyOfLatestSnapshot(tempDir.resolve("snapshot"), damage);
    var storageRoot = tempDir.resolve("storage");
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withStorageRoot(storageRoot)
      .withStorageSnapshot(snapshot)
      .withSonarQubeConnection(CONNECTION_ID)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY);

    assertThatThrownBy(() -> backend.start(client)).hasMessage("Cannot initialize the backend");

    var dbFile = storageRoot.resolve("h2").resolve(DB_FILE_NAME).toAbsolutePath();
    await().untilAsserted(() -> assertThat(client.getLogMessages())
      .contains("SonarQube for IDE could not read its local database. Close all IDE instances, delete '" + dbFile
        + "', and restart your IDE. Cached server data will be downloaded again when connected."));
    assertThat(dbFile).exists();
  }

  @SonarLintTest
  void should_start_from_scratch_when_the_database_file_is_empty(SonarLintTestHarness harness, @TempDir Path tempDir) throws Exception {
    var snapshot = tempDir.resolve("snapshot");
    Files.createDirectories(snapshot.resolve("h2"));
    Files.createFile(snapshot.resolve("h2").resolve(DB_FILE_NAME));

    var backend = harness.newBackend()
      .withStorageSnapshot(snapshot)
      .withSonarQubeConnection(CONNECTION_ID)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var dsl = backend.getSonarLintDatabase().dsl();
    assertThat(StorageSnapshot.rowCounts(dsl)).isNotEmpty().allSatisfy((table, rows) -> assertThat(rows).as(table).isZero());
    assertThat(StorageSnapshot.appliedMigrationChecksums(dsl)).containsAllEntriesOf(StorageSnapshot.migrationFileChecksums());
  }

  private static Path damagedCopyOfLatestSnapshot(Path target, Damage damage) throws Exception {
    FileUtils.copyDirectory(StorageSnapshot.latestSnapshotDirectory().resolve("h2").toFile(), target.resolve("h2").toFile());
    try (var file = new RandomAccessFile(target.resolve("h2").resolve(DB_FILE_NAME).toFile(), "rw")) {
      damage.apply(file);
    }
    return target;
  }
}
