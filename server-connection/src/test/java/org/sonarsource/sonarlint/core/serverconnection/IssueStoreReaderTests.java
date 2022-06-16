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
package org.sonarsource.sonarlint.core.serverconnection;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueStore;
import testutils.InMemoryIssueStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueFixtures.aServerIssue;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueFixtures.aServerTaintIssue;

class IssueStoreReaderTests {
  private static final String PROJECT_KEY = "root";

  private IssueStoreReader issueStoreReader;
  private final ServerIssueStore issueStore = new InMemoryIssueStore();
  private final ProjectBinding projectBinding = new ProjectBinding(PROJECT_KEY, "", "");

  @BeforeEach
  void setUp() {
    issueStoreReader = new IssueStoreReader(issueStore);
  }

  @Test
  void testSingleModule() {
    // setup issues
    issueStore.replaceAllIssuesOfProject(projectBinding.projectKey(), "branch", Collections.singletonList(createServerIssue("src/path1")));

    // test

    // matches path
    assertThat(issueStoreReader.getServerIssues(projectBinding, "branch", "src/path1"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createServerIssue("src/path1"));

    // no file found
    assertThat(issueStoreReader.getServerIssues(projectBinding, "branch", "src/path3")).isEmpty();

    // invalid prefixes
    var bindingWithPrefix = new ProjectBinding(PROJECT_KEY, "", "src");
    assertThat(issueStoreReader.getServerIssues(bindingWithPrefix, "branch", "src2/path2")).isEmpty();
  }

  @Test
  void return_empty_list_if_local_path_is_invalid() {
    var projectBinding = new ProjectBinding(PROJECT_KEY, "", "local");
    issueStore.replaceAllIssuesOfProject(projectBinding.projectKey(), "branch", Collections.singletonList(createServerIssue("src/path1")));
    assertThat(issueStoreReader.getServerIssues(projectBinding, "branch", "src/path1"))
      .isEmpty();
  }

  @Test
  void testSingleModuleWithBothPrefixes() {
    var projectBinding = new ProjectBinding(PROJECT_KEY, "sq", "local");

    // setup issues
    issueStore.replaceAllIssuesOfProject(projectBinding.projectKey(), "branch", Collections.singletonList(createServerIssue("sq/src/path1")));

    // test

    // matches path
    assertThat(issueStoreReader.getServerIssues(projectBinding, "branch", "local/src/path1"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createServerIssue("local/src/path1"));
  }

  @Test
  void testSingleModuleWithLocalPrefix() {
    var projectBinding = new ProjectBinding(PROJECT_KEY, "", "local");

    // setup issues
    issueStore.replaceAllIssuesOfProject(projectBinding.projectKey(), "branch", Collections.singletonList(createServerIssue("src/path1")));

    // test

    // matches path
    assertThat(issueStoreReader.getServerIssues(projectBinding, "branch", "local/src/path1"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createServerIssue("local/src/path1"));
  }

  @Test
  void testSingleModuleWithSQPrefix() {
    var projectBinding = new ProjectBinding(PROJECT_KEY, "sq", "");

    // setup issues
    issueStore.replaceAllIssuesOfProject(projectBinding.projectKey(), "branch", Collections.singletonList(createServerIssue("sq/src/path1")));

    // test

    // matches path
    assertThat(issueStoreReader.getServerIssues(projectBinding, "branch", "src/path1"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createServerIssue("src/path1"));
  }

  @Test
  void canReadFlowsFromStorage() {
    // setup issues
    issueStore.replaceAllTaintOfFile(projectBinding.projectKey(), "branch", "src/path1", List.of(aServerTaintIssue()
      .setFilePath("src/path1")
      .setMessage("Primary")
      .setTextRange(new ServerTaintIssue.TextRange(1, 2, 3, 4))
      .setCodeSnippet("Primary location code")
      .setFlows(List.of(
        new ServerTaintIssue.Flow(List.of(
          new ServerTaintIssue.ServerIssueLocation("src/path1", new ServerTaintIssue.TextRange(5, 6, 7, 8), "Flow 1 - Location 1", "Some code snipper\n\t with newline"),
          new ServerTaintIssue.ServerIssueLocation("src/path1", null, "Flow 1 - Location 2 - Without text range", null),
          new ServerTaintIssue.ServerIssueLocation("src/path2", null, null, null)))))));

    var issuesReadFromStorage = issueStoreReader.getServerTaintIssues(projectBinding, "branch", "src/path1");
    assertThat(issuesReadFromStorage).hasSize(1);
    var loadedIssue = issuesReadFromStorage.get(0);
    assertThat(loadedIssue.getFilePath()).isEqualTo("src/path1");
    assertThat(loadedIssue.getMessage()).isEqualTo("Primary");
    assertThat(loadedIssue.getTextRange().getStartLine()).isEqualTo(1);
    assertThat(loadedIssue.getTextRange().getStartLineOffset()).isEqualTo(2);
    assertThat(loadedIssue.getTextRange().getEndLine()).isEqualTo(3);
    assertThat(loadedIssue.getTextRange().getEndLineOffset()).isEqualTo(4);
    assertThat(loadedIssue.getCodeSnippet()).isEqualTo("Primary location code");

    assertThat(loadedIssue.getFlows()).hasSize(1);
    assertThat(loadedIssue.getFlows().get(0).locations()).hasSize(3);
    assertThat(loadedIssue.getFlows().get(0).locations().get(0).getFilePath()).isEqualTo("src/path1");
    assertThat(loadedIssue.getFlows().get(0).locations().get(0).getMessage()).isEqualTo("Flow 1 - Location 1");
    assertThat(loadedIssue.getFlows().get(0).locations().get(0).getTextRange().getStartLine()).isEqualTo(5);
    assertThat(loadedIssue.getFlows().get(0).locations().get(0).getTextRange().getStartLineOffset()).isEqualTo(6);
    assertThat(loadedIssue.getFlows().get(0).locations().get(0).getTextRange().getEndLine()).isEqualTo(7);
    assertThat(loadedIssue.getFlows().get(0).locations().get(0).getTextRange().getEndLineOffset()).isEqualTo(8);
    assertThat(loadedIssue.getFlows().get(0).locations().get(0).getCodeSnippet()).isEqualTo("Some code snipper\n\t with newline");

    assertThat(loadedIssue.getFlows().get(0).locations().get(1).getMessage()).isEqualTo("Flow 1 - Location 2 - Without text range");
    assertThat(loadedIssue.getFlows().get(0).locations().get(1).getTextRange()).isNull();

    assertThat(loadedIssue.getFlows().get(0).locations().get(2).getMessage()).isNull();
    assertThat(loadedIssue.getFlows().get(0).locations().get(2).getFilePath()).isEqualTo("src/path2");
  }

  private final Comparator<ServerIssue> simpleComparator = (o1, o2) -> {
    if (Objects.equals(o1.getFilePath(), o2.getFilePath())) {
      return 0;
    }
    return 1;
  };

  private ServerIssue createServerIssue(String filePath) {
    return aServerIssue()
      .setFilePath(filePath);
  }
}
