/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2022 SonarSource SA
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueStore;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueFixtures.aServerIssue;

class PartialUpdaterTests {
  private static final ProgressMonitor PROGRESS = new ProgressMonitor(null);

  private final IssueDownloader downloader = mock(IssueDownloader.class);
  private final ProjectStoragePaths projectStoragePaths = mock(ProjectStoragePaths.class);
  private final ServerIssueStore issueStore = mock(ServerIssueStore.class);
  private ProjectBinding projectBinding = new ProjectBinding("module", "", "");

  private PartialUpdater updater;

  @BeforeEach
  void setUp() {
    updater = new PartialUpdater(__ -> issueStore, downloader, projectStoragePaths);
  }

  @Test
  void update_file_issues(@TempDir Path tmp) {
    var issue = aServerIssue();
    List<ServerIssue> issues = Collections.singletonList(issue);
    when(projectStoragePaths.getServerIssuesPath("module")).thenReturn(tmp);
    var serverApiHelper = mock(ServerApiHelper.class);
    when(downloader.download(serverApiHelper, "module:file", false, null, PROGRESS)).thenReturn(issues);

    updater.updateFileIssues(serverApiHelper, projectBinding.projectKey(), false, null, PROGRESS);

    verify(issueStore).save(anyList());
  }

  @Test
  void update_file_issues_for_unknown_file(@TempDir Path tmp) {
    projectBinding = new ProjectBinding("module", "", "ide_prefix");
    when(projectStoragePaths.getServerIssuesPath("module")).thenReturn(tmp);

    updater.updateFileIssues(mock(ServerApiHelper.class), projectBinding, "not_ide_prefix", false, null, PROGRESS);

    verifyNoInteractions(downloader);
    verifyNoInteractions(issueStore);
  }

  @Test
  void error_downloading_issues(@TempDir Path tmp) {
    when(projectStoragePaths.getServerIssuesPath("module")).thenReturn(tmp);
    var serverApiHelper = mock(ServerApiHelper.class);
    when(downloader.download(serverApiHelper, "module:file", false, null, PROGRESS)).thenThrow(IllegalArgumentException.class);
    // when(issueStorePaths.idePathToFileKey(projectBinding, "file")).thenReturn("module:file");

    assertThrows(DownloadException.class, () -> updater.updateFileIssues(serverApiHelper, projectBinding, "file", false, null, PROGRESS));
  }

  @Test
  void update_file_issues_by_project(@TempDir Path tmp) {
    var issue = aServerIssue();
    List<ServerIssue> issues = Collections.singletonList(issue);

    when(projectStoragePaths.getServerIssuesPath(projectBinding.projectKey())).thenReturn(tmp);
    var serverApiHelper = mock(ServerApiHelper.class);
    when(downloader.download(serverApiHelper, projectBinding.projectKey(), false, null, PROGRESS)).thenReturn(issues);

    updater.updateFileIssues(serverApiHelper, projectBinding.projectKey(), false, null, PROGRESS);

    verify(issueStore).save(anyList());
  }
}
