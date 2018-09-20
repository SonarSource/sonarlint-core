/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IssueStoreReaderTest {
  private static final String MODULE_KEY = "root";

  private IssueStoreReader issueStoreReader;
  private IssueStore issueStore = new InMemoryIssueStore();
  private StoragePaths storagePaths = mock(StoragePaths.class);
  private StorageReader storageReader = mock(StorageReader.class);
  private IssueStorePaths issueStorePaths = new IssueStorePaths();
  private ProjectBinding projectBinding = new ProjectBinding(MODULE_KEY, "", "");
  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Before
  public void setUp() {
    IssueStoreFactory issueStoreFactory = mock(IssueStoreFactory.class);
    Path storagePath = mock(Path.class);
    when(storagePaths.getServerIssuesPath(MODULE_KEY)).thenReturn(storagePath);
    when(issueStoreFactory.apply(storagePath)).thenReturn(issueStore);

    issueStoreReader = new IssueStoreReader(issueStoreFactory, issueStorePaths, storagePaths, storageReader);
  }

  private void setModulePaths(Map<String, String> modulePaths) {
    Builder moduleConfigBuilder = ProjectConfiguration.newBuilder();
    moduleConfigBuilder.getMutableModulePathByKey().putAll(modulePaths);

    when(storageReader.readProjectConfig(MODULE_KEY)).thenReturn(moduleConfigBuilder.build());
  }

  @Test
  public void testMultiModule() {
    // setup module hierarchy
    Map<String, String> modulePaths = new HashMap<>();
    modulePaths.put(MODULE_KEY, "");
    modulePaths.put("root:module1", "module1/src");
    modulePaths.put("root:module2", "module2");
    setModulePaths(modulePaths);

    // setup issues
    issueStore.save(Arrays.asList(
      createServerIssue(MODULE_KEY, "module1/src/path1"),
      createServerIssue(MODULE_KEY, "module1/src/path2"),
      createServerIssue(MODULE_KEY, "module2/path1"),
      createServerIssue(MODULE_KEY, "module2/path2")));

    // test

    // matches module1 path
    assertThat(issueStoreReader.getServerIssues(projectBinding, "module1/src/path1"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createApiIssue(MODULE_KEY, "module1/src/path1"));

    // matches module2
    assertThat(issueStoreReader.getServerIssues(projectBinding, "module2/path2"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createApiIssue(MODULE_KEY, "module2/path2"));

    // no file found
    assertThat(issueStoreReader.getServerIssues(projectBinding, "module1/src/path3"))
      .isEmpty();

    // module not in storage
    exception.expect(IllegalStateException.class);
    exception.expectMessage("project not in storage");
    assertThat(issueStoreReader.getServerIssues(new ProjectBinding("root:module1", "", ""), "path2"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createApiIssue("module1", "path2"));
  }

  @Test
  public void testMultiModule2() {
    // setup module hierarchy
    Map<String, String> modulePaths = new HashMap<>();
    modulePaths.put(MODULE_KEY, "");
    modulePaths.put("root:module1", "module1");
    modulePaths.put("root:module12", "module12");
    setModulePaths(modulePaths);

    // setup issues
    issueStore.save(Arrays.asList(
      createServerIssue(MODULE_KEY, "module12/path1"),
      createServerIssue(MODULE_KEY, "module12/path12")));

    // test

    // matches module12 path
    assertThat(issueStoreReader.getServerIssues(projectBinding, "module12/path12"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createApiIssue(MODULE_KEY, "module12/path12"));
  }

  @Test
  public void testDontSetTypeIfDoesntExist() {
    setModulePaths(Collections.singletonMap(MODULE_KEY, ""));

    Sonarlint.ServerIssue serverIssue = Sonarlint.ServerIssue.newBuilder()
      .setPath("path")
      .build();

    issueStore.save(Collections.singletonList(serverIssue));

    ServerIssue issue = issueStoreReader.getServerIssues(projectBinding, "path").iterator().next();
    assertThat(issue.type()).isNull();
  }

  @Test
  public void testSingleModule() {
    setModulePaths(Collections.singletonMap(MODULE_KEY, ""));

    // setup issues
    issueStore.save(Collections.singletonList(createServerIssue(MODULE_KEY, "src/path1")));

    // test

    // matches path
    assertThat(issueStoreReader.getServerIssues(projectBinding, "src/path1"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createApiIssue(MODULE_KEY, "src/path1"));

    // no file found
    assertThat(issueStoreReader.getServerIssues(projectBinding, "src/path3"))
      .isEmpty();

    // module not in storage
    exception.expect(IllegalStateException.class);
    exception.expectMessage("project not in storage");
    issueStoreReader.getServerIssues(new ProjectBinding("unknown", "", ""), "path2");
  }

  @Test
  public void testSingleModuleWithBothPrefixes() {
    setModulePaths(Collections.singletonMap(MODULE_KEY, ""));
    ProjectBinding projectBinding = new ProjectBinding(MODULE_KEY, "sq", "local");

    // setup issues
    issueStore.save(Collections.singletonList(createServerIssue(MODULE_KEY, "sq/src/path1")));

    // test

    // matches path
    assertThat(issueStoreReader.getServerIssues(projectBinding, "local/src/path1"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createApiIssue(MODULE_KEY, "local/src/path1"));
  }

  @Test
  public void testSingleModuleWithLocalPrefix() {
    setModulePaths(Collections.singletonMap(MODULE_KEY, ""));
    ProjectBinding projectBinding = new ProjectBinding(MODULE_KEY, "", "local");

    // setup issues
    issueStore.save(Collections.singletonList(createServerIssue(MODULE_KEY, "src/path1")));

    // test

    // matches path
    assertThat(issueStoreReader.getServerIssues(projectBinding, "local/src/path1"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createApiIssue(MODULE_KEY, "local/src/path1"));
  }

  @Test
  public void testSingleModuleWithSQPrefix() {
    setModulePaths(Collections.singletonMap(MODULE_KEY, ""));
    ProjectBinding projectBinding = new ProjectBinding(MODULE_KEY, "sq", "");

    // setup issues
    issueStore.save(Collections.singletonList(createServerIssue(MODULE_KEY, "sq/src/path1")));

    // test

    // matches path
    assertThat(issueStoreReader.getServerIssues(projectBinding, "src/path1"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createApiIssue(MODULE_KEY, "src/path1"));
  }

  private Comparator<ServerIssue> simpleComparator = (o1, o2) -> {
    if (Objects.equal(o1.moduleKey(), o2.moduleKey()) && Objects.equal(o1.filePath(), o2.filePath())) {
      return 0;
    }
    return 1;
  };

  private ServerIssue createApiIssue(String moduleKey, String filePath) {
    return new DefaultServerIssue()
      .setFilePath(filePath)
      .setModuleKey(moduleKey);
  }

  private Sonarlint.ServerIssue createServerIssue(String moduleKey, String filePath) {
    return Sonarlint.ServerIssue.newBuilder()
      .setPath(filePath)
      .setModuleKey(moduleKey)
      .build();
  }
}
