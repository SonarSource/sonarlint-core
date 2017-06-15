/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.container.connected.InMemoryIssueStore;
import org.sonarsource.sonarlint.core.container.connected.IssueStore;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.model.DefaultServerIssue;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration.Builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IssueStoreReaderTest {
  private static final String MODULE_KEY = "root";
  private IssueStoreReader issueStoreReader;
  private IssueStore issueStore;
  private StorageReader storage;
  private StoragePaths storagePaths;

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Before
  public void setUp() {
    IssueStoreFactory issueStoreFactory = mock(IssueStoreFactory.class);
    issueStore = new InMemoryIssueStore();
    storage = mock(StorageReader.class);
    storagePaths = mock(StoragePaths.class);
    Path storagePath = mock(Path.class);
    when(storagePaths.getServerIssuesPath(MODULE_KEY)).thenReturn(storagePath);
    when(issueStoreFactory.apply(storagePath)).thenReturn(issueStore);
    issueStoreReader = new IssueStoreReader(issueStoreFactory, storage, storagePaths);
  }

  @Test
  public void testMultiModule() {
    // setup module hierarchy
    Map<String, String> modulePaths = new HashMap<>();
    modulePaths.put(MODULE_KEY, "");
    modulePaths.put("root:module1", "module1/src");
    modulePaths.put("root:module2", "module2");

    Builder moduleConfigBuilder = ModuleConfiguration.newBuilder();
    moduleConfigBuilder.getMutableModulePathByKey().putAll(modulePaths);

    when(storage.readModuleConfig(MODULE_KEY)).thenReturn(moduleConfigBuilder.build());

    // setup issues
    issueStore.save(Arrays.asList(
      createServerIssue("root:module1", "path1"),
      createServerIssue("root:module1", "path2"),
      createServerIssue("root:module2", "path1"),
      createServerIssue("root:module2", "path2")));

    // test

    // matches module1 path
    assertThat(issueStoreReader.getServerIssues(MODULE_KEY, "module1/src/path1"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createApiIssue(MODULE_KEY, "module1/src/path1"));

    // matches module2
    assertThat(issueStoreReader.getServerIssues(MODULE_KEY, "module2/path2"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createApiIssue(MODULE_KEY, "module2/path2"));

    // no file found
    assertThat(issueStoreReader.getServerIssues(MODULE_KEY, "module1/src/path3"))
      .isEmpty();

    // module not in storage
    exception.expect(IllegalStateException.class);
    exception.expectMessage("module not in storage");
    assertThat(issueStoreReader.getServerIssues("root:module1", "path2"))
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

    Builder moduleConfigBuilder = ModuleConfiguration.newBuilder();
    moduleConfigBuilder.getMutableModulePathByKey().putAll(modulePaths);

    when(storage.readModuleConfig(MODULE_KEY)).thenReturn(moduleConfigBuilder.build());

    // setup issues
    issueStore.save(Arrays.asList(
      createServerIssue("root:module1", "path1"),
      createServerIssue("root:module12", "path12")));

    // test

    // matches module12 path
    assertThat(issueStoreReader.getServerIssues(MODULE_KEY, "module12/path12"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createApiIssue(MODULE_KEY, "module12/path12"));
  }

  @Test
  public void testDontSetTypeIfDoesntExist() {
    // setup module hierarchy
    Map<String, String> modulePaths = new HashMap<>();
    modulePaths.put(MODULE_KEY, "");

    Builder moduleConfigBuilder = ModuleConfiguration.newBuilder();
    moduleConfigBuilder.getMutableModulePathByKey().putAll(modulePaths);

    when(storage.readModuleConfig(MODULE_KEY)).thenReturn(moduleConfigBuilder.build());

    ScannerInput.ServerIssue serverIssue = ScannerInput.ServerIssue.newBuilder()
      .setModuleKey(MODULE_KEY)
      .setPath("path")
      .build();

    issueStore.save(Collections.singletonList(serverIssue));

    ServerIssue issue = issueStoreReader.getServerIssues(MODULE_KEY, "path").iterator().next();
    assertThat(issue.type()).isNull();
  }

  @Test
  public void testSingleModule() {
    // setup module hierarchy
    Map<String, String> modulePaths = new HashMap<>();
    modulePaths.put(MODULE_KEY, "");

    Builder moduleConfigBuilder = ModuleConfiguration.newBuilder();
    moduleConfigBuilder.getMutableModulePathByKey().putAll(modulePaths);

    when(storage.readModuleConfig(MODULE_KEY)).thenReturn(moduleConfigBuilder.build());

    // setup issues
    issueStore.save(Arrays.asList(createServerIssue(MODULE_KEY, "src/path1")));

    // test

    // matches path
    assertThat(issueStoreReader.getServerIssues(MODULE_KEY, "src/path1"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createApiIssue(MODULE_KEY, "src/path1"));

    // no file found
    assertThat(issueStoreReader.getServerIssues(MODULE_KEY, "src/path3"))
      .isEmpty();

    // module not in storage
    exception.expect(IllegalStateException.class);
    exception.expectMessage("module not in storage");
    assertThat(issueStoreReader.getServerIssues("root:module1", "path2"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createApiIssue("module1", "path2"));
  }

  private Comparator<ServerIssue> simpleComparator = new Comparator<ServerIssue>() {
    @Override
    public int compare(ServerIssue o1, ServerIssue o2) {
      if (Objects.equal(o1.moduleKey(), o2.moduleKey()) && Objects.equal(o1.filePath(), o2.filePath())) {
        return 0;
      }
      return 1;
    }
  };

  private ServerIssue createApiIssue(String moduleKey, String filePath) {
    DefaultServerIssue issue = new DefaultServerIssue();
    issue.setFilePath(filePath);
    issue.setModuleKey(moduleKey);
    return issue;
  }

  private ScannerInput.ServerIssue createServerIssue(String moduleKey, String filePath) {
    return ScannerInput.ServerIssue.newBuilder()
      .setModuleKey(moduleKey)
      .setPath(filePath)
      .build();
  }
}
