/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2022 SonarSource SA
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
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.serverconnection.ServerTaintIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.FileLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.LineLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.RangeLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueFixtures.aBatchServerIssue;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueFixtures.aFileLevelServerIssue;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueFixtures.aServerIssue;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueFixtures.aServerTaintIssue;

class XodusServerIssueStoreTests {
  @TempDir
  Path tmpBaseDir;
  private XodusServerIssueStore store;

  @BeforeEach
  void setUp() {
    store = new XodusServerIssueStore(tmpBaseDir);
  }

  @AfterEach
  void tearDown() {
    store.close();
  }

  @Test
  void should_return_empty_when_file_path_unknown() {
    var issues = store.load("projectKey", "branch", "path");

    assertThat(issues).isEmpty();
  }

  @Test
  void should_save_a_batch_issue() {
    var creationDate = Instant.now();

    store
      .replaceAllIssuesOfProject("projectKey", "branch", List.of(aBatchServerIssue().setFilePath("file/path").setCreationDate(creationDate)));

    var savedIssues = store.load("projectKey", "branch", "file/path");
    assertThat(savedIssues).isNotEmpty();
    var savedIssue = savedIssues.get(0);
    assertThat(savedIssue.getKey()).isEqualTo("key");
    assertThat(savedIssue.isResolved()).isTrue();
    assertThat(savedIssue.getRuleKey()).isEqualTo("repo:key");
    assertThat(savedIssue.getMessage()).isEqualTo("message");
    assertThat(((LineLevelServerIssue) savedIssue).getLineHash()).isEqualTo("hash");
    assertThat(savedIssue.getFilePath()).isEqualTo("file/path");
    assertThat(savedIssue.getCreationDate()).isEqualTo(creationDate);
    assertThat(savedIssue.getUserSeverity()).isEqualTo("MINOR");
    assertThat(savedIssue.getType()).isEqualTo("BUG");
    assertThat(((LineLevelServerIssue) savedIssue).getLine()).isEqualTo(1);
  }

  @Test
  void should_save_a_pull_issue() {
    var creationDate = Instant.now();

    store
      .replaceAllIssuesOfProject("projectKey", "branch", List.of(aServerIssue().setFilePath("file/path").setCreationDate(creationDate)));

    var savedIssues = store.load("projectKey", "branch", "file/path");
    assertThat(savedIssues).isNotEmpty();
    var savedIssue = savedIssues.get(0);
    assertThat(savedIssue.getKey()).isEqualTo("key");
    assertThat(savedIssue.isResolved()).isTrue();
    assertThat(savedIssue.getRuleKey()).isEqualTo("repo:key");
    assertThat(savedIssue.getMessage()).isEqualTo("message");
    assertThat(savedIssue.getFilePath()).isEqualTo("file/path");
    assertThat(savedIssue.getCreationDate()).isEqualTo(creationDate);
    assertThat(savedIssue.getUserSeverity()).isEqualTo("MINOR");
    assertThat(savedIssue.getType()).isEqualTo("BUG");
    assertThat(((RangeLevelServerIssue) savedIssue).getRangeHash()).isEqualTo("hash");
    assertThat(((RangeLevelServerIssue) savedIssue).getTextRange().getStartLine()).isEqualTo(1);
    assertThat(((RangeLevelServerIssue) savedIssue).getTextRange().getStartLineOffset()).isEqualTo(2);
    assertThat(((RangeLevelServerIssue) savedIssue).getTextRange().getEndLine()).isEqualTo(3);
    assertThat(((RangeLevelServerIssue) savedIssue).getTextRange().getEndLineOffset()).isEqualTo(4);
  }

  @Test
  void should_save_a_taint_issue() {
    var creationDate = Instant.now();

    store
      .replaceAllTaintOfFile("projectKey", "branch", "file/path", List.of(aServerTaintIssue().setCreationDate(creationDate).setCodeSnippet("code")
        .setFlows(List.of(new ServerTaintIssue.Flow(List.of(new ServerTaintIssue.ServerIssueLocation("file/path",
          new ServerTaintIssue.TextRange(5, 6, 7, 8), "flow message", "code")))))));

    var savedIssues = store.loadTaint("projectKey", "branch", "file/path");
    assertThat(savedIssues).isNotEmpty();
    var savedIssue = savedIssues.get(0);
    assertThat(savedIssue.key()).isEqualTo("key");
    assertThat(savedIssue.resolved()).isTrue();
    assertThat(savedIssue.ruleKey()).isEqualTo("repo:key");
    assertThat(savedIssue.getMessage()).isEqualTo("message");
    assertThat(savedIssue.lineHash()).isEqualTo("hash");
    assertThat(savedIssue.getFilePath()).isEqualTo("file/path");
    assertThat(savedIssue.creationDate()).isEqualTo(creationDate);
    assertThat(savedIssue.severity()).isEqualTo("MINOR");
    assertThat(savedIssue.type()).isEqualTo("BUG");
    assertThat(savedIssue.getTextRange().getStartLine()).isEqualTo(1);
    assertThat(savedIssue.getTextRange().getStartLineOffset()).isEqualTo(2);
    assertThat(savedIssue.getTextRange().getEndLine()).isEqualTo(3);
    assertThat(savedIssue.getTextRange().getEndLineOffset()).isEqualTo(4);
    assertThat(savedIssue.getCodeSnippet()).isEqualTo("code");
    assertThat(savedIssue.getFlows()).hasSize(1);
    assertThat(savedIssue.getFlows().get(0).locations())
      .extracting("filePath", "message", "codeSnippet", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset")
      .containsOnly(tuple("file/path", "flow message", "code", 5, 6, 7, 8));
  }

  @Test
  void should_load_all_issues_of_a_file() {
    store.replaceAllIssuesOfFile("projectKey", "branch", "file/path1", List.of(
      aServerIssue().setFilePath("file/path1").setKey("key1"),
      aServerIssue().setFilePath("file/path1").setKey("key3")));
    store.replaceAllIssuesOfFile("projectKey", "branch", "file/path2", List.of(
      aServerIssue().setFilePath("file/path2").setKey("key2")));
    store.replaceAllTaintOfFile("projectKey", "branch", "file/path1", List.of(
      aServerTaintIssue().setFilePath("file/path1").setKey("key4")));

    var issues = store.load("projectKey", "branch", "file/path1");
    assertThat(issues)
      .extracting(ServerIssue::getKey)
      .containsOnly("key1", "key3");
  }

  @Test
  void should_load_all_taint_issues_of_a_file() {
    store.replaceAllTaintOfFile("projectKey", "branch", "file/path1", List.of(
      aServerTaintIssue().setFilePath("file/path1").setKey("key1"),
      aServerTaintIssue().setFilePath("file/path1").setKey("key3")));
    store.replaceAllTaintOfFile("projectKey", "branch", "file/path2", List.of(
      aServerTaintIssue().setFilePath("file/path2").setKey("key2")));
    store.replaceAllIssuesOfProject("projectKey", "branch", List.of(
      aServerIssue().setFilePath("file/path1").setKey("key4")));

    var issues = store.loadTaint("projectKey", "branch", "file/path1");
    assertThat(issues)
      .extracting(ServerTaintIssue::key)
      .containsOnly("key1", "key3");
  }

  @Test
  void should_load_issues_of_the_right_project() {
    store.replaceAllIssuesOfProject("projectKey", "branch", List.of(aServerIssue().setFilePath("file/path1").setKey("key1")));
    store.replaceAllIssuesOfProject("projectKey2", "branch", List.of(aServerIssue().setFilePath("file/path1").setKey("key2")));

    var issues = store.load("projectKey", "branch", "file/path1");
    assertThat(issues)
      .extracting(ServerIssue::getKey)
      .containsOnly("key1");
  }

  @Test
  void should_remove_issues_by_key() {
    store.replaceAllIssuesOfProject("projectKey", "branch", List.of(
      aServerIssue().setKey("key1"),
      aServerIssue().setKey("key2"),
      aServerIssue().setKey("key3")));

    store.removeAll(Set.of("key1", "key3"));

    assertThat(store.load("projectKey", "branch", "file/path"))
      .extracting(ServerIssue::getKey)
      .containsOnly("key2");
  }

  @Test
  void should_save_issue_without_line() {
    store.replaceAllIssuesOfProject("projectKey", "branch", List.of(
      aFileLevelServerIssue().setFilePath("file/path")));

    var issue = store.load("projectKey", "branch", "file/path").get(0);

    assertThat(issue).isInstanceOf(FileLevelServerIssue.class);
  }

  @Test
  void should_save_taint_issue_without_range() {
    store.replaceAllTaintOfFile("projectKey", "branch", "file/path", List.of(
      aServerTaintIssue().setKey("key1").setTextRange(null)));

    var issues = store.loadTaint("projectKey", "branch", "file/path");

    assertThat(issues).isNotEmpty();
    assertThat(issues.get(0).getTextRange()).isNull();
  }

  @Test
  void should_replace_existing_taint_issues_on_file() {
    store.replaceAllTaintOfFile("projectKey", "branch", "file/path", List.of(aServerTaintIssue().setKey("key1").setTextRange(null)));
    store.replaceAllTaintOfFile("projectKey", "branch", "file/path", List.of(aServerTaintIssue().setKey("key2").setTextRange(null)));

    var issues = store.loadTaint("projectKey", "branch", "file/path");

    assertThat(issues)
      .extracting("key", "message")
      .containsOnly(tuple("key2", "message"));
  }

  @Test
  void should_replace_existing_issues_on_file() {
    store.replaceAllIssuesOfFile("projectKey", "branch", "filePath", List.of(aServerIssue().setKey("key1").setFilePath("filePath").setMessage("old message")));
    store.replaceAllIssuesOfFile("projectKey", "branch", "filePath", List.of(aServerIssue().setKey("key2").setFilePath("filePath").setMessage("new message")));

    var issues = store.load("projectKey", "branch", "filePath");

    assertThat(issues)
      .extracting("key", "message")
      .containsOnly(tuple("key2", "new message"));
  }

  @Test
  void should_replace_existing_issues_on_same_file() {
    store.replaceAllIssuesOfProject("projectKey", "branch", List.of(aServerIssue().setKey("key1").setFilePath("filePath").setMessage("old message")));
    store.replaceAllIssuesOfProject("projectKey", "branch", List.of(aServerIssue().setKey("key2").setFilePath("filePath").setMessage("new message")));

    var issues = store.load("projectKey", "branch", "filePath");

    assertThat(issues)
      .extracting("key", "message")
      .containsOnly(tuple("key2", "new message"));
  }

  @Test
  void should_replace_existing_issues_on_another_file() {
    store.replaceAllIssuesOfProject("projectKey", "branch", List.of(aServerIssue().setKey("key1").setFilePath("filePath1").setMessage("old message")));
    store.replaceAllIssuesOfProject("projectKey", "branch", List.of(aServerIssue().setKey("key2").setFilePath("filePath2").setMessage("new message")));

    var issues = store.load("projectKey", "branch", "filePath2");

    assertThat(issues)
      .extracting("key", "message")
      .containsOnly(tuple("key2", "new message"));

    assertThat(store.load("projectKey", "branch", "filePath1")).isEmpty();
  }

  @Test
  void should_replace_existing_issue_with_same_key() {
    store.replaceAllIssuesOfProject("projectKey", "branch", List.of(aServerIssue().setKey("key1").setFilePath("filePath").setMessage("old message")));
    store.replaceAllIssuesOfProject("projectKey", "branch", List.of(aServerIssue().setKey("key1").setFilePath("filePath").setMessage("new message")));

    var issues = store.load("projectKey", "branch", "filePath");

    assertThat(issues)
      .extracting("key", "message")
      .containsOnly(tuple("key1", "new message"));
  }

  @Test
  void should_save_from_different_files() {
    store.replaceAllIssuesOfProject("projectKey", "branch", List.of(
      aServerIssue().setKey("key1").setFilePath("filePath1"),
      aServerIssue().setKey("key2").setFilePath("filePath2")));

    var issues = store.load("projectKey", "branch", "filePath1");

    assertThat(issues)
      .extracting("key", "message")
      .containsOnly(tuple("key1", "message"));
  }

  @Test
  void should_save_issues_with_different_case_keys() {
    store.replaceAllIssuesOfProject("projectKey", "branch", List.of(
      aServerIssue().setKey("key"),
      aServerIssue().setKey("Key")));

    var issues = store.load("projectKey", "branch", "file/path");

    assertThat(issues)
      .extracting("key")
      .containsOnly("key", "Key");
  }

  @Test
  void should_move_issue_to_other_file() {
    store.replaceAllIssuesOfProject("projectKey", "branch", List.of(
      aServerIssue().setKey("key").setFilePath("filePath1")));
    store.replaceAllIssuesOfProject("projectKey", "branch", List.of(
      aServerIssue().setKey("key").setFilePath("filePath2")));

    var issuesFile1 = store.load("projectKey", "branch", "filePath1");
    assertThat(issuesFile1).isEmpty();

    var issuesFile2 = store.load("projectKey", "branch", "filePath2");
    assertThat(issuesFile2).isNotEmpty();
  }

  @Test
  void should_update_issue() {
    store.replaceAllIssuesOfProject("projectKey", "branch", List.of(
      aServerIssue().setKey("key").setFilePath("filePath").setResolved(false)));

    store.updateIssue("key", issue -> issue.setResolved(true));

    assertThat(store.load("projectKey", "branch", "filePath"))
      .extracting("resolved")
      .containsOnly(true);
  }
}
