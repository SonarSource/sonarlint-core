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
import org.sonarsource.sonarlint.core.tracking.LocalOnlyIssueResolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.clientapi.backend.issue.IssueStatus.FALSE_POSITIVE;
import static org.sonarsource.sonarlint.core.clientapi.backend.issue.IssueStatus.WONT_FIX;
import static org.sonarsource.sonarlint.core.local.only.LocalOnlyIssueFixtures.aLocalOnlyIssueUnresolved;
import static org.sonarsource.sonarlint.core.local.only.LocalOnlyIssueFixtures.aLocalOnlyIssueUnresolvedWithoutTextAndLineRange;

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
    var issues = store.load("configScopeId", "path");

    assertThat(issues).isEmpty();
  }

  @Test
  void should_save_a_local_only_issue() {
    var localOnlyIssue = aLocalOnlyIssueUnresolved();
    store.storeLocalOnlyIssue("configScopeId", localOnlyIssue, WONT_FIX);

    var storedIssues = store.load("configScopeId", "file/path");
    assertThat(storedIssues).hasSize(1);
    var storedIssue = storedIssues.get(0);
    assertThat(storedIssue).usingRecursiveComparison().ignoringFieldsOfTypes(LocalOnlyIssueResolution.class).isEqualTo(localOnlyIssue);
    assertThat(storedIssue.getResolution().getStatus()).isEqualTo(WONT_FIX);
  }

  @Test
  void should_save_multiple_local_only_issue_to_save_file() {
    var localOnlyIssue1 = aLocalOnlyIssueUnresolved();
    var localOnlyIssue2 = aLocalOnlyIssueUnresolvedWithoutTextAndLineRange();
    var localOnlyIssue3 = aLocalOnlyIssueUnresolved();
    store.storeLocalOnlyIssue("configScopeId", localOnlyIssue1, WONT_FIX);
    store.storeLocalOnlyIssue("configScopeId", localOnlyIssue2, WONT_FIX);
    store.storeLocalOnlyIssue("configScopeId", localOnlyIssue3, FALSE_POSITIVE);

    var storedIssues = store.load("configScopeId", "file/path");
    assertThat(storedIssues).hasSize(3);
    var storedIssue1 = storedIssues.get(0);
    var storedIssue2 = storedIssues.get(1);
    var storedIssue3 = storedIssues.get(2);
    assertThat(storedIssue1).usingRecursiveComparison().ignoringFieldsOfTypes(LocalOnlyIssueResolution.class).isEqualTo(localOnlyIssue1);
    assertThat(storedIssue1.getResolution().getStatus()).isEqualTo(WONT_FIX);
    assertThat(storedIssue2).usingRecursiveComparison().ignoringFieldsOfTypes(LocalOnlyIssueResolution.class).isEqualTo(localOnlyIssue2);
    assertThat(storedIssue2.getResolution().getStatus()).isEqualTo(WONT_FIX);
    assertThat(storedIssue3).usingRecursiveComparison().ignoringFieldsOfTypes(LocalOnlyIssueResolution.class).isEqualTo(localOnlyIssue3);
    assertThat(storedIssue3.getResolution().getStatus()).isEqualTo(FALSE_POSITIVE);
  }

  @Test
  void should_not_load_a_local_only_issue_if_file_path_is_wrong() {
    var localOnlyIssue = aLocalOnlyIssueUnresolved();
    store.storeLocalOnlyIssue("configScopeId", localOnlyIssue, WONT_FIX);

    var storedIssues = store.load("configScopeId", "wrong/path");
    assertThat(storedIssues).isEmpty();
  }

  @Test
  void should_not_load_a_local_only_issue_if_config_scope_id_is_wrong() {
    var localOnlyIssue = aLocalOnlyIssueUnresolved();
    store.storeLocalOnlyIssue("configScopeId", localOnlyIssue, WONT_FIX);

    var storedIssues = store.load("wrongConfigScopeId", "file/path");
    assertThat(storedIssues).isEmpty();
  }

}
