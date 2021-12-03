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
package org.sonarsource.sonarlint.core.container.storage.partialupdate;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.JUnitTempFolder;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.exceptions.DownloadException;
import org.sonarsource.sonarlint.core.container.connected.IssueStore;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.IssueStorePaths;
import org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class PartialUpdaterTest {
  private static final ProgressWrapper PROGRESS = new ProgressWrapper(null);
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();
  @Rule
  public JUnitTempFolder tempFolder = new JUnitTempFolder();

  private final IssueStoreFactory issueStoreFactory = mock(IssueStoreFactory.class);
  private final IssueDownloader downloader = mock(IssueDownloader.class);
  private final ProjectStoragePaths projectStoragePaths = mock(ProjectStoragePaths.class);
  private final IssueStore issueStore = mock(IssueStore.class);
  private final IssueStorePaths issueStorePaths = mock(IssueStorePaths.class);
  private final Sonarlint.ProjectConfiguration projectConfiguration = Sonarlint.ProjectConfiguration.newBuilder().build();
  private final ProjectBinding projectBinding = new ProjectBinding("module", "", "");

  private PartialUpdater updater;

  @Before
  public void setUp() {
    updater = new PartialUpdater(issueStoreFactory, downloader, projectStoragePaths, issueStorePaths, tempFolder);
    when(issueStoreFactory.apply(any(Path.class))).thenReturn(issueStore);
  }

  @Test
  public void update_file_issues() {
    ServerIssue issue = ServerIssue.newBuilder().setKey("issue1").build();
    List<ServerIssue> issues = Collections.singletonList(issue);
    when(issueStorePaths.idePathToFileKey(projectConfiguration, projectBinding, "file")).thenReturn("module:file");
    when(projectStoragePaths.getServerIssuesPath("module")).thenReturn(temp.getRoot().toPath());
    when(downloader.download("module:file", projectConfiguration, false, null, PROGRESS)).thenReturn(issues);

    updater.updateFileIssues(projectBinding, projectConfiguration, "file", false, PROGRESS);

    verify(issueStore).save(anyList());
  }

  @Test
  public void update_file_issues_for_unknown_file() {
    when(issueStorePaths.idePathToFileKey(projectConfiguration, projectBinding, "file")).thenReturn(null);
    updater.updateFileIssues(projectBinding, projectConfiguration, "file", false, PROGRESS);
    verifyNoInteractions(downloader);
    verifyNoInteractions(issueStore);
  }

  @Test
  public void error_downloading_issues() {
    when(projectStoragePaths.getServerIssuesPath("module")).thenReturn(temp.getRoot().toPath());
    when(downloader.download("module:file", projectConfiguration, false, null, PROGRESS)).thenThrow(IllegalArgumentException.class);
    when(issueStorePaths.idePathToFileKey(projectConfiguration, projectBinding, "file")).thenReturn("module:file");

    assertThrows(DownloadException.class, () -> updater.updateFileIssues(projectBinding, projectConfiguration, "file", false, PROGRESS));
  }

  @Test
  public void update_file_issues_by_project() throws IOException {
    ServerIssue issue = ServerIssue.newBuilder().setKey("issue1").build();
    List<ServerIssue> issues = Collections.singletonList(issue);

    when(projectStoragePaths.getServerIssuesPath(projectBinding.projectKey())).thenReturn(temp.newFolder().toPath());
    when(downloader.download(projectBinding.projectKey(), projectConfiguration, false, null, PROGRESS)).thenReturn(issues);

    updater.updateFileIssues(projectBinding.projectKey(), projectConfiguration, false, PROGRESS);

    verify(issueStore).save(anyList());
  }
}
