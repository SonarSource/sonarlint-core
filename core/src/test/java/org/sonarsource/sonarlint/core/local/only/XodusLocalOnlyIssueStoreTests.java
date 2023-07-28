/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.local.only;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.local.only.LocalOnlyIssueFixtures.aLocalOnlyIssueResolved;
import static org.sonarsource.sonarlint.core.local.only.LocalOnlyIssueFixtures.aLocalOnlyIssueResolvedWithoutTextAndLineRange;

class XodusLocalOnlyIssueStoreTests {

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester(true);

  @TempDir
  Path workDir;
  @TempDir
  Path backupDir;
  private XodusLocalOnlyIssueStore store;

  @BeforeEach
  void setUp() throws IOException {
    store = new XodusLocalOnlyIssueStore(backupDir, workDir);
  }

  @AfterEach
  void tearDown() {
    store.close();
  }

  @Test
  void should_return_empty_when_file_path_unknown() {
    var issues = store.loadForFile("configScopeId", "path");

    assertThat(issues).isEmpty();
  }

  @Test
  void should_save_a_local_only_issue() {
    var localOnlyIssue = aLocalOnlyIssueResolved();
    store.storeLocalOnlyIssue("configScopeId", localOnlyIssue);

    var storedIssues = store.loadForFile("configScopeId", "file/path");
    assertThat(storedIssues).usingRecursiveFieldByFieldElementComparator()
      .containsOnly(localOnlyIssue);
  }

  @Test
  void should_save_a_resolved_local_only_issue() {
    var localOnlyIssue = aLocalOnlyIssueResolved();
    store.storeLocalOnlyIssue("configScopeId", localOnlyIssue);

    var storedIssues = store.loadForFile("configScopeId", "file/path");
    assertThat(storedIssues).usingRecursiveFieldByFieldElementComparator()
        .containsOnly(localOnlyIssue);
  }

  @Test
  void should_save_multiple_local_only_issue_to_save_file() {
    var localOnlyIssue1 = aLocalOnlyIssueResolved();
    var localOnlyIssue2 = aLocalOnlyIssueResolvedWithoutTextAndLineRange();
    var localOnlyIssue3 = aLocalOnlyIssueResolved();
    store.storeLocalOnlyIssue("configScopeId", localOnlyIssue1);
    store.storeLocalOnlyIssue("configScopeId", localOnlyIssue2);
    store.storeLocalOnlyIssue("configScopeId", localOnlyIssue3);

    var storedIssues = store.loadForFile("configScopeId", "file/path");
    assertThat(storedIssues).usingRecursiveFieldByFieldElementComparator()
        .containsExactly(localOnlyIssue1, localOnlyIssue2, localOnlyIssue3);
  }

  @Test
  void should_not_load_a_local_only_issue_if_file_path_is_wrong() {
    var localOnlyIssue = aLocalOnlyIssueResolved();
    store.storeLocalOnlyIssue("configScopeId", localOnlyIssue);

    var storedIssues = store.loadForFile("configScopeId", "wrong/path");
    assertThat(storedIssues).isEmpty();
  }

  @Test
  void should_not_load_a_local_only_issue_if_config_scope_id_is_wrong() {
    var localOnlyIssue = aLocalOnlyIssueResolved();
    store.storeLocalOnlyIssue("configScopeId", localOnlyIssue);

    var storedIssues = store.loadForFile("wrongConfigScopeId", "file/path");
    assertThat(storedIssues).isEmpty();
  }

  @Test
  void should_reopen_issue() {
    var localOnlyIssue = aLocalOnlyIssueResolved();
    store.storeLocalOnlyIssue("configScopeId", localOnlyIssue);
    assertThat(store.loadForFile("configScopeId", "file/path")).usingRecursiveFieldByFieldElementComparator()
      .containsExactly(localOnlyIssue);

    store.removeIssue(localOnlyIssue.getId());
    assertThat(store.loadForFile("configScopeId", "file/path")).isEmpty();
  }

  @Test
  void should_reopen_all_issues_for_file() {
    var localOnlyIssue1 = aLocalOnlyIssueResolved();
    var localOnlyIssue2 = aLocalOnlyIssueResolved();
    store.storeLocalOnlyIssue("configScopeId", localOnlyIssue1);
    store.storeLocalOnlyIssue("configScopeId", localOnlyIssue2);
    assertThat(store.loadForFile("configScopeId", "file/path")).usingRecursiveFieldByFieldElementComparator()
      .containsExactly(localOnlyIssue1, localOnlyIssue2);

    store.removeAllIssuesForFile("configScopeId", "file/path");
    assertThat(store.loadForFile("configScopeId", "file/path")).isEmpty();
  }


}
