/*
 * SonarLint Core - RPC Integration Tests
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
package integrationtests.storage;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.KnownFinding;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssue;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.serverconnection.aicodefix.AiCodeFix;
import org.sonarsource.sonarlint.core.serverconnection.aicodefix.AiCodeFixRepository;
import org.sonarsource.sonarlint.core.serverconnection.issues.KnownFindingsRepository;
import org.sonarsource.sonarlint.core.serverconnection.issues.RangeLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerDependencyRisk;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerFindingRepository;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static integrationtests.storage.StorageSnapshot.AI_CODEFIX_RULE;
import static integrationtests.storage.StorageSnapshot.BRANCH_NAME;
import static integrationtests.storage.StorageSnapshot.CONFIG_SCOPE_ID;
import static integrationtests.storage.StorageSnapshot.CONNECTION_ID;
import static integrationtests.storage.StorageSnapshot.CREATION_DATE;
import static integrationtests.storage.StorageSnapshot.DEPENDENCY_RISK_KEY;
import static integrationtests.storage.StorageSnapshot.KNOWN_FINDINGS_FILE;
import static integrationtests.storage.StorageSnapshot.PROJECT_KEY;
import static integrationtests.storage.StorageSnapshot.SERVER_FINDINGS_FILE;
import static integrationtests.storage.StorageSnapshot.TEXT_RANGE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opens the storage written by the latest release with the current code, to catch upgrade regressions such as an H2 version unable to read files written by the
 * previous one, or an edited migration that already shipped.
 */
class StorageUpgradeIntegrationTests {

  @Test
  void should_not_change_migrations_shipped_in_latest_release() throws Exception {
    var snapshot = StorageSnapshot.Metadata.read(StorageSnapshot.latestSnapshotDirectory());

    assertThat(StorageSnapshot.migrationFileChecksums())
      .as("Migrations shipped in %s must not be changed, add a new migration instead", snapshot.release())
      .containsAllEntriesOf(snapshot.checksumsByMigration());
  }

  @SonarLintTest
  void should_start_on_storage_written_by_latest_release(SonarLintTestHarness harness) throws Exception {
    var snapshotDirectory = StorageSnapshot.latestSnapshotDirectory();

    assertStartsAndPreservesData(harness, snapshotDirectory, snapshotDirectory);
  }

  @SonarLintTest
  void should_start_on_storage_left_by_a_crash_of_latest_release(SonarLintTestHarness harness) throws Exception {
    var snapshotDirectory = StorageSnapshot.latestSnapshotDirectory();

    assertStartsAndPreservesData(harness, snapshotDirectory, snapshotDirectory.resolve(StorageSnapshot.UNCLEAN_SHUTDOWN_DIRECTORY));
  }

  private static void assertStartsAndPreservesData(SonarLintTestHarness harness, Path snapshotDirectory, Path storageDirectory) throws Exception {
    var snapshot = StorageSnapshot.Metadata.read(snapshotDirectory);
    var client = harness.newFakeClient().build();

    // The connection must be declared, otherwise its data is purged at startup
    var backend = harness.newBackend()
      .withStorageSnapshot(storageDirectory)
      .withSonarQubeConnection(CONNECTION_ID)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start(client);

    var dsl = backend.getSonarLintDatabase().dsl();
    assertThat(StorageSnapshot.rowCounts(dsl))
      .as("Data committed by %s (H2 %s) must be preserved", snapshot.release(), snapshot.h2Version())
      .containsAllEntriesOf(snapshot.rowsByTable());
    assertThat(backend.getLocalOnlyIssuesRepository().loadAll(CONFIG_SCOPE_ID))
      .extracting(LocalOnlyIssue::getRuleKey)
      .containsExactly("java:S1481");
    var knownFindingsRepository = new KnownFindingsRepository(backend.getSonarLintDatabase());
    assertThat(knownFindingsRepository.loadIssuesForFile(CONFIG_SCOPE_ID, Path.of(KNOWN_FINDINGS_FILE)))
      .extracting(KnownFinding::getServerKey)
      .containsExactly("issueKey1");
    assertReadsServerData(dsl);
    assertThat(client.getLogMessages()).noneMatch(message -> message.contains("could not read its local database"));
  }

  /**
   * Row counts do not catch a value that the current code can no longer map, such as a renamed enum constant or a changed JSON format. Some of these mappings
   * log and fall back to a default instead of failing, so the values are checked too.
   */
  private static void assertReadsServerData(DSLContext dsl) {
    var serverFindingRepository = new ServerFindingRepository(dsl, CONNECTION_ID, PROJECT_KEY);

    var bug = serverFindingRepository.getIssue("issueKey1");
    assertThat(bug).isInstanceOf(RangeLevelServerIssue.class);
    assertThat(bug.getType()).isEqualTo(RuleType.BUG);
    assertThat(bug.getRuleKey()).isEqualTo("ruleKey");
    assertThat(bug.getMessage()).isEqualTo("message");
    assertThat(bug.getFilePath()).isEqualTo(Path.of(SERVER_FINDINGS_FILE));
    assertThat(bug.getCreationDate()).isEqualTo(CREATION_DATE);
    assertThat(bug.isResolved()).isFalse();
    assertThat(bug.getResolutionStatus()).isNull();
    assertThat(bug.getImpacts()).isEmpty();
    assertThat(((RangeLevelServerIssue) bug).getTextRange()).isEqualTo(TEXT_RANGE);
    assertThat(serverFindingRepository.getIssue("issueKey2").getType()).isEqualTo(RuleType.CODE_SMELL);

    var hotspot = serverFindingRepository.getHotspot("hotspotKey");
    assertThat(hotspot).isNotNull();
    assertThat(hotspot.getFilePath()).isEqualTo(Path.of(SERVER_FINDINGS_FILE));
    assertThat(hotspot.getCreationDate()).isEqualTo(CREATION_DATE);
    assertThat(hotspot.getTextRange()).isEqualTo(TEXT_RANGE);
    assertThat(hotspot.getStatus()).isEqualTo(HotspotReviewStatus.TO_REVIEW);
    assertThat(hotspot.getVulnerabilityProbability()).isEqualTo(VulnerabilityProbability.MEDIUM);

    assertThat(serverFindingRepository.loadDependencyRisks(BRANCH_NAME))
      .usingRecursiveFieldByFieldElementComparator()
      .containsExactly(new ServerDependencyRisk(DEPENDENCY_RISK_KEY, ServerDependencyRisk.Type.VULNERABILITY, ServerDependencyRisk.Severity.HIGH,
        ServerDependencyRisk.SoftwareQuality.SECURITY, ServerDependencyRisk.Status.OPEN, "com.example.vulnerable", "2.1.0", "CVE-1234", "7.5",
        List.of(ServerDependencyRisk.Transition.CONFIRM, ServerDependencyRisk.Transition.ACCEPT)));

    assertThat(serverFindingRepository.wasEverUpdated()).isTrue();
    assertThat(serverFindingRepository.getLastIssueSyncTimestamp(BRANCH_NAME)).isPresent();
    assertThat(serverFindingRepository.getLastHotspotSyncTimestamp(BRANCH_NAME)).isPresent();
    assertThat(serverFindingRepository.getLastTaintSyncTimestamp(BRANCH_NAME)).isPresent();

    assertThat(new AiCodeFixRepository(dsl).get(CONNECTION_ID))
      .contains(new AiCodeFix(CONNECTION_ID, Set.of(AI_CODEFIX_RULE), true, AiCodeFix.Enablement.ENABLED_FOR_SOME_PROJECTS, Set.of(PROJECT_KEY)));
  }
}
