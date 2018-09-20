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
package org.sonarsource.sonarlint.core.container.storage.partialupdate;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.utils.TempFolder;
import org.sonar.api.utils.internal.DefaultTempFolder;
import org.sonar.api.utils.internal.JUnitTempFolder;
import org.sonar.scanner.protocol.input.ScannerInput.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.exceptions.DownloadException;
import org.sonarsource.sonarlint.core.container.connected.IssueStore;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.IssueStorePaths;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectListDownloader;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyListOf;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class PartialUpdaterTest {
  private static final String SERVER_VERSION = "6.0";
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();
  @Rule
  public JUnitTempFolder tempFolder = new JUnitTempFolder();
  @Rule
  public ExpectedException exception = ExpectedException.none();

  private IssueStoreFactory issueStoreFactory = mock(IssueStoreFactory.class);
  private IssueDownloader downloader = mock(IssueDownloader.class);
  private StoragePaths storagePaths = mock(StoragePaths.class);
  private StorageReader storageReader = mock(StorageReader.class);
  private IssueStore issueStore = mock(IssueStore.class);
  private ProjectListDownloader projectListDownloader = mock(ProjectListDownloader.class);
  private IssueStorePaths issueStorePaths = mock(IssueStorePaths.class);
  private Sonarlint.ProjectConfiguration projectConfiguration = Sonarlint.ProjectConfiguration.newBuilder().build();
  private ProjectBinding projectBinding = new ProjectBinding("module", "", "");

  private PartialUpdater updater;

  @Before
  public void setUp() {
    updater = new PartialUpdater(issueStoreFactory, downloader, storageReader, storagePaths, projectListDownloader, issueStorePaths, tempFolder);
    when(issueStoreFactory.apply(any(Path.class))).thenReturn(issueStore);
    when(storageReader.readServerInfos()).thenReturn(ServerInfos.newBuilder().setVersion(SERVER_VERSION).build());
  }

  @Test
  public void update_file_issues() {
    ServerIssue issue = ServerIssue.newBuilder().setKey("issue1").build();
    List<ServerIssue> issues = Collections.singletonList(issue);
    when(issueStorePaths.localPathToFileKey(projectConfiguration, projectBinding, "file")).thenReturn("module:file");
    when(storagePaths.getServerIssuesPath("module")).thenReturn(temp.getRoot().toPath());
    when(downloader.apply("module:file")).thenReturn(issues);

    updater.updateFileIssues(projectBinding, projectConfiguration, "file");

    verify(issueStore).save(anyListOf(Sonarlint.ServerIssue.class));
  }

  @Test
  public void update_file_issues_for_unknown_file() {
    when(issueStorePaths.localPathToFileKey(projectConfiguration, projectBinding, "file")).thenReturn(null);
    updater.updateFileIssues(projectBinding, projectConfiguration, "file");
    verifyZeroInteractions(downloader);
    verifyZeroInteractions(issueStore);
  }

  @Test
  public void error_downloading_issues() {
    when(storagePaths.getServerIssuesPath("module")).thenReturn(temp.getRoot().toPath());
    when(downloader.apply("module:file")).thenThrow(IllegalArgumentException.class);
    when(issueStorePaths.localPathToFileKey(projectConfiguration, projectBinding, "file")).thenReturn("module:file");

    exception.expect(DownloadException.class);
    updater.updateFileIssues(projectBinding, projectConfiguration, "file");
  }

  @Test
  public void update_file_issues_by_module() throws IOException {
    ServerIssue issue = ServerIssue.newBuilder().setKey("issue1").setModuleKey(projectBinding.projectKey()).build();
    List<ServerIssue> issues = Collections.singletonList(issue);

    when(storagePaths.getServerIssuesPath(projectBinding.projectKey())).thenReturn(temp.newFolder().toPath());
    when(downloader.apply(projectBinding.projectKey())).thenReturn(issues);

    updater.updateFileIssues(projectBinding.projectKey(), projectConfiguration);

    verify(issueStore).save(anyListOf(Sonarlint.ServerIssue.class));
  }

  @Test
  public void error_downloading_modules() {
    when(storagePaths.getGlobalStorageRoot()).thenReturn(temp.getRoot().toPath());
    doThrow(IllegalArgumentException.class).when(projectListDownloader).fetchTo(eq(temp.getRoot().toPath()), eq(SERVER_VERSION), any(ProgressWrapper.class));
    exception.expect(DownloadException.class);

    updater.updateProjectList(new ProgressWrapper(null));
  }

  @Test
  public void update_module_list() {
    when(storagePaths.getGlobalStorageRoot()).thenReturn(temp.getRoot().toPath());
    updater.updateProjectList(new ProgressWrapper(null));
    verify(projectListDownloader).fetchTo(eq(temp.getRoot().toPath()), eq(SERVER_VERSION), any(ProgressWrapper.class));
  }
}
