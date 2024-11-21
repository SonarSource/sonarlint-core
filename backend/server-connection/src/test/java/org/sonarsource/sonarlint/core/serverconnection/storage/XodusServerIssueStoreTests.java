/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2024 SonarSource SA
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.log.LogOutput.Level;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.serverconnection.FileUtils;
import org.sonarsource.sonarlint.core.serverconnection.issues.FileLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.LineLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.RangeLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue.Flow;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue.ServerIssueLocation;

import static java.time.temporal.ChronoUnit.MILLIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ServerHotspotFixtures.aServerHotspot;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueFixtures.aBatchServerIssue;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueFixtures.aFileLevelServerIssue;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueFixtures.aServerIssue;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueFixtures.aServerTaintIssue;

class XodusServerIssueStoreTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private final Path filePath = Path.of("file/path");
  private final Path filePath1 = Path.of("file/path1");
  private final Path filePath2 = Path.of("file/path2");
  private final Path filePathNoSlash = Path.of("filePath");
  private final Path filePathNoSlash1 = Path.of("filePath1");
  private final Path filePathNoSlash2 = Path.of("filePath2");
  
  @TempDir
  Path workDir;
  @TempDir
  Path backupDir;
  private XodusServerIssueStore store;

  @BeforeEach
  void setUp() throws IOException {
    store = new XodusServerIssueStore(backupDir, workDir);
  }

  @AfterEach
  void tearDown() {
    store.close();
  }

  @Test
  void should_return_empty_when_file_path_unknown() {
    var issues = store.load("branch", Path.of("path"));

    assertThat(issues).isEmpty();
  }

  @Test
  void should_save_a_batch_issue() {
    var creationDate = Instant.now();

    store
      .replaceAllIssuesOfBranch("branch", List.of(aBatchServerIssue().setFilePath(filePath).setCreationDate(creationDate)));

    var savedIssues = store.load("branch", filePath);
    assertThat(savedIssues).isNotEmpty();
    var savedIssue = savedIssues.get(0);
    assertThat(savedIssue.getKey()).isEqualTo("key");
    assertThat(savedIssue.isResolved()).isTrue();
    assertThat(savedIssue.getRuleKey()).isEqualTo("repo:key");
    assertThat(savedIssue.getMessage()).isEqualTo("message");
    assertThat(((LineLevelServerIssue) savedIssue).getLineHash()).isEqualTo("hash");
    assertThat(savedIssue.getFilePath()).isEqualTo(filePath);
    assertThat(savedIssue.getCreationDate()).isCloseTo(creationDate, within(1, MILLIS));
    assertThat(savedIssue.getUserSeverity()).isEqualTo(IssueSeverity.MINOR);
    assertThat(savedIssue.getType()).isEqualTo(RuleType.BUG);
    assertThat(((LineLevelServerIssue) savedIssue).getLine()).isEqualTo(1);
    assertThat(savedIssue.getImpacts()).isEqualTo(Map.of(SoftwareQuality.MAINTAINABILITY, ImpactSeverity.HIGH));
  }

  @Test
  void should_save_a_pull_issue() {
    var creationDate = Instant.now();

    store
      .replaceAllIssuesOfBranch("branch", List.of(aServerIssue().setFilePath(filePath).setCreationDate(creationDate)));

    var savedIssues = store.load("branch", filePath);
    assertThat(savedIssues).isNotEmpty();
    var savedIssue = savedIssues.get(0);
    assertThat(savedIssue.getKey()).isEqualTo("key");
    assertThat(savedIssue.isResolved()).isTrue();
    assertThat(savedIssue.getRuleKey()).isEqualTo("repo:key");
    assertThat(savedIssue.getMessage()).isEqualTo("message");
    assertThat(savedIssue.getFilePath()).isEqualTo(filePath);
    assertThat(savedIssue.getCreationDate()).isCloseTo(creationDate, within(1, MILLIS));
    assertThat(savedIssue.getUserSeverity()).isEqualTo(IssueSeverity.MINOR);
    assertThat(savedIssue.getType()).isEqualTo(RuleType.BUG);
    assertThat(((RangeLevelServerIssue) savedIssue).getTextRange().getStartLine()).isEqualTo(1);
    assertThat(((RangeLevelServerIssue) savedIssue).getTextRange().getStartLineOffset()).isEqualTo(2);
    assertThat(((RangeLevelServerIssue) savedIssue).getTextRange().getEndLine()).isEqualTo(3);
    assertThat(((RangeLevelServerIssue) savedIssue).getTextRange().getEndLineOffset()).isEqualTo(4);
    assertThat(((RangeLevelServerIssue) savedIssue).getTextRange().getHash()).isEqualTo("ab12");
    assertThat(savedIssue.getImpacts()).isEqualTo(Map.of(SoftwareQuality.MAINTAINABILITY, ImpactSeverity.HIGH));
  }

  @Test
  void should_save_a_taint_issue() {
    var creationDate = Instant.now();

    store
      .replaceAllTaintsOfBranch("branch", List.of(aServerTaintIssue().setCreationDate(creationDate)
        .setFlows(List.of(new ServerTaintIssue.Flow(List.of(new ServerTaintIssue.ServerIssueLocation(filePath,
          new TextRangeWithHash(5, 6, 7, 8, "myFlowRangeHash"), "flow message")))))));

    var savedIssues = store.loadTaint("branch");
    assertThat(savedIssues).isNotEmpty();
    var savedIssue = savedIssues.get(0);
    assertThat(savedIssue.getSonarServerKey()).isEqualTo("key");
    assertThat(savedIssue.isResolved()).isFalse();
    assertThat(savedIssue.getRuleKey()).isEqualTo("repo:key");
    assertThat(savedIssue.getMessage()).isEqualTo("message");
    assertThat(savedIssue.getFilePath()).isEqualTo(filePath);
    assertThat(savedIssue.getCreationDate()).isCloseTo(creationDate, within(1, MILLIS));
    assertThat(savedIssue.getSeverity()).isEqualTo(IssueSeverity.MINOR);
    assertThat(savedIssue.getType()).isEqualTo(RuleType.VULNERABILITY);
    assertThat(savedIssue.getTextRange().getStartLine()).isEqualTo(1);
    assertThat(savedIssue.getTextRange().getStartLineOffset()).isEqualTo(2);
    assertThat(savedIssue.getTextRange().getEndLine()).isEqualTo(3);
    assertThat(savedIssue.getTextRange().getEndLineOffset()).isEqualTo(4);
    assertThat(savedIssue.getTextRange().getHash()).isEqualTo("ab12");
    assertThat(savedIssue.getFlows()).hasSize(1);
    assertThat(savedIssue.getFlows().get(0).locations())
      .extracting(ServerIssueLocation::getFilePath, ServerIssueLocation::getMessage, l -> l.getTextRange().getHash(), l -> l.getTextRange().getStartLine(),
        l -> l.getTextRange().getStartLineOffset(), l -> l.getTextRange().getEndLine(), l -> l.getTextRange().getEndLineOffset())
      .containsOnly(tuple(filePath, "flow message", "myFlowRangeHash", 5, 6, 7, 8));
    assertThat(savedIssue.getRuleDescriptionContextKey()).isEqualTo("context");
    assertThat(savedIssue.getImpacts()).isEqualTo(Map.of(SoftwareQuality.SECURITY, ImpactSeverity.HIGH));
  }

  @Test
  void should_load_all_issues_of_a_file() {
    store.replaceAllIssuesOfFile("branch", filePath1, List.of(
      aServerIssue().setFilePath(filePath1).setKey("key1"),
      aServerIssue().setFilePath(filePath1).setKey("key3")));
    store.replaceAllIssuesOfFile("branch", filePath2, List.of(
      aServerIssue().setFilePath(filePath2).setKey("key2")));
    store.replaceAllTaintsOfBranch("branch", List.of(
      aServerTaintIssue().setFilePath(filePath1).setKey("key4")));

    var issues = store.load("branch", filePath1);
    assertThat(issues)
      .extracting(ServerIssue::getKey)
      .containsOnly("key1", "key3");
  }

  @Test
  void should_load_all_taint_issues_of_a_branch() {
    store.replaceAllTaintsOfBranch("branch", List.of(
      aServerTaintIssue().setFilePath(filePath1).setKey("key0"),
      aServerTaintIssue().setFilePath(filePath2).setKey("key2")));
    store.replaceAllTaintsOfBranch("branch", List.of(
      aServerTaintIssue().setFilePath(filePath1).setKey("key1"),
      aServerTaintIssue().setFilePath(filePath1).setKey("key3")));
    store.replaceAllIssuesOfBranch("branch", List.of(
      aServerIssue().setFilePath(filePath1).setKey("key4")));

    var issues = store.loadTaint("branch");
    assertThat(issues)
      .extracting(ServerTaintIssue::getSonarServerKey)
      .containsOnly("key1", "key3");
  }

  @Test
  void should_load_issues_of_the_right_branch() {
    store.replaceAllIssuesOfBranch("branch2", List.of(aServerIssue().setFilePath(filePath1).setKey("key2")));
    store.replaceAllIssuesOfBranch("branch1", List.of(aServerIssue().setFilePath(filePath1).setKey("key1")));

    var issues = store.load("branch1", filePath1);
    assertThat(issues)
      .extracting(ServerIssue::getKey)
      .containsOnly("key1");
  }

  @Test
  void should_load_all_taint_issues_on_a_branch() {
    var branchName = "branch1";

    store.replaceAllTaintsOfBranch(branchName, List.of(aServerTaintIssue().setFilePath(filePath1).setKey("key1"), aServerTaintIssue().setFilePath(filePath2).setKey("key2")));

    var issues = store.loadTaint(branchName);

    assertThat(issues)
      .extracting(ServerTaintIssue::getSonarServerKey)
      .containsOnly("key1", "key2");
  }

  @Test
  void should_load_issues_of_the_right_file() {
    store.replaceAllIssuesOfBranch("branch1", List.of(aServerIssue().setFilePath(filePath1).setKey("key1")));
    store.replaceAllIssuesOfBranch("branch2", List.of(aServerIssue().setFilePath(Path.of("file/Path1")).setKey("key2")));

    var issues = store.load("branch1", filePath1);
    assertThat(issues)
      .extracting(ServerIssue::getKey)
      .containsOnly("key1");
  }

  @Test
  void should_remove_closed_issues_by_key_when_merging() {
    store.replaceAllIssuesOfBranch("branch", List.of(
      aServerIssue().setKey("key1"),
      aServerIssue().setKey("key2"),
      aServerIssue().setKey("key3")));

    store.mergeIssues("branch", List.of(), Set.of("key1", "key3"), Instant.now(), Set.of());

    assertThat(store.load("branch", filePath))
      .extracting(ServerIssue::getKey)
      .containsOnly("key2");
  }

  @Test
  void should_add_new_issues_when_merging() {
    store.mergeIssues("branch", List.of(
      aServerIssue().setKey("key1"),
      aServerIssue().setKey("key2"),
      aServerIssue().setKey("key3")), Set.of(), Instant.now(),
      Set.of());

    assertThat(store.load("branch", filePath))
      .extracting(ServerIssue::getKey)
      .containsOnly("key1", "key2", "key3");
  }

  @Test
  void should_update_existing_issues_when_merging() {
    store.replaceAllIssuesOfBranch("branch", List.of(
      aServerIssue().setType(RuleType.VULNERABILITY).setKey("key1"),
      aServerIssue().setType(RuleType.VULNERABILITY).setKey("key2")));

    store.mergeIssues("branch", List.of(
      aServerIssue().setType(RuleType.CODE_SMELL).setKey("key1"),
      aServerIssue().setType(RuleType.BUG).setKey("key2"),
      aServerIssue().setType(RuleType.VULNERABILITY).setKey("key3")), Set.of(), Instant.now(),
      Set.of());

    assertThat(store.load("branch", filePath))
      .extracting(ServerIssue::getKey, ServerIssue::getType)
      .containsOnly(tuple("key1", RuleType.CODE_SMELL), tuple("key2", RuleType.BUG), tuple("key3", RuleType.VULNERABILITY));
  }

  @Test
  void should_remove_closed_taints_by_key_when_merging() {
    store.replaceAllTaintsOfBranch("branch", List.of(
      aServerTaintIssue().setKey("key1"),
      aServerTaintIssue().setKey("key2"),
      aServerTaintIssue().setKey("key3")));

    store.mergeTaintIssues("branch", List.of(), Set.of("key1", "key3"), Instant.now(), Set.of());

    assertThat(store.loadTaint("branch"))
      .extracting(ServerTaintIssue::getSonarServerKey)
      .containsOnly("key2");
  }

  @Test
  void should_add_new_taints_when_merging() {
    store.mergeTaintIssues("branch", List.of(
      aServerTaintIssue().setKey("key1"),
      aServerTaintIssue().setKey("key2"),
      aServerTaintIssue().setKey("key3")), Set.of(), Instant.now(), Set.of());

    assertThat(store.loadTaint("branch"))
      .extracting(ServerTaintIssue::getSonarServerKey)
      .containsOnly("key1", "key2", "key3");
  }

  @Test
  void should_update_existing_taints_when_merging() {
    store.replaceAllTaintsOfBranch("branch", List.of(
      aServerTaintIssue().setType(RuleType.VULNERABILITY).setKey("key1"),
      aServerTaintIssue().setType(RuleType.VULNERABILITY).setKey("key2")));

    store.mergeTaintIssues("branch", List.of(
      aServerTaintIssue().setType(RuleType.CODE_SMELL).setKey("key1"),
      aServerTaintIssue().setType(RuleType.BUG).setKey("key2"),
      aServerTaintIssue().setType(RuleType.VULNERABILITY).setKey("key3")), Set.of(), Instant.now(), Set.of());

    assertThat(store.loadTaint("branch"))
      .extracting(ServerTaintIssue::getSonarServerKey, ServerTaintIssue::getType)
      .containsOnly(tuple("key1", RuleType.CODE_SMELL), tuple("key2", RuleType.BUG), tuple("key3", RuleType.VULNERABILITY));
  }

  @Test
  void should_save_issue_without_line() {
    store.replaceAllIssuesOfBranch("branch", List.of(
      aFileLevelServerIssue().setFilePath(filePath)));

    var issue = store.load("branch", filePath).get(0);

    assertThat(issue).isInstanceOf(FileLevelServerIssue.class);
  }

  @Test
  void should_save_taint_issue_without_range() {
    store.replaceAllTaintsOfBranch("branch", List.of(
      aServerTaintIssue().setKey("key1").setTextRange(null)));

    var issues = store.loadTaint("branch");

    assertThat(issues).isNotEmpty();
    assertThat(issues.get(0).getTextRange()).isNull();
  }

  @Test
  void should_replace_existing_taint_issues_on_branch() {
    store.replaceAllTaintsOfBranch("branch", List.of(aServerTaintIssue().setKey("key1").setTextRange(null)));
    store.replaceAllTaintsOfBranch("branch", List.of(aServerTaintIssue().setKey("key2").setTextRange(null)));

    var issues = store.loadTaint("branch");

    assertThat(issues)
      .extracting("key", "message")
      .containsOnly(tuple("key2", "message"));
  }

  @Test
  void should_replace_existing_issues_on_file() {
    store.replaceAllIssuesOfFile("branch", filePathNoSlash, List.of(aServerIssue().setKey("key1").setFilePath(filePathNoSlash).setMessage("old message")));
    store.replaceAllIssuesOfFile("branch", filePathNoSlash, List.of(aServerIssue().setKey("key2").setFilePath(filePathNoSlash).setMessage("new message")));

    var issues = store.load("branch", filePathNoSlash);

    assertThat(issues)
      .extracting("key", "message")
      .containsOnly(tuple("key2", "new message"));
  }

  @Test
  void should_replace_existing_issues_on_same_file() {
    store.replaceAllIssuesOfBranch("branch", List.of(aServerIssue().setKey("key1").setFilePath(filePathNoSlash).setMessage("old message")));
    store.replaceAllIssuesOfBranch("branch", List.of(aServerIssue().setKey("key2").setFilePath(filePathNoSlash).setMessage("new message")));

    var issues = store.load("branch", filePathNoSlash);

    assertThat(issues)
      .extracting("key", "message")
      .containsOnly(tuple("key2", "new message"));
  }

  @Test
  void should_replace_existing_issues_on_another_file() {
    store.replaceAllIssuesOfBranch("branch", List.of(aServerIssue().setKey("key1").setFilePath(filePathNoSlash1).setMessage("old message")));
    store.replaceAllIssuesOfBranch("branch", List.of(aServerIssue().setKey("key2").setFilePath(filePathNoSlash2).setMessage("new message")));

    var issues = store.load("branch", filePathNoSlash2);

    assertThat(issues)
      .extracting("key", "message")
      .containsOnly(tuple("key2", "new message"));

    assertThat(store.load("branch", filePathNoSlash1)).isEmpty();
  }

  @Test
  void should_replace_existing_issue_with_same_key() {
    store.replaceAllIssuesOfBranch("branch", List.of(aServerIssue().setKey("key1").setFilePath(filePathNoSlash).setMessage("old message")));
    store.replaceAllIssuesOfBranch("branch", List.of(aServerIssue().setKey("key1").setFilePath(filePathNoSlash).setMessage("new message")));

    var issues = store.load("branch", filePathNoSlash);

    assertThat(issues)
      .extracting("key", "message")
      .containsOnly(tuple("key1", "new message"));
  }

  @Test
  void should_save_from_different_files() {
    store.replaceAllIssuesOfBranch("branch", List.of(
      aServerIssue().setKey("key1").setFilePath(filePathNoSlash1),
      aServerIssue().setKey("key2").setFilePath(filePathNoSlash2)));

    var issues = store.load("branch", filePathNoSlash1);

    assertThat(issues)
      .extracting("key", "message")
      .containsOnly(tuple("key1", "message"));
  }

  @Test
  void should_save_issues_with_different_case_keys() {
    store.replaceAllIssuesOfBranch("branch", List.of(
      aServerIssue().setKey("key"),
      aServerIssue().setKey("Key")));

    var issues = store.load("branch", filePath);

    assertThat(issues)
      .extracting("key")
      .containsOnly("key", "Key");
  }

  @Test
  void should_move_issue_to_other_file() {
    store.replaceAllIssuesOfBranch("branch", List.of(
      aServerIssue().setKey("key").setFilePath(filePathNoSlash1)));
    store.replaceAllIssuesOfBranch("branch", List.of(
      aServerIssue().setKey("key").setFilePath(filePathNoSlash2)));

    var issuesFile1 = store.load("branch", filePathNoSlash1);
    assertThat(issuesFile1).isEmpty();

    var issuesFile2 = store.load("branch", filePathNoSlash2);
    assertThat(issuesFile2).isNotEmpty();
  }

  @Test
  void should_update_issue() {
    store.replaceAllIssuesOfBranch("branch", List.of(
      aServerIssue().setKey("key").setFilePath(filePathNoSlash).setResolved(false)));

    store.updateIssue("key", issue -> issue.setResolved(true));

    assertThat(store.load("branch", filePathNoSlash))
      .extracting("resolved")
      .containsOnly(true);
  }

  @Test
  void should_update_hotspots() {
    store.replaceAllHotspotsOfBranch("branch", List.of(aServerHotspot()));

    store.updateHotspot("key", hotspot -> hotspot.setStatus(HotspotReviewStatus.SAFE));

    assertThat(store.loadHotspots("branch", filePath))
      .extracting("status")
      .containsExactly(HotspotReviewStatus.SAFE);
  }

  @Test
  void should_remove_hotspot() {
    store.replaceAllHotspotsOfBranch("branch", List.of(aServerHotspot(), aServerHotspot("key2", Path.of("file2"))));

    store.deleteHotspot("key2");

    assertThat(store.loadHotspots("branch", filePath))
      .extracting("key")
      .containsExactly("key");
  }

  @Test
  void should_insert_hotspot() {
    store.replaceAllHotspotsOfBranch("branch", List.of(aServerHotspot()));

    var newHotspot = aServerHotspot("key2", filePath);
    var newHotspot2 = aServerHotspot("key3", filePath);

    store.insert("branch", newHotspot);
    store.insert("branch2", newHotspot2);

    assertThat(store.loadHotspots("branch", filePath)).hasSize(2);
    assertThat(store.loadHotspots("branch2", filePath)).hasSize(1);
  }

  @Test
  void should_get_empty_last_issue_sync_timestamp_if_no_branch() {
    store.replaceAllIssuesOfBranch("branch", List.of(aServerIssue()));

    assertThat(store.getLastIssueSyncTimestamp("unknown")).isEmpty();
  }

  @Test
  void should_get_empty_last_issue_sync_timestamp_if_no_timestamp_on_branch() {
    store.replaceAllIssuesOfBranch("branch", List.of(aServerIssue()));

    assertThat(store.getLastIssueSyncTimestamp("branch")).isEmpty();
  }

  @Test
  void should_get_last_issue_sync_timestamp() {
    store.mergeIssues("branch", List.of(aServerIssue()), Set.of(), Instant.ofEpochMilli(123456789), Set.of());

    assertThat(store.getLastIssueSyncTimestamp("branch")).contains(Instant.ofEpochMilli(123456789));
  }

  @Test
  void should_get_last_enabled_languages_for_issues() {
    store.mergeIssues("branch", List.of(aServerIssue()), Set.of(), Instant.ofEpochMilli(123456789), Set.of(SonarLanguage.C, SonarLanguage.GO));

    var lastEnabledLanguages = store.getLastIssueEnabledLanguages("branch");

    assertThat(lastEnabledLanguages).isEqualTo(Set.of(SonarLanguage.C, SonarLanguage.GO));
  }

  @Test
  void should_get_last_enabled_languages_for_hotspots() {
    store.mergeHotspots("branch", List.of(aServerHotspot()), Set.of(), Instant.ofEpochMilli(123456789), Set.of(SonarLanguage.C, SonarLanguage.GO));

    var lastEnabledLanguages = store.getLastHotspotEnabledLanguages("branch");

    assertThat(lastEnabledLanguages).isEqualTo(Set.of(SonarLanguage.C, SonarLanguage.GO));
  }

  @Test
  void should_get_last_enabled_languages_for_taints() {
    store.mergeTaintIssues("branch", List.of(aServerTaintIssue()), Set.of(), Instant.ofEpochMilli(123456789), Set.of(SonarLanguage.C,
      SonarLanguage.GO));

    var lastEnabledLanguages = store.getLastTaintEnabledLanguages("branch");

    assertThat(lastEnabledLanguages).isEqualTo(Set.of(SonarLanguage.C, SonarLanguage.GO));
  }

  @Test
  void should_get_empty_last_taint_sync_timestamp_if_no_branch() {
    store.replaceAllIssuesOfBranch("branch", List.of(aServerIssue()));

    assertThat(store.getLastTaintSyncTimestamp("unknown")).isEmpty();
  }

  @Test
  void should_get_empty_last_taint_sync_timestamp_if_no_timestamp_on_branch() {
    store.replaceAllIssuesOfBranch("branch", List.of(aServerIssue()));

    assertThat(store.getLastTaintSyncTimestamp("branch")).isEmpty();
  }

  @Test
  void should_get_last_taint_sync_timestamp() {
    store.mergeTaintIssues("branch", List.of(aServerTaintIssue()), Set.of(), Instant.ofEpochMilli(123456789), Set.of());

    assertThat(store.getLastTaintSyncTimestamp("branch")).contains(Instant.ofEpochMilli(123456789));
  }

  @Test
  void should_insert_taints() {
    store.insert("branch", aServerTaintIssue());

    var taintIssues = store.loadTaint("branch");
    assertThat(taintIssues)
      .extracting("key", "resolved", "ruleKey", "message", "filePath", "severity", "type", "ruleDescriptionContextKey")
      .containsOnly(tuple("key", false, "repo:key", "message", Path.of("file/path"), IssueSeverity.MINOR, RuleType.VULNERABILITY, "context"));
    assertThat(taintIssues)
      .extracting(ServerTaintIssue::getTextRange)
      .extracting("startLine", "startLineOffset", "endLine", "endLineOffset", "hash")
      .containsOnly(tuple(1, 2, 3, 4, "ab12"));
    assertThat(taintIssues)
      .flatExtracting(ServerTaintIssue::getFlows)
      .flatExtracting(Flow::locations)
      .extracting("message", "filePath", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset", "textRange.hash")
      .containsOnly(
        // flow 1
        tuple("message", filePath, 5, 6, 7, 8, "rangeHash"));
  }

  @Test
  void should_insert_taint_with_file_level_location() {
    store.insert("branch", aServerTaintIssue().setFlows(List.of(new ServerTaintIssue.Flow(List.of(new ServerTaintIssue.ServerIssueLocation(
      filePath,
      null,
      "message"))))));

    var taintIssues = store.loadTaint("branch");
    assertThat(taintIssues)
      .flatExtracting(ServerTaintIssue::getFlows)
      .flatExtracting(Flow::locations)
      .extracting("message", "filePath", "textRange")
      .containsOnly(
        // flow 1
        tuple("message", filePath, null));
  }

  @Test
  void should_insert_taint_with_project_level_location() {
    store.insert("branch", aServerTaintIssue().setFlows(List.of(new ServerTaintIssue.Flow(List.of(new ServerTaintIssue.ServerIssueLocation(
      null,
      null,
      "message"))))));

    var taintIssues = store.loadTaint("branch");
    assertThat(taintIssues)
      .flatExtracting(ServerTaintIssue::getFlows)
      .flatExtracting(Flow::locations)
      .extracting("message", "filePath", "textRange")
      .containsOnly(
        // flow 1
        tuple("message", null, null));
  }

  @Test
  void should_delete_taints() {
    store.replaceAllTaintsOfBranch("branch", List.of(aServerTaintIssue()));

    store.deleteTaintIssueBySonarServerKey("key");

    var taintIssues = store.loadTaint("branch");
    assertThat(taintIssues).isEmpty();
  }

  @Test
  void should_update_taints() {
    store.replaceAllTaintsOfBranch("branch", List.of(aServerTaintIssue()));

    store.updateTaintIssueBySonarServerKey("key", taintIssue -> taintIssue.setResolved(true));

    var taintIssues = store.loadTaint("branch");
    assertThat(taintIssues)
      .extracting("resolved")
      .containsOnly(true);
  }

  @Test
  void should_update_taint_issue_status() {
    store.replaceAllTaintsOfBranch("branch", List.of(aServerTaintIssue()));

    store.updateIssueResolutionStatus("key", true, true);

    var taintIssues = store.loadTaint("branch");
    var issues = store.load("branch", filePath);
    assertThat(taintIssues)
      .extracting("resolved")
      .containsOnly(true);
    assertThat(issues).isEmpty();
  }

  @Test
  void should_update_non_taint_issue_status() {
    store.replaceAllIssuesOfFile("branch", filePath, List.of(aServerIssue()));

    store.updateIssueResolutionStatus("key", false, true);

    var taintIssues = store.loadTaint("branch");
    var issues = store.load("branch", filePath);
    assertThat(issues)
      .extracting("resolved")
      .containsOnly(true);
    assertThat(taintIssues).isEmpty();
  }

  @Test
  void should_not_update_issue_status_if_issue_is_not_in_storage() {
    store.updateIssueResolutionStatus("key", false, true);

    var taintIssues = store.loadTaint("branch");
    var issues = store.load("branch", filePath);
    assertThat(issues).isEmpty();
    assertThat(taintIssues).isEmpty();
  }

  @Test
  void should_save_branch_hotspots_when_replacing_them_on_an_empty_store() {
    store.replaceAllHotspotsOfBranch("branch", List.of(aServerHotspot()));

    var hotspots = store.loadHotspots("branch", filePath);
    assertThat(hotspots)
      .extracting("key", "ruleKey", "message", "filePath", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset", "status")
      .containsOnly(tuple("key", "repo:key", "message", Path.of("file/path"), 1, 2, 3, 4, HotspotReviewStatus.TO_REVIEW));
  }

  @Test
  void should_save_file_hotspots_when_replacing_them_on_an_empty_store() {
    store.replaceAllHotspotsOfFile("branch", filePath, List.of(aServerHotspot()));

    var hotspots = store.loadHotspots("branch", filePath);
    assertThat(hotspots)
      .extracting("key", "ruleKey", "message", "filePath", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset", "status")
      .containsOnly(tuple("key", "repo:key", "message", Path.of("file/path"), 1, 2, 3, 4, HotspotReviewStatus.TO_REVIEW));
  }

  @Test
  void should_replace_file_hotspots_when_replacing_an_already_existing_file() {
    store.replaceAllHotspotsOfFile("branch", filePath, List.of(aServerHotspot("previousHotspot")));

    store.replaceAllHotspotsOfFile("branch", filePath, List.of(aServerHotspot("newHotspot")));

    var hotspots = store.loadHotspots("branch", filePath);
    assertThat(hotspots)
      .extracting("key")
      .containsOnly("newHotspot");
  }

  @Test
  void should_replace_branch_hotspots_of_an_existing_file() {
    store.replaceAllHotspotsOfBranch("branch", List.of(aServerHotspot("previousHotspot")));

    store.replaceAllHotspotsOfBranch("branch", List.of(aServerHotspot("newHotspot")));

    var hotspots = store.loadHotspots("branch", filePath);
    assertThat(hotspots)
      .extracting("key")
      .containsOnly("newHotspot");
  }

  @Test
  void should_replace_branch_hotspots_for_a_new_file() {
    store.replaceAllHotspotsOfBranch("branch", List.of(aServerHotspot("previousHotspot", Path.of("old/path"))));

    store.replaceAllHotspotsOfBranch("branch", List.of(aServerHotspot("newHotspot", Path.of("new/path"))));

    var hotspots = store.loadHotspots("branch", Path.of("new/path"));
    assertThat(hotspots)
      .extracting("key", "filePath")
      .containsOnly(tuple("newHotspot", Path.of("new/path")));
  }

  @Test
  void should_remove_closed_hotspots_by_key_when_merging() {
    store.replaceAllHotspotsOfBranch("branch", List.of(
      aServerHotspot("key1"),
      aServerHotspot("key2"),
      aServerHotspot("key3")));

    store.mergeHotspots("branch", List.of(), Set.of("key1", "key3"), Instant.now(), Set.of());

    assertThat(store.loadHotspots("branch", filePath))
      .extracting(ServerHotspot::getKey)
      .containsOnly("key2");
  }

  @Test
  void should_add_new_hotspots_when_merging() {
    store.mergeHotspots("branch", List.of(
      aServerHotspot("key1"),
      aServerHotspot("key2"),
      aServerHotspot("key3")), Set.of(), Instant.now(), Set.of());

    assertThat(store.loadHotspots("branch", filePath))
      .extracting(ServerHotspot::getKey)
      .containsOnly("key1", "key2", "key3");
  }

  @Test
  void should_update_existing_hotspots_when_merging() {
    store.replaceAllHotspotsOfBranch("branch", List.of(
      aServerHotspot("key1"),
      aServerHotspot("key2")));

    store.mergeHotspots("branch", List.of(
      aServerHotspot("key1"),
      aServerHotspot("key2").withStatus(HotspotReviewStatus.SAFE)), Set.of(), Instant.now(), Set.of());

    assertThat(store.loadHotspots("branch", filePath))
      .extracting(ServerHotspot::getKey, ServerHotspot::getStatus)
      .containsOnly(tuple("key1", HotspotReviewStatus.TO_REVIEW), tuple("key2", HotspotReviewStatus.SAFE));
  }

  @Test
  void should_restore_from_backup() throws IOException {
    var creationDate = Instant.now();

    store
      .replaceAllIssuesOfBranch("branch", List.of(aServerIssue().setFilePath(filePath).setCreationDate(creationDate)));

    assertThat(backupDir).isEmptyDirectory();

    store.close();

    assertThat(backupDir).isDirectoryContaining("glob:**backup.tar.gz");

    FileUtils.deleteRecursively(workDir);
    Files.createDirectories(workDir);

    store = new XodusServerIssueStore(backupDir, workDir);

    var savedIssues = store.load("branch", filePath);
    assertThat(savedIssues).isNotEmpty();
  }

  @Test
  void should_log_and_continue_if_invalid_backup() throws IOException {
    var creationDate = Instant.now();

    store
      .replaceAllIssuesOfBranch("branch", List.of(aServerIssue().setFilePath(filePath).setCreationDate(creationDate)));

    assertThat(backupDir).isEmptyDirectory();

    store.close();

    assertThat(backupDir).isDirectoryContaining("glob:**backup.tar.gz");

    Files.writeString(backupDir.resolve("backup.tar.gz"), "Garbage", StandardCharsets.UTF_8);

    store = new XodusServerIssueStore(backupDir, workDir);

    assertThat(logTester.logs(Level.ERROR)).contains("Unable to restore backup " + backupDir.resolve("backup.tar.gz"));

    var savedIssues = store.load("branch", filePath);
    assertThat(savedIssues).isEmpty();
  }

  @Test
  void should_allow_concurrent_instances() throws IOException {
    var creationDate = Instant.now();

    store
      .replaceAllIssuesOfBranch("branch", List.of(aServerIssue().setFilePath(filePath).setCreationDate(creationDate)));
    store.close();
    assertThat(backupDir).isDirectoryContaining("glob:**backup.tar.gz");

    store = new XodusServerIssueStore(backupDir, workDir);
    var store2 = new XodusServerIssueStore(backupDir, workDir);

    var savedIssues = store.load("branch", filePath);
    assertThat(savedIssues).isNotEmpty();

    var savedIssues2 = store2.load("branch", filePath);
    assertThat(savedIssues2).isNotEmpty();

    store2.close();
  }

  @Test
  void should_find_when_the_issue_exists() {
    var creationDate = Instant.now();

    store
      .replaceAllIssuesOfBranch("branch", List.of(aServerIssue().setFilePath(filePath).setCreationDate(creationDate)));

    assertThat(store.containsIssue("key")).isTrue();
  }

  @Test
  void should_find_when_the_taint_exists() {
    var creationDate = Instant.now();

    store
      .replaceAllTaintsOfBranch("branch", List.of(aServerTaintIssue().setFilePath(filePath).setCreationDate(creationDate)));

    assertThat(store.containsIssue("key")).isTrue();
  }

  @Test
  void should_not_find_the_issue_when_it_does_not_exist() {
    var creationDate = Instant.now();

    store
      .replaceAllIssuesOfBranch("branch", List.of(aServerIssue().setFilePath(filePath).setCreationDate(creationDate)));

    assertThat(store.containsIssue("key_not_found")).isFalse();
  }
}
