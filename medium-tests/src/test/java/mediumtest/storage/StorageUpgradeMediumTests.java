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

import java.nio.file.Path;
import org.sonarsource.sonarlint.core.commons.KnownFinding;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.KnownFindingsRepository;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static mediumtest.storage.StorageSnapshot.CONFIG_SCOPE_ID;
import static mediumtest.storage.StorageSnapshot.CONNECTION_ID;
import static mediumtest.storage.StorageSnapshot.KNOWN_FINDINGS_FILE;
import static mediumtest.storage.StorageSnapshot.PROJECT_KEY;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opens the storage written by the latest release with the current code, to catch upgrade regressions such as an H2 version unable to read files written by the
 * previous one, or an edited migration that already shipped. The snapshot is written after a clean shutdown.
 */
class StorageUpgradeMediumTests {

  @SonarLintTest
  void should_start_on_storage_written_by_latest_release(SonarLintTestHarness harness) throws Exception {
    var snapshotDirectory = StorageSnapshot.latestSnapshotDirectory();
    var snapshot = StorageSnapshot.Metadata.read(snapshotDirectory);
    assertThat(StorageSnapshot.migrationFileChecksums())
      .as("Migrations shipped in %s must not be changed, add a new migration instead", snapshot.release())
      .containsAllEntriesOf(snapshot.checksumsByMigration());
    var client = harness.newFakeClient().build();

    // The connection must be declared, otherwise its data is purged at startup
    var backend = harness.newBackend()
      .withStorageSnapshot(snapshotDirectory)
      .withSonarQubeConnection(CONNECTION_ID)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start(client);

    var dsl = backend.getSonarLintDatabase().dsl();
    assertThat(StorageSnapshot.rowCounts(dsl))
      .as("Data written by %s (H2 %s) must be preserved", snapshot.release(), snapshot.h2Version())
      .containsAllEntriesOf(snapshot.rowsByTable());
    assertThat(backend.getLocalOnlyIssuesRepository().loadAll(CONFIG_SCOPE_ID))
      .extracting(LocalOnlyIssue::getRuleKey)
      .containsExactly("java:S1481");
    var knownFindingsRepository = new KnownFindingsRepository(backend.getSonarLintDatabase());
    assertThat(knownFindingsRepository.loadIssuesForFile(CONFIG_SCOPE_ID, Path.of(KNOWN_FINDINGS_FILE)))
      .extracting(KnownFinding::getServerKey)
      .containsExactly("issueKey1");
    assertThat(client.getLogMessages()).noneMatch(message -> message.contains("could not read its local database"));
  }
}
