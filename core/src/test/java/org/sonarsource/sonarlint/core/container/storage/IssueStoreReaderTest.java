/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.storage;

import com.google.common.base.Objects;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.container.connected.InMemoryIssueStore;
import org.sonarsource.sonarlint.core.container.connected.IssueStore;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.update.IssueStorePaths;
import org.sonarsource.sonarlint.core.container.model.DefaultServerIssue;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectConfiguration;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectConfiguration.Builder;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue.Flow;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue.Location;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue.TextRange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IssueStoreReaderTest {
  private static final String PROJECT_KEY = "root";

  private IssueStoreReader issueStoreReader;
  private final IssueStore issueStore = new InMemoryIssueStore();
  private final StoragePaths storagePaths = mock(StoragePaths.class);
  private final StorageReader storageReader = mock(StorageReader.class);
  private final IssueStorePaths issueStorePaths = new IssueStorePaths();
  private final ProjectBinding projectBinding = new ProjectBinding(PROJECT_KEY, "", "");
  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Before
  public void setUp() {
    IssueStoreFactory issueStoreFactory = mock(IssueStoreFactory.class);
    Path storagePath = mock(Path.class);
    when(storagePaths.getServerIssuesPath(PROJECT_KEY)).thenReturn(storagePath);
    when(issueStoreFactory.apply(storagePath)).thenReturn(issueStore);

    issueStoreReader = new IssueStoreReader(issueStoreFactory, issueStorePaths, storagePaths, storageReader);
  }

  private void setModulePaths(Map<String, String> modulePaths) {
    Builder moduleConfigBuilder = ProjectConfiguration.newBuilder();
    moduleConfigBuilder.getMutableModulePathByKey().putAll(modulePaths);

    when(storageReader.readProjectConfig(PROJECT_KEY)).thenReturn(moduleConfigBuilder.build());
  }

  @Test
  public void testMultiModule() {
    // setup module hierarchy
    Map<String, String> modulePaths = new HashMap<>();
    modulePaths.put(PROJECT_KEY, "");
    modulePaths.put("root:module1", "module1/src");
    modulePaths.put("root:module2", "module2");
    setModulePaths(modulePaths);

    // setup issues
    issueStore.save(Arrays.asList(
      createServerIssue("module1/src/path1"),
      createServerIssue("module1/src/path2"),
      createServerIssue("module2/path1"),
      createServerIssue("module2/path2")));

    // test

    // matches module1 path
    assertThat(issueStoreReader.getServerIssues(projectBinding, "module1/src/path1"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createApiIssue("module1/src/path1"));

    // matches module2
    assertThat(issueStoreReader.getServerIssues(projectBinding, "module2/path2"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createApiIssue("module2/path2"));

    // no file found
    assertThat(issueStoreReader.getServerIssues(projectBinding, "module1/src/path3"))
      .isEmpty();

    // module not in storage
    exception.expect(IllegalStateException.class);
    exception.expectMessage("project not in storage");
    assertThat(issueStoreReader.getServerIssues(new ProjectBinding("root:module1", "", ""), "path2"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createApiIssue("path2"));
  }

  @Test
  public void testMultiModule2() {
    // setup module hierarchy
    Map<String, String> modulePaths = new HashMap<>();
    modulePaths.put(PROJECT_KEY, "");
    modulePaths.put("root:module1", "module1");
    modulePaths.put("root:module12", "module12");
    setModulePaths(modulePaths);

    // setup issues
    issueStore.save(Arrays.asList(
      createServerIssue("module12/path1"),
      createServerIssue("module12/path12")));

    // test

    // matches module12 path
    assertThat(issueStoreReader.getServerIssues(projectBinding, "module12/path12"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createApiIssue("module12/path12"));
  }

  @Test
  public void testSingleModule() {
    setModulePaths(Collections.singletonMap(PROJECT_KEY, ""));

    // setup issues
    issueStore.save(Collections.singletonList(createServerIssue("src/path1")));

    // test

    // matches path
    assertThat(issueStoreReader.getServerIssues(projectBinding, "src/path1"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createApiIssue("src/path1"));

    // no file found
    assertThat(issueStoreReader.getServerIssues(projectBinding, "src/path3"))
      .isEmpty();

    // module not in storage
    exception.expect(IllegalStateException.class);
    exception.expectMessage("project not in storage");
    issueStoreReader.getServerIssues(new ProjectBinding("unknown", "", ""), "path2");
  }

  @Test
  public void return_empty_list_if_local_path_is_invalid() {
    setModulePaths(Collections.singletonMap(PROJECT_KEY, ""));
    ProjectBinding projectBinding = new ProjectBinding(PROJECT_KEY, "", "local");
    issueStore.save(Collections.singletonList(createServerIssue("src/path1")));
    assertThat(issueStoreReader.getServerIssues(projectBinding, "src/path1"))
      .isEmpty();
  }

  @Test
  public void testSingleModuleWithBothPrefixes() {
    setModulePaths(Collections.singletonMap(PROJECT_KEY, ""));
    ProjectBinding projectBinding = new ProjectBinding(PROJECT_KEY, "sq", "local");

    // setup issues
    issueStore.save(Collections.singletonList(createServerIssue("sq/src/path1")));

    // test

    // matches path
    assertThat(issueStoreReader.getServerIssues(projectBinding, "local/src/path1"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createApiIssue("local/src/path1"));
  }

  @Test
  public void testSingleModuleWithLocalPrefix() {
    setModulePaths(Collections.singletonMap(PROJECT_KEY, ""));
    ProjectBinding projectBinding = new ProjectBinding(PROJECT_KEY, "", "local");

    // setup issues
    issueStore.save(Collections.singletonList(createServerIssue("src/path1")));

    // test

    // matches path
    assertThat(issueStoreReader.getServerIssues(projectBinding, "local/src/path1"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createApiIssue("local/src/path1"));
  }

  @Test
  public void testSingleModuleWithSQPrefix() {
    setModulePaths(Collections.singletonMap(PROJECT_KEY, ""));
    ProjectBinding projectBinding = new ProjectBinding(PROJECT_KEY, "sq", "");

    // setup issues
    issueStore.save(Collections.singletonList(createServerIssue("sq/src/path1")));

    // test

    // matches path
    assertThat(issueStoreReader.getServerIssues(projectBinding, "src/path1"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createApiIssue("src/path1"));
  }

  @Test
  public void canReadFlowsFromStorage() {
    setModulePaths(Collections.singletonMap(PROJECT_KEY, ""));

    // setup issues
    issueStore.save(Collections.singletonList(Sonarlint.ServerIssue.newBuilder()
      .setPrimaryLocation(
        Location.newBuilder()
          .setPath("src/path1")
          .setMsg("Primary")
          .setTextRange(TextRange.newBuilder().setStartLine(1).setStartLineOffset(2).setEndLine(3).setEndLineOffset(4))
          .setCodeSnippet("Primary location code"))
      .addFlow(Flow.newBuilder()
        .addLocation(Location.newBuilder().setPath("src/path1").setMsg("Flow 1 - Location 1")
          .setTextRange(TextRange.newBuilder().setStartLine(5).setStartLineOffset(6).setEndLine(7).setEndLineOffset(8))
          .setCodeSnippet("Some code snipper\n\t with newline"))
        .addLocation(Location.newBuilder().setPath("src/path1").setMsg("Flow 1 - Location 2 - Without text range"))
        .addLocation(Location.newBuilder().setPath("src/path2")))
      .build()));

    List<ServerIssue> issuesReadFromStorage = issueStoreReader.getServerIssues(projectBinding, "src/path1");
    assertThat(issuesReadFromStorage).hasSize(1);
    ServerIssue serverIssue = issuesReadFromStorage.get(0);
    assertThat(serverIssue.getFilePath()).isEqualTo("src/path1");
    assertThat(serverIssue.getMessage()).isEqualTo("Primary");
    assertThat(serverIssue.getTextRange().getStartLine()).isEqualTo(1);
    assertThat(serverIssue.getTextRange().getStartLineOffset()).isEqualTo(2);
    assertThat(serverIssue.getTextRange().getEndLine()).isEqualTo(3);
    assertThat(serverIssue.getTextRange().getEndLineOffset()).isEqualTo(4);
    assertThat(serverIssue.getCodeSnippet()).isEqualTo("Primary location code");

    assertThat(serverIssue.getFlows()).hasSize(1);
    assertThat(serverIssue.getFlows().get(0).getLocations()).hasSize(3);
    assertThat(serverIssue.getFlows().get(0).getLocations().get(0).getFilePath()).isEqualTo("src/path1");
    assertThat(serverIssue.getFlows().get(0).getLocations().get(0).getMessage()).isEqualTo("Flow 1 - Location 1");
    assertThat(serverIssue.getFlows().get(0).getLocations().get(0).getTextRange().getStartLine()).isEqualTo(5);
    assertThat(serverIssue.getFlows().get(0).getLocations().get(0).getTextRange().getStartLineOffset()).isEqualTo(6);
    assertThat(serverIssue.getFlows().get(0).getLocations().get(0).getTextRange().getEndLine()).isEqualTo(7);
    assertThat(serverIssue.getFlows().get(0).getLocations().get(0).getTextRange().getEndLineOffset()).isEqualTo(8);
    assertThat(serverIssue.getFlows().get(0).getLocations().get(0).getCodeSnippet()).isEqualTo("Some code snipper\n\t with newline");

    assertThat(serverIssue.getFlows().get(0).getLocations().get(1).getMessage()).isEqualTo("Flow 1 - Location 2 - Without text range");
    assertThat(serverIssue.getFlows().get(0).getLocations().get(1).getTextRange()).isNull();

    assertThat(serverIssue.getFlows().get(0).getLocations().get(2).getMessage()).isEmpty();
    assertThat(serverIssue.getFlows().get(0).getLocations().get(2).getFilePath()).isEqualTo("src/path2");
  }

  private final Comparator<ServerIssue> simpleComparator = (o1, o2) -> {
    if (Objects.equal(o1.getFilePath(), o2.getFilePath())) {
      return 0;
    }
    return 1;
  };

  private ServerIssue createApiIssue(String filePath) {
    return new DefaultServerIssue()
      .setFilePath(filePath);
  }

  private Sonarlint.ServerIssue createServerIssue(String filePath) {
    return Sonarlint.ServerIssue.newBuilder()
      .setPrimaryLocation(Location.newBuilder().setPath(filePath))
      .build();
  }
}
