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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.LinkedList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonar.api.utils.TempFolder;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.plugin.PluginRepository;

public class StorageContainerHandlerTest {
  @Mock
  private StorageAnalyzer storageAnalyzer;
  @Mock
  private StorageRuleDetailsReader storageRuleDetailsReader;
  @Mock
  private GlobalUpdateStatusReader globalUpdateStatusReader;
  @Mock
  private PluginRepository pluginRepository;
  @Mock
  private ModuleStorageStatusReader moduleStorageStatusReader;
  @Mock
  private IssueStoreReader issueStoreReader;
  @Mock
  private AllModulesReader allModulesReader;
  @Mock
  private StoragePaths storagePaths;
  @Mock
  private StorageReader storageReader;
  @Mock
  private TempFolder tempFolder;
  @Mock
  private StorageContainerHandler handler;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    handler = new StorageContainerHandler(storageAnalyzer, storageRuleDetailsReader, globalUpdateStatusReader,
      pluginRepository, moduleStorageStatusReader, issueStoreReader, allModulesReader, storagePaths, storageReader, tempFolder);
  }

  @Test
  public void testSeverIssues() {
    List<ServerIssue> issueList = new LinkedList<>();
    when(issueStoreReader.getServerIssues("module", "file")).thenReturn(issueList);
    assertThat(handler.getServerIssues("module", "file")).isEqualTo(issueList);
  }

}
