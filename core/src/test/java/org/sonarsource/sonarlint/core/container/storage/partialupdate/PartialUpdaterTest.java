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
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.sonar.api.utils.internal.DefaultTempFolder;
import org.sonar.scanner.protocol.input.ScannerInput.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectId;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.exceptions.DownloadException;
import org.sonarsource.sonarlint.core.container.connected.IssueStore;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ModuleListDownloader;
import org.sonarsource.sonarlint.core.container.storage.IssueStoreReader;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PartialUpdaterTest {
  private static final String SERVER_VERSION = "6.0";
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();
  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Mock
  private IssueStoreFactory issueStoreFactory;
  @Mock
  private IssueDownloader downloader;
  @Mock
  private StorageManager storageManager;
  @Mock
  private IssueStoreReader issueStoreReader;
  @Mock
  private IssueStore issueStore;
  @Mock
  private ModuleListDownloader moduleListDownloader;

  private PartialUpdater updater;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    updater = new PartialUpdater(issueStoreFactory, downloader, storageManager, issueStoreReader, moduleListDownloader);
    when(issueStoreFactory.apply(Mockito.any(Path.class))).thenReturn(issueStore);
    when(storageManager.readServerInfosFromStorage()).thenReturn(ServerInfos.newBuilder().setVersion(SERVER_VERSION).build());
  }

  @Test
  public void update_file_issues() {
    ServerIssue issue = ServerIssue.newBuilder().setKey("issue1").build();
    List<ServerIssue> issues = Collections.singletonList(issue);

    ProjectId projectId = new ProjectId(null, "module");
    when(storageManager.getServerIssuesPath(projectId)).thenReturn(temp.getRoot().toPath());
    when(issueStoreReader.getFileKey(projectId, "file")).thenReturn("module:file");
    when(downloader.download(null, "module:file")).thenReturn(issues);

    updater.updateFileIssues(projectId, "file");

    verify(issueStore).save(issues);
  }

  @Test
  public void error_downloading_issues() {
    ProjectId projectId = new ProjectId(null, "module");
    when(storageManager.getServerIssuesPath(projectId)).thenReturn(temp.getRoot().toPath());
    when(issueStoreReader.getFileKey(projectId, "file")).thenReturn("module:file");
    when(downloader.download(null, "module:file")).thenThrow(IOException.class);

    exception.expect(DownloadException.class);
    updater.updateFileIssues(projectId, "file");
  }

  @Test
  public void update_file_issues_by_module() throws IOException {
    ServerIssue issue = ServerIssue.newBuilder().setKey("issue1").build();
    List<ServerIssue> issues = Collections.singletonList(issue);

    String moduleKey = "dummy";
    ProjectId projectId = new ProjectId(null, moduleKey);
    when(storageManager.getServerIssuesPath(projectId)).thenReturn(temp.newFolder().toPath());
    when(downloader.download(null, moduleKey)).thenReturn(issues);

    updater.updateFileIssues(projectId, new DefaultTempFolder(temp.newFolder()));

    verify(issueStore).save(issues);
  }

  @Test
  public void create() {
    ServerConfiguration serverConfiguration = mock(ServerConfiguration.class);
    when(serverConfiguration.getUrl()).thenReturn("http://fake.com");
    assertThat(PartialUpdater.create(storageManager, serverConfiguration, issueStoreReader)).isNotNull();
  }
}
