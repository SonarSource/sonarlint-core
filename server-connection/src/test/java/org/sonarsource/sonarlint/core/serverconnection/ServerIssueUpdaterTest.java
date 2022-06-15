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

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.issue.IssueApi;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueStore;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueFixtures.aServerIssue;

class ServerIssueUpdaterTest {

  private static final ProgressMonitor PROGRESS = new ProgressMonitor(null);

  private final IssueDownloader downloader = mock(IssueDownloader.class);
  private final ServerIssueStore issueStore = mock(ServerIssueStore.class);
  private ProjectBinding projectBinding = new ProjectBinding("module", "", "");

  private ServerIssueUpdater updater;

  @BeforeEach
  void setUp() {
    updater = new ServerIssueUpdater(issueStore, downloader);
  }

  @Test
  void update_project_issues_before_sq_9_5() {
    var issue = aServerIssue();
    List<ServerIssue> issues = Collections.singletonList(issue);
    var serverApiHelper = mock(ServerApiHelper.class);
    when(downloader.downloadFromBatch(serverApiHelper, "module:file", null)).thenReturn(issues);

    updater.update(serverApiHelper, projectBinding.projectKey(), "branch", false, Version.create("8.9"));

    verify(issueStore).replaceAllIssuesOfProject(eq(projectBinding.projectKey()), eq("branch"), anyList());
  }

  @Test
  void update_project_issues_sonarcloud() {
    var issue = aServerIssue();
    List<ServerIssue> issues = Collections.singletonList(issue);
    var serverApiHelper = mock(ServerApiHelper.class);
    when(downloader.downloadFromBatch(serverApiHelper, "module:file", null)).thenReturn(issues);

    updater.update(serverApiHelper, projectBinding.projectKey(), "branch", true, Version.create("99.9"));

    verify(issueStore).replaceAllIssuesOfProject(eq(projectBinding.projectKey()), eq("branch"), anyList());
  }

  @Test
  void update_project_issues_sonarqube_9_5() {
    var issue = aServerIssue();
    List<ServerIssue> issues = Collections.singletonList(issue);
    var serverApiHelper = mock(ServerApiHelper.class);
    when(downloader.downloadFromPull(serverApiHelper, "module:file", "master")).thenReturn(issues);

    updater.update(serverApiHelper, projectBinding.projectKey(), "master", false, IssueApi.MIN_SQ_VERSION_SUPPORTING_PULL);

    verify(issueStore).replaceAllIssuesOfProject(eq(projectBinding.projectKey()), eq("master"), anyList());
  }

  @Test
  void update_file_issues_for_unknown_file() {
    projectBinding = new ProjectBinding("module", "", "ide_prefix");

    updater.updateFileIssues(mock(ServerApiHelper.class), projectBinding, "not_ide_prefix", null, false, Version.create("8.9"), PROGRESS);

    verifyNoInteractions(downloader);
    verifyNoInteractions(issueStore);
  }

  @Test
  void error_downloading_file_issues() {
    var serverApiHelper = mock(ServerApiHelper.class);
    when(downloader.downloadFromBatch(serverApiHelper, "module:file", null)).thenThrow(IllegalArgumentException.class);
    // when(issueStorePaths.idePathToFileKey(projectBinding, "file")).thenReturn("module:file");

    assertThrows(DownloadException.class, () -> updater.updateFileIssues(serverApiHelper, projectBinding, "file", null, false, Version.create("8.9"), PROGRESS));
  }

  @Test
  void update_file_issues_before_sonarqube_9_5() {
    var issue = aServerIssue();
    List<ServerIssue> issues = Collections.singletonList(issue);

    var serverApiHelper = mock(ServerApiHelper.class);
    when(downloader.downloadFromBatch(serverApiHelper, projectBinding.projectKey() + ":src/main/Foo.java", null)).thenReturn(issues);

    updater.updateFileIssues(serverApiHelper, projectBinding, "src/main/Foo.java", "branch", false, Version.create("8.9"), PROGRESS);

    verify(issueStore).replaceAllIssuesOfFile(eq(projectBinding.projectKey()), eq("branch"), eq("src/main/Foo.java"), anyList());
  }

  @Test
  void update_file_issues_sonarcloud() {
    var issue = aServerIssue();
    List<ServerIssue> issues = Collections.singletonList(issue);

    var serverApiHelper = mock(ServerApiHelper.class);
    when(downloader.downloadFromBatch(serverApiHelper, projectBinding.projectKey() + ":src/main/Foo.java", null)).thenReturn(issues);

    updater.updateFileIssues(serverApiHelper, projectBinding, "src/main/Foo.java", "branch", true, Version.create("99.9"), PROGRESS);

    verify(issueStore).replaceAllIssuesOfFile(eq(projectBinding.projectKey()), eq("branch"), eq("src/main/Foo.java"), anyList());
  }

  @Test
  void dont_update_file_issues_sonarqube_9_5() {
    var serverApiHelper = mock(ServerApiHelper.class);

    updater.updateFileIssues(serverApiHelper, projectBinding, "src/main/Foo.java", "branch", false, IssueApi.MIN_SQ_VERSION_SUPPORTING_PULL, PROGRESS);

    verify(issueStore, never()).replaceAllIssuesOfFile(any(), eq("branch"), anyString(), anyList());
  }
}
