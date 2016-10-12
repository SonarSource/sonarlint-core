/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.Arrays;
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

import com.google.common.base.Objects;

public class IssueStoreGetterTest {
  private IssueStoreGetter issueStoreGetter;
  private IssueStore issueStore;
  private StorageManager storage;

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Before
  public void setUp() {
    IssueStoreFactory issueStoreFactory = mock(IssueStoreFactory.class);
    issueStore = new InMemoryIssueStore();
    when(issueStoreFactory.apply(any(Path.class))).thenReturn(issueStore);
    storage = mock(StorageManager.class);
    issueStoreGetter = new IssueStoreGetter(issueStoreFactory, storage);
  }

  @Test
  public void test() {
    // setup module hierarchy
    Map<String, String> modulePaths = new HashMap<>();
    modulePaths.put("root", "");
    modulePaths.put("root:module1", "module1/src");
    modulePaths.put("root:module2", "module2");

    Builder moduleConfigBuilder = ModuleConfiguration.newBuilder();
    moduleConfigBuilder.getMutableModulePathByKey().putAll(modulePaths);

    when(storage.readModuleConfigFromStorage("root")).thenReturn(moduleConfigBuilder.build());

    // setup issues
    issueStore.save(Arrays.asList(
      createServerIssue("root:module1", "path1"),
      createServerIssue("root:module1", "path2"),
      createServerIssue("root:module2", "path1"),
      createServerIssue("root:module2", "path2")).iterator());

    // test

    // matches module1 path
    assertThat(issueStoreGetter.getServerIssues("root", "module1/src/path1"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createApiIssue("root", "module1/src/path1"));

    // matches module2
    assertThat(issueStoreGetter.getServerIssues("root", "module2/path2"))
      .usingElementComparator(simpleComparator)
      .containsOnly(createApiIssue("root", "module2/path2"));

    // no file found
    assertThat(issueStoreGetter.getServerIssues("root", "module1/src/path3"))
      .isEmpty();

    // module not in storage
    exception.expect(IllegalStateException.class);
    exception.expectMessage("module not in storage");
    assertThat(issueStoreGetter.getServerIssues("root:module1", "path2"))
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
