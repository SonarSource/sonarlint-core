/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectServerIssueStore;
import testutils.InMemoryIssueStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ServerHotspotFixtures.aServerHotspot;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueFixtures.aServerIssue;

class IssueStoreReaderTests {
  private static final String PROJECT_KEY = "root";

  private IssueStoreReader issueStoreReader;
  private final ProjectServerIssueStore issueStore = new InMemoryIssueStore();
  private final ProjectBinding projectBinding = new ProjectBinding(PROJECT_KEY, "", "");

  @BeforeEach
  void setUp() {
    ConnectionStorage storage = mock(ConnectionStorage.class);
    SonarProjectStorage projectStorage = mock(SonarProjectStorage.class);
    when(storage.project(PROJECT_KEY)).thenReturn(projectStorage);
    when(projectStorage.findings()).thenReturn(issueStore);
    issueStoreReader = new IssueStoreReader(storage);
  }

  @Test
  void testSingleModule() {
    // setup issues
    issueStore.replaceAllIssuesOfBranch("branch", Collections.singletonList(createServerIssue("src/path1")));

    // test

    // matches path
    assertThat(issueStoreReader.getServerIssues(projectBinding, "branch", Path.of("src/path1")))
      .usingElementComparator(simpleComparator)
      .containsOnly(createServerIssue("src/path1"));

    // no file found
    assertThat(issueStoreReader.getServerIssues(projectBinding, "branch", Path.of("src/path3"))).isEmpty();

    // invalid prefixes
    var bindingWithPrefix = new ProjectBinding(PROJECT_KEY, "", "src");
    assertThat(issueStoreReader.getServerIssues(bindingWithPrefix, "branch", Path.of("src2/path2"))).isEmpty();
  }

  @Test
  void return_empty_list_if_local_path_is_invalid() {
    var projectBinding = new ProjectBinding(PROJECT_KEY, "", "local");
    issueStore.replaceAllIssuesOfBranch("branch", Collections.singletonList(createServerIssue("src/path1")));
    assertThat(issueStoreReader.getServerIssues(projectBinding, "branch", Path.of("src/path1")))
      .isEmpty();
  }

  @Test
  void testSingleModuleWithBothPrefixes() {
    var projectBinding = new ProjectBinding(PROJECT_KEY, "sq", "local");

    // setup issues
    issueStore.replaceAllIssuesOfBranch("branch", Collections.singletonList(createServerIssue("sq/src/path1")));

    // test

    // matches path
    assertThat(issueStoreReader.getServerIssues(projectBinding, "branch", Path.of("local/src/path1")))
      .usingElementComparator(simpleComparator)
      .containsOnly(createServerIssue("local/src/path1"));
  }

  @Test
  void testSingleModuleWithLocalPrefix() {
    var projectBinding = new ProjectBinding(PROJECT_KEY, "", "local");

    // setup issues
    issueStore.replaceAllIssuesOfBranch("branch", Collections.singletonList(createServerIssue("src/path1")));

    // test

    // matches path
    assertThat(issueStoreReader.getServerIssues(projectBinding, "branch", Path.of("local/src/path1")))
      .usingElementComparator(simpleComparator)
      .containsOnly(createServerIssue("local/src/path1"));
  }

  @Test
  void testSingleModuleWithSQPrefix() {
    var projectBinding = new ProjectBinding(PROJECT_KEY, "sq", "");

    // setup issues
    issueStore.replaceAllIssuesOfBranch("branch", Collections.singletonList(createServerIssue("sq/src/path1")));

    // test

    // matches path
    assertThat(issueStoreReader.getServerIssues(projectBinding, "branch", Path.of("src/path1")))
      .usingElementComparator(simpleComparator)
      .containsOnly(createServerIssue("src/path1"));
  }

  private final Comparator<ServerIssue> simpleComparator = (o1, o2) -> {
    if (Objects.equals(o1.getFilePath(), o2.getFilePath())) {
      return 0;
    }
    return 1;
  };

  private ServerIssue createServerIssue(String filePath) {
    return aServerIssue()
      .setFilePath(Path.of(filePath));
  }
}
