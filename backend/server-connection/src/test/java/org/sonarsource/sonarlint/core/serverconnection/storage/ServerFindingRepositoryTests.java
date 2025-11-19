/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection.storage;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.IssueStatus;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.serverconnection.issues.LineLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.RangeLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerDependencyRisk;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class ServerFindingRepositoryTests {

  private static final long INSTANT_TOLERANCE_MS = 1500;

  @RegisterExtension
  static SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path tempDir;

  private ServerFindingRepository repo;
  private String branch;
  private Path filePath;
  private SonarLintDatabase db;

  @BeforeEach
  void setUp() {
    var storageRoot = tempDir.resolve("storage");
    db = new SonarLintDatabase(storageRoot);
    repo = new ServerFindingRepository(db.dsl(), "conn-1", "project-1");
    branch = "main";
    filePath = Path.of("/file/path");
  }

  @AfterEach
  void tearDown() {
    if (repo != null) {
      db.shutdown();
    }
  }

  @Test
  void hotspots_replace_load_get_change_update_delete() {
    var h1 = hotspot("HOTSPOT_KEY_1", filePath, 1, HotspotReviewStatus.TO_REVIEW, VulnerabilityProbability.MEDIUM, null);
    var h2 = hotspot("HOTSPOT_KEY_2", filePath, 2, HotspotReviewStatus.TO_REVIEW, VulnerabilityProbability.HIGH, "john.doe");

    repo.replaceAllHotspotsOfFile(branch, filePath, List.of(h1, h2));

    var loaded = repo.loadHotspots(branch, filePath);
    assertThat(loaded).hasSize(2);
    var loadedH1 = loaded.stream().filter(h -> h.getKey().equals(h1.getKey())).findFirst().orElseThrow();
    var loadedH2 = loaded.stream().filter(h -> h.getKey().equals(h2.getKey())).findFirst().orElseThrow();
    assertHotspotEquals(h1, loadedH1);
    assertHotspotEquals(h2, loadedH2);

    var fetched = repo.getHotspot("HOTSPOT_KEY_2");
    assertThat(fetched).isNotNull();
    assertHotspotEquals(h2, fetched);

    assertThat(repo.changeHotspotStatus("HOTSPOT_KEY_1", HotspotReviewStatus.SAFE)).isTrue();
    var afterStatus = repo.getHotspot("HOTSPOT_KEY_1");
    var expectedAfterStatus = new ServerHotspot(h1.getId(), h1.getKey(), h1.getRuleKey(), h1.getMessage(), h1.getFilePath(), h1.getTextRange(), h1.getCreationDate(),
      HotspotReviewStatus.SAFE, h1.getVulnerabilityProbability(), h1.getAssignee());
    assertHotspotEquals(expectedAfterStatus, afterStatus);

    repo.updateHotspot("HOTSPOT_KEY_2", hs -> { /* no-op */ });
    var afterUpdate = repo.getHotspot("HOTSPOT_KEY_2");
    assertHotspotEquals(h2, afterUpdate);

    repo.deleteHotspot("HOTSPOT_KEY_1");
    assertThat(repo.getHotspot("HOTSPOT_KEY_1")).isNull();
  }

  @Test
  void hotspots_replace_for_branch_load_get_change_update_delete() {
    var h1 = hotspot("HOTSPOT_KEY_1", filePath, 1, HotspotReviewStatus.TO_REVIEW, VulnerabilityProbability.MEDIUM, null);
    var h2 = hotspot("HOTSPOT_KEY_2", filePath, 2, HotspotReviewStatus.TO_REVIEW, VulnerabilityProbability.HIGH, "john.doe");

    repo.replaceAllHotspotsOfBranch(branch, List.of(h1, h2), Set.of());

    var loaded = repo.loadHotspots(branch, filePath);
    assertThat(loaded).hasSize(2);
    var loadedH1 = loaded.stream().filter(h -> h.getKey().equals(h1.getKey())).findFirst().orElseThrow();
    var loadedH2 = loaded.stream().filter(h -> h.getKey().equals(h2.getKey())).findFirst().orElseThrow();
    assertHotspotEquals(h1, loadedH1);
    assertHotspotEquals(h2, loadedH2);

    var fetched = repo.getHotspot("HOTSPOT_KEY_2");
    assertThat(fetched).isNotNull();
    assertHotspotEquals(h2, fetched);

    assertThat(repo.changeHotspotStatus("HOTSPOT_KEY_1", HotspotReviewStatus.SAFE)).isTrue();
    var afterStatus = repo.getHotspot("HOTSPOT_KEY_1");
    var expectedAfterStatus = new ServerHotspot(h1.getId(), h1.getKey(), h1.getRuleKey(), h1.getMessage(), h1.getFilePath(), h1.getTextRange(), h1.getCreationDate(),
      HotspotReviewStatus.SAFE, h1.getVulnerabilityProbability(), h1.getAssignee());
    assertHotspotEquals(expectedAfterStatus, afterStatus);

    repo.updateHotspot("HOTSPOT_KEY_2", hs -> { /* no-op */ });
    var afterUpdate = repo.getHotspot("HOTSPOT_KEY_2");
    assertHotspotEquals(h2, afterUpdate);

    repo.deleteHotspot("HOTSPOT_KEY_1");
    assertThat(repo.getHotspot("HOTSPOT_KEY_1")).isNull();
  }

  @Test
  void server_dependency_risk() {
    var serverDependencyRisk1 = dependencyRisk();

    repo.replaceAllDependencyRisksOfBranch(branch, List.of(serverDependencyRisk1));

    var serverDependencyRisks = repo.loadDependencyRisks(branch);

    assertThat(serverDependencyRisks).hasSize(1);
    assertThat(serverDependencyRisks.get(0)).isEqualTo(serverDependencyRisk1);
  }

  @Test
  void issues_update() {
    var issueKey = "ISSUE_KEY";
    var file = Path.of("/file/path");
    var issue = rangeIssue(issueKey, file, new TextRangeWithHash(1, 10, 1, 20, "hash"));

    repo.replaceAllIssuesOfFile(branch, file, List.of(issue));
    var loadedIssue = repo.getIssue(issueKey);
    assertRangeIssueEquals(issue, (RangeLevelServerIssue) loadedIssue);

    repo.updateIssue(issueKey, issueToUpdate -> issueToUpdate.setUserSeverity(IssueSeverity.MAJOR));
    loadedIssue = repo.getIssue(issueKey);
    assertThat(loadedIssue.getUserSeverity()).isEqualTo(IssueSeverity.MAJOR);
  }

  @Test
  void replace_all_issues_of_branch() {
    var issueKey = "ISSUE_KEY";
    var file = Path.of("/file/path");
    var issue = rangeIssue(issueKey, file, new TextRangeWithHash(1, 10, 1, 20, "hash"));

    repo.replaceAllIssuesOfBranch(branch, List.of(issue), Set.of());

    var loadedIssue = repo.getIssue(issueKey);
    assertRangeIssueEquals(issue, (RangeLevelServerIssue) loadedIssue);
  }

  @Test
  void was_ever_updated() {
    var issueKey = "ISSUE_KEY";
    var file = Path.of("/file/path");
    var issue = rangeIssue(issueKey, file, new TextRangeWithHash(1, 10, 1, 20, "hash"));

    repo.replaceAllIssuesOfBranch(branch, List.of(issue), Set.of());
    assertThat(repo.wasEverUpdated()).isTrue();
  }

  @Test
  void was_ever_updated_for_no_issues() {
    repo.replaceAllIssuesOfBranch(branch, List.of(), Set.of());

    assertThat(repo.wasEverUpdated()).isTrue();
  }

  @Test
  void was_never_updated() {
    assertThat(repo.wasEverUpdated()).isFalse();
  }

  @Test
  void was_ever_updated_when_only_hotspots_synced() {
    var h1 = hotspot("HOTSPOT_KEY_1", filePath, 1, HotspotReviewStatus.TO_REVIEW, VulnerabilityProbability.MEDIUM, null);
    repo.mergeHotspots(branch, List.of(h1), Set.of(), Instant.now(), Set.of());

    assertThat(repo.wasEverUpdated()).isTrue();
  }

  @Test
  void was_ever_updated_when_only_taints_synced() {
    var t1 = taint("TAINT_KEY_1", filePath);
    repo.mergeTaintIssues(branch, List.of(t1), Set.of(), Instant.now(), Set.of());

    assertThat(repo.wasEverUpdated()).isTrue();
  }

  @Test
  void update_issue_resolution_status() {
    var issueKey = "ISSUE_KEY";
    var file = Path.of("/file/path");
    var issue = rangeIssue(issueKey, file, new TextRangeWithHash(1, 10, 1, 20, "hash"));
    repo.replaceAllIssuesOfFile(branch, file, List.of(issue));
    assertThat(issue.isResolved()).isFalse();
    var loadedIssue = repo.getIssue(issueKey);
    assertRangeIssueEquals(issue, (RangeLevelServerIssue) loadedIssue);

    var serverFinding = repo.updateIssueResolutionStatus(issueKey, false, true);
    assertThat(serverFinding).isPresent();

    var repoIssue = repo.getIssue(issueKey);
    assertThat(repoIssue.isResolved()).isTrue();
  }

  @Test
  void taints_replace_load_insert_delete_update() {
    var t1 = taint("TAINT_KEY_1", filePath);
    repo.replaceAllTaintsOfBranch(branch, List.of(t1), Set.of());

    var loaded = repo.loadTaint(branch);
    assertThat(loaded).hasSize(1);
    assertTaintEquals(t1, loaded.get(0));

    var t2 = taint("TAINT_KEY_2", filePath);
    repo.insert(branch, t2);
    loaded = repo.loadTaint(branch);
    assertThat(loaded).hasSize(2);
    var loadedT1 = loaded.stream().filter(t -> t.getSonarServerKey().equals("TAINT_KEY_1")).findFirst().orElseThrow();
    var loadedT2 = loaded.stream().filter(t -> t.getSonarServerKey().equals("TAINT_KEY_2")).findFirst().orElseThrow();
    assertTaintEquals(t1, loadedT1);
    assertTaintEquals(t2, loadedT2);

    var deletedId = repo.deleteTaintIssueBySonarServerKey("TAINT_KEY_2");
    assertThat(deletedId).isPresent();
    loaded = repo.loadTaint(branch);
    assertThat(loaded).hasSize(1);
    assertTaintEquals(t1, loaded.get(0));

    assertThat(repo.updateTaintIssueBySonarServerKey("TAINT_KEY_1", t -> { /* no-op */ })).isPresent();
    var afterUpdate = repo.loadTaint(branch).get(0);
    assertTaintEquals(t1, afterUpdate);
  }

  @Test
  void merge_issues_removes_closed_and_upserts() {
    var newIssue = lineIssue("ISSUE_KEY_4", filePath, 2);
    repo.mergeIssues(branch, List.of(newIssue), Set.of("ISSUE_KEY_1"), Instant.now(), Set.of());

    var afterMerge = repo.load(branch, filePath);
    assertThat(afterMerge.stream().anyMatch(i -> i.getKey().equals("ISSUE_KEY_1"))).isFalse();
    assertThat(afterMerge.stream().anyMatch(i -> i.getKey().equals("ISSUE_KEY_4"))).isTrue();
  }

  @Test
  void merge_taints_removes_closed_and_upserts() {
    var t3 = new ServerTaintIssue(UUID.randomUUID(), "TAINT_KEY_3", false, null, "rule", "msg", filePath,
      Instant.now(), IssueSeverity.MINOR, RuleType.CODE_SMELL, null, null, null, Map.of(), List.of());
    repo.mergeTaintIssues(branch, List.of(t3), Set.of("TAINT_KEY_1"), Instant.now(), Set.of());

    var afterMerge = repo.loadTaint(branch);
    assertThat(afterMerge.stream().anyMatch(t -> t.getSonarServerKey().equals("TAINT_KEY_1"))).isFalse();
    assertThat(afterMerge.stream().anyMatch(t -> t.getSonarServerKey().equals("TAINT_KEY_3"))).isTrue();
  }

  @Test
  void merge_hotspots_removes_closed_and_upserts() {
    var h3 = new ServerHotspot(UUID.randomUUID(), "HOTSPOT_KEY_3", "rule", "msg", filePath,
      new TextRange(4, 0, 4, 1), Instant.now(), HotspotReviewStatus.TO_REVIEW, VulnerabilityProbability.LOW, null);
    repo.mergeHotspots(branch, List.of(h3), Set.of("HOTSPOT_KEY_2"), Instant.now(), Set.of());

    var afterMerge = repo.loadHotspots(branch, filePath);
    assertThat(afterMerge.stream().anyMatch(h -> h.getKey().equals("HOTSPOT_KEY_2"))).isFalse();
    assertThat(afterMerge.stream().anyMatch(h -> h.getKey().equals("HOTSPOT_KEY_3"))).isTrue();
  }

  @Test
  void branch_metadata_is_stored_during_merges() {
    // perform one merge for each type to set metadata
    repo.mergeIssues(branch, List.of(lineIssue("ISSUE_KEY_X", filePath, 1)), Set.of(), Instant.now(), Set.of());
    repo.mergeTaintIssues(branch, List.of(taint("TAINT_KEY_X", filePath)), Set.of(), Instant.now(), Set.of());
    repo.mergeHotspots(branch, List.of(hotspot("HOTSPOT_KEY_X", filePath, 1, HotspotReviewStatus.TO_REVIEW, VulnerabilityProbability.LOW, null)), Set.of(), Instant.now(),
      Set.of());

    assertThat(repo.getLastIssueSyncTimestamp(branch)).isPresent();
    assertThat(repo.getLastTaintSyncTimestamp(branch)).isPresent();
    assertThat(repo.getLastHotspotSyncTimestamp(branch)).isPresent();

    assertThat(repo.getLastIssueEnabledLanguages(branch)).isEmpty();
    assertThat(repo.getLastHotspotEnabledLanguages(branch)).isEmpty();
    assertThat(repo.getLastTaintEnabledLanguages(branch)).isEmpty();
  }

  // Helpers
  private static void assertInstantsClose(Instant expected, Instant actual) {
    long diff = Math.abs(expected.toEpochMilli() - actual.toEpochMilli());
    assertThat(diff).isLessThan(INSTANT_TOLERANCE_MS);
  }

  private static void assertTextRangeEquals(TextRange expected, TextRange actual) {
    assertThat(actual.getStartLine()).isEqualTo(expected.getStartLine());
    assertThat(actual.getStartLineOffset()).isEqualTo(expected.getStartLineOffset());
    assertThat(actual.getEndLine()).isEqualTo(expected.getEndLine());
    assertThat(actual.getEndLineOffset()).isEqualTo(expected.getEndLineOffset());
  }

  private static void assertTextRangeWithHashEquals(TextRangeWithHash expected, TextRangeWithHash actual) {
    assertThat(actual.getHash()).isEqualTo(expected.getHash());
    assertTextRangeEquals(expected, actual);
  }

  private static void assertHotspotEquals(ServerHotspot expected, ServerHotspot actual) {
    assertThat(actual.getId()).isEqualTo(expected.getId());
    assertThat(actual.getKey()).isEqualTo(expected.getKey());
    assertThat(actual.getRuleKey()).isEqualTo(expected.getRuleKey());
    assertThat(actual.getMessage()).isEqualTo(expected.getMessage());
    assertThat(actual.getFilePath()).isEqualTo(expected.getFilePath());
    assertTextRangeEquals(expected.getTextRange(), actual.getTextRange());
    assertInstantsClose(expected.getCreationDate(), actual.getCreationDate());
    assertThat(actual.getStatus()).isEqualTo(expected.getStatus());
    assertThat(actual.getVulnerabilityProbability()).isEqualTo(expected.getVulnerabilityProbability());
    assertThat(actual.getAssignee()).isEqualTo(expected.getAssignee());
  }

  private static void assertTaintEquals(ServerTaintIssue expected, ServerTaintIssue actual) {
    assertThat(actual.getId()).isEqualTo(expected.getId());
    assertThat(actual.getSonarServerKey()).isEqualTo(expected.getSonarServerKey());
    assertThat(actual.getRuleKey()).isEqualTo(expected.getRuleKey());
    assertThat(actual.getMessage()).isEqualTo(expected.getMessage());
    assertThat(actual.getFilePath()).isEqualTo(expected.getFilePath());
    assertInstantsClose(expected.getCreationDate(), actual.getCreationDate());
    assertThat(actual.getSeverity()).isEqualTo(expected.getSeverity());
    assertThat(actual.getType()).isEqualTo(expected.getType());
    if (expected.getTextRange() != null && actual.getTextRange() != null) {
      assertTextRangeWithHashEquals(expected.getTextRange(), actual.getTextRange());
    } else {
      assertThat(actual.getTextRange()).isNull();
      assertThat(expected.getTextRange()).isNull();
    }
  }

  private static void assertRangeIssueEquals(RangeLevelServerIssue expected, RangeLevelServerIssue actual) {
    assertThat(actual.getId()).isEqualTo(expected.getId());
    assertThat(actual.getKey()).isEqualTo(expected.getKey());
    assertThat(actual.getRuleKey()).isEqualTo(expected.getRuleKey());
    assertThat(actual.getMessage()).isEqualTo(expected.getMessage());
    assertThat(actual.getFilePath()).isEqualTo(expected.getFilePath());
    assertInstantsClose(expected.getCreationDate(), actual.getCreationDate());
    assertThat(actual.getUserSeverity()).isEqualTo(expected.getUserSeverity());
    assertThat(actual.getType()).isEqualTo(expected.getType());
    assertTextRangeWithHashEquals(expected.getTextRange(), actual.getTextRange());
  }

  private static ServerHotspot hotspot(String key, Path file, int startLine, HotspotReviewStatus status, VulnerabilityProbability prob, String assignee) {
    return new ServerHotspot(UUID.randomUUID(), key, "hotspot:rule-" + key, "Hotspot Message " + key, file,
      new TextRange(startLine, 0, startLine, 10), Instant.now(), status, prob, assignee);
  }

  private static ServerTaintIssue taint(String key, Path file) {
    return new ServerTaintIssue(UUID.randomUUID(), key, false, null, "java:S" + Math.abs(key.hashCode() % 1000),
      "Taint message " + key, file, Instant.now(), IssueSeverity.MAJOR, RuleType.SECURITY_HOTSPOT,
      new TextRangeWithHash(3, 1, 3, 5, "hash-" + key), null, null, Map.of(), List.of());
  }

  private static LineLevelServerIssue lineIssue(String key, Path file, int line) {
    return new LineLevelServerIssue(UUID.randomUUID(), key, false, null, "rule", "msg", "h", file, Instant.now(),
      IssueSeverity.MINOR, RuleType.CODE_SMELL, line, Map.of());
  }

  private static RangeLevelServerIssue rangeIssue(String key, Path file, TextRangeWithHash range) {
    return new RangeLevelServerIssue(UUID.randomUUID(), key, false, IssueStatus.ACCEPT, "ruleKey",
      "message", file, Instant.now(), IssueSeverity.MINOR, RuleType.CODE_SMELL, range, Map.of());
  }

  private static ServerDependencyRisk dependencyRisk() {
    return new ServerDependencyRisk(UUID.randomUUID(), ServerDependencyRisk.Type.VULNERABILITY, ServerDependencyRisk.Severity.HIGH, ServerDependencyRisk.SoftwareQuality.SECURITY,
      ServerDependencyRisk.Status.ACCEPT, "package", "version", "vulnerabilityId", "cvssScore",
      List.of(ServerDependencyRisk.Transition.REOPEN, ServerDependencyRisk.Transition.CONFIRM));
  }
}
