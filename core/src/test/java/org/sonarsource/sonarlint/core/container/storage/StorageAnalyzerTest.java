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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectStorageStatus;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StorageAnalyzerTest {
  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Mock
  private GlobalUpdateStatusReader globalReader;
  @Mock
  private ProjectStorageStatusReader moduleReader;
  @Mock
  private ConnectedAnalysisConfiguration config;

  private StorageAnalyzer analyzer;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(config.projectKey()).thenReturn("module1");
    analyzer = new StorageAnalyzer(globalReader, moduleReader);
  }

  @Test
  public void testNoGlobalStorage() {
    when(globalReader.get()).thenReturn(null);

    exception.expect(StorageException.class);
    exception.expectMessage("Missing global data");
    analyzer.analyze(mock(StorageContainer.class), config, mock(IssueListener.class), new ProgressWrapper(null));
  }

  @Test
  public void testNoModuleStorage() {
    when(globalReader.get()).thenReturn(mock(GlobalStorageStatus.class));
    when(moduleReader.apply("module1")).thenReturn(null);

    exception.expect(StorageException.class);
    exception.expectMessage("No data stored for project");
    analyzer.analyze(mock(StorageContainer.class), config, mock(IssueListener.class), new ProgressWrapper(null));
  }

  @Test
  public void testStaleModuleStorage() {
    when(globalReader.get()).thenReturn(mock(GlobalStorageStatus.class));
    ProjectStorageStatus moduleStatus = mock(ProjectStorageStatus.class);
    when(moduleStatus.isStale()).thenReturn(true);
    when(moduleReader.apply("module1")).thenReturn(moduleStatus);

    exception.expect(StorageException.class);
    exception.expectMessage("Stored data for project 'module1' is stale");
    analyzer.analyze(mock(StorageContainer.class), config, mock(IssueListener.class), new ProgressWrapper(null));
  }

}
