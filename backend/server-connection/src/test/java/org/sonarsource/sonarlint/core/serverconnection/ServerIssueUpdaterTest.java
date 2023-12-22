/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2023 SonarSource SA
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
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.issue.IssueApi;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectServerIssueStore;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueFixtures.aServerIssue;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueFixtures.aServerTaintIssue;

class ServerIssueUpdaterTest {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private static final String PROJECT_KEY = "module";
  private final IssueDownloader downloader = mock(IssueDownloader.class);
  private final TaintIssueDownloader taintDownloader = mock(TaintIssueDownloader.class);
  private final ProjectServerIssueStore issueStore = mock(ProjectServerIssueStore.class);
  private ProjectBinding projectBinding = new ProjectBinding(PROJECT_KEY, "", "");

  private ServerIssueUpdater updater;
  private ServerApi serverApi;

  @BeforeEach
  void setUp() {
    serverApi = new ServerApi(mock(ServerApiHelper.class));
    ConnectionStorage storage = mock(ConnectionStorage.class);
    var projectStorage = mock(SonarProjectStorage.class);
    when(storage.project(PROJECT_KEY)).thenReturn(projectStorage);
    when(projectStorage.findings()).thenReturn(issueStore);
    updater = new ServerIssueUpdater(storage, downloader, taintDownloader);
  }

  @Test
  void update_project_issues_no_pull() {
    var issue = aServerIssue();
    List<ServerIssue<?>> issues = Collections.singletonList(issue);
    when(downloader.downloadFromBatch(serverApi, "module:file", null)).thenReturn(issues);

    updater.update(serverApi, projectBinding.projectKey(), "branch", false, Version.create("8.9"));

    verify(issueStore).replaceAllIssuesOfBranch(eq("branch"), anyList());
  }

  @Test
  void update_project_issues_sonarcloud() {
    var issue = aServerIssue();
    List<ServerIssue<?>> issues = Collections.singletonList(issue);
    when(downloader.downloadFromBatch(serverApi, "module:file", null)).thenReturn(issues);

    updater.update(serverApi, projectBinding.projectKey(), "branch", true, Version.create("99.9"));

    verify(issueStore).replaceAllIssuesOfBranch(eq("branch"), anyList());
  }

  @Test
  void update_project_issues_with_pull_first_time() {
    var issue = aServerIssue();
    List<ServerIssue<?>> issues = Collections.singletonList(issue);
    var queryTimestamp = Instant.now();
    var lastSync = Optional.<Instant>empty();
    when(issueStore.getLastIssueSyncTimestamp("master")).thenReturn(lastSync);
    when(downloader.downloadFromPull(serverApi, projectBinding.projectKey(), "master", lastSync)).thenReturn(new IssueDownloader.PullResult(queryTimestamp, issues, Set.of()));

    updater.update(serverApi, projectBinding.projectKey(), "master", false, IssueApi.MIN_SQ_VERSION_SUPPORTING_PULL);

    verify(issueStore).mergeIssues(eq("master"), anyList(), anySet(), eq(queryTimestamp), anySet());
  }

  @Test
  void update_project_issues_with_pull_using_last_sync() {
    var issue = aServerIssue();
    List<ServerIssue<?>> issues = Collections.singletonList(issue);
    var queryTimestamp = Instant.now();
    var lastSync = Optional.of(Instant.ofEpochMilli(123456789));
    var lastIssueEnabledLanguages = Set.of(SonarLanguage.C, SonarLanguage.GO);
    when(issueStore.getLastIssueEnabledLanguages("master")).thenReturn(lastIssueEnabledLanguages);
    when(issueStore.getLastIssueSyncTimestamp("master")).thenReturn(lastSync);
    when(downloader.getEnabledLanguages()).thenReturn(Set.of(SonarLanguage.C, SonarLanguage.GO));
    when(downloader.downloadFromPull(serverApi, projectBinding.projectKey(), "master", lastSync)).thenReturn(new IssueDownloader.PullResult(queryTimestamp, issues, Set.of()));

    updater.update(serverApi, projectBinding.projectKey(), "master", false, IssueApi.MIN_SQ_VERSION_SUPPORTING_PULL);

    verify(issueStore).mergeIssues(eq("master"), anyList(), anySet(), eq(queryTimestamp), anySet());
  }

  @Test
  void update_project_issues_with_pull_when_there_were_no_enabled_languages() {
    var issue = aServerIssue();
    List<ServerIssue<?>> issues = Collections.singletonList(issue);
    var queryTimestamp = Instant.now();
    var lastSync = Optional.of(Instant.ofEpochMilli(123456789));
    var lastIssueEnabledLanguages = new HashSet<SonarLanguage>();
    when(issueStore.getLastIssueSyncTimestamp("master")).thenReturn(lastSync);
    when(issueStore.getLastIssueEnabledLanguages("master")).thenReturn(lastIssueEnabledLanguages);
    when(downloader.getEnabledLanguages()).thenReturn(Set.of(SonarLanguage.C));
    when(downloader.downloadFromPull(serverApi, projectBinding.projectKey(), "master", Optional.empty())).thenReturn(new IssueDownloader.PullResult(queryTimestamp, issues, Set.of()));
    updater.update(serverApi, projectBinding.projectKey(), "master", false, IssueApi.MIN_SQ_VERSION_SUPPORTING_PULL);
    verify(downloader).downloadFromPull(serverApi, projectBinding.projectKey(), "master", Optional.empty());
  }

  @Test
  void update_project_issues_with_pull_when_enabled_language_changed() {
    var issue = aServerIssue();
    List<ServerIssue<?>> issues = Collections.singletonList(issue);
    var queryTimestamp = Instant.now();
    var lastSync = Optional.of(Instant.ofEpochMilli(123456789));
    var lastIssueEnabledLanguages = Set.of(SonarLanguage.C, SonarLanguage.GO);
    when(issueStore.getLastIssueSyncTimestamp("master")).thenReturn(lastSync);
    when(issueStore.getLastIssueEnabledLanguages("master")).thenReturn(lastIssueEnabledLanguages);
    when(downloader.getEnabledLanguages()).thenReturn(Set.of(SonarLanguage.C));
    when(downloader.downloadFromPull(serverApi, projectBinding.projectKey(), "master", Optional.empty())).thenReturn(new IssueDownloader.PullResult(queryTimestamp, issues, Set.of()));
    updater.update(serverApi, projectBinding.projectKey(), "master", false, IssueApi.MIN_SQ_VERSION_SUPPORTING_PULL);
    verify(downloader).downloadFromPull(serverApi, projectBinding.projectKey(), "master", Optional.empty());
  }

  @Test
  void update_project_issues_with_pull_when_enabled_language_not_changed() {
    var issue = aServerIssue();
    List<ServerIssue<?>> issues = Collections.singletonList(issue);
    var queryTimestamp = Instant.now();
    var lastSync = Optional.of(Instant.ofEpochMilli(123456789));
    var lastIssueEnabledLanguages = Set.of(SonarLanguage.C, SonarLanguage.GO);
    when(issueStore.getLastIssueSyncTimestamp("master")).thenReturn(lastSync);
    when(issueStore.getLastIssueEnabledLanguages("master")).thenReturn(lastIssueEnabledLanguages);
    when(downloader.getEnabledLanguages()).thenReturn(Set.of(SonarLanguage.C, SonarLanguage.GO));
    when(downloader.downloadFromPull(serverApi, projectBinding.projectKey(), "master", lastSync)).thenReturn(new IssueDownloader.PullResult(queryTimestamp, issues, Set.of()));
    updater.update(serverApi, projectBinding.projectKey(), "master", false, IssueApi.MIN_SQ_VERSION_SUPPORTING_PULL);
    verify(downloader).downloadFromPull(serverApi, projectBinding.projectKey(), "master", lastSync);
  }

  @Test
  void update_project_taints_with_pull_when_there_were_no_enabled_languages() {
    var issue = aServerTaintIssue();
    List<ServerTaintIssue> issues = Collections.singletonList(issue);
    var queryTimestamp = Instant.now();
    var lastSync = Optional.of(Instant.ofEpochMilli(123456789));
    var lastIssueEnabledLanguages = new HashSet<SonarLanguage>();
    when(issueStore.getLastTaintSyncTimestamp("master")).thenReturn(lastSync);
    when(issueStore.getLastTaintEnabledLanguages("master")).thenReturn(lastIssueEnabledLanguages);
    when(taintDownloader.downloadTaintFromPull(serverApi, projectBinding.projectKey(), "master", Optional.empty())).thenReturn(new TaintIssueDownloader.PullTaintResult(queryTimestamp, issues, Set.of()));

    updater.syncTaints(serverApi, projectBinding.projectKey(), "master", Set.of(SonarLanguage.C));
    verify(taintDownloader).downloadTaintFromPull(serverApi, projectBinding.projectKey(), "master", Optional.empty());
  }

  @Test
  void update_project_taints_with_pull_when_enabled_language_changed() {
    var issue = aServerTaintIssue();
    List<ServerTaintIssue> issues = Collections.singletonList(issue);
    var queryTimestamp = Instant.now();
    var lastSync = Optional.of(Instant.ofEpochMilli(123456789));
    var lastIssueEnabledLanguages = Set.of(SonarLanguage.C, SonarLanguage.GO);
    when(issueStore.getLastTaintSyncTimestamp("master")).thenReturn(lastSync);
    when(issueStore.getLastTaintEnabledLanguages("master")).thenReturn(lastIssueEnabledLanguages);
    when(taintDownloader.downloadTaintFromPull(serverApi, projectBinding.projectKey(), "master", Optional.empty())).thenReturn(new TaintIssueDownloader.PullTaintResult(queryTimestamp, issues, Set.of()));

    updater.syncTaints(serverApi, projectBinding.projectKey(), "master", Set.of(SonarLanguage.C));
    verify(taintDownloader).downloadTaintFromPull(serverApi, projectBinding.projectKey(), "master", Optional.empty());
  }

  @Test
  void update_project_taints_with_pull_when_enabled_language_not_changed() {
    var issue = aServerTaintIssue();
    List<ServerTaintIssue> issues = Collections.singletonList(issue);
    var queryTimestamp = Instant.now();
    var lastSync = Optional.of(Instant.ofEpochMilli(123456789));
    var lastIssueEnabledLanguages = Set.of(SonarLanguage.C, SonarLanguage.GO);
    when(issueStore.getLastTaintSyncTimestamp("master")).thenReturn(lastSync);
    when(issueStore.getLastTaintEnabledLanguages("master")).thenReturn(lastIssueEnabledLanguages);
    when(taintDownloader.downloadTaintFromPull(serverApi, projectBinding.projectKey(), "master", lastSync)).thenReturn(new TaintIssueDownloader.PullTaintResult(queryTimestamp, issues, Set.of()));

    updater.syncTaints(serverApi, projectBinding.projectKey(), "master", Set.of(SonarLanguage.C, SonarLanguage.GO));
    verify(taintDownloader).downloadTaintFromPull(serverApi, projectBinding.projectKey(), "master", lastSync);
  }

  @Test
  void update_file_issues_for_unknown_file() {
    projectBinding = new ProjectBinding(PROJECT_KEY, "", "ide_prefix");

    updater.updateFileIssues(serverApi, projectBinding, Path.of("not_ide_prefix"), null, false, Version.create("8.9"));

    verifyNoInteractions(downloader);
    verifyNoInteractions(issueStore);
  }

  @Test
  void error_downloading_file_issues() {
    when(downloader.downloadFromBatch(serverApi, "module:file", null)).thenThrow(IllegalArgumentException.class);
    // when(issueStorePaths.idePathToFileKey(projectBinding, "file")).thenReturn("module:file");
    var filePath = Path.of("file");
    var version = Version.create("8.9");
    assertThrows(DownloadException.class, () -> updater.updateFileIssues(serverApi, projectBinding, filePath, null, false, version));
  }

  @Test
  void update_file_issues_sq_no_pull() {
    var issue = aServerIssue();
    List<ServerIssue<?>> issues = Collections.singletonList(issue);

    when(downloader.downloadFromBatch(serverApi, projectBinding.projectKey() + ":src/main/Foo.java", null)).thenReturn(issues);

    updater.updateFileIssues(serverApi, projectBinding, Path.of("src/main/Foo.java"), "branch", false, Version.create("8.9"));

    verify(issueStore).replaceAllIssuesOfFile(eq("branch"), eq(Path.of("src/main/Foo.java")), anyList());
  }

  @Test
  void update_file_issues_sonarcloud() {
    var issue = aServerIssue();
    List<ServerIssue<?>> issues = Collections.singletonList(issue);

    when(downloader.downloadFromBatch(serverApi, projectBinding.projectKey() + ":src/main/Foo.java", null)).thenReturn(issues);

    updater.updateFileIssues(serverApi, projectBinding, Path.of("src/main/Foo.java"), "branch", true, Version.create("99.9"));

    verify(issueStore).replaceAllIssuesOfFile(eq("branch"), eq(Path.of("src/main/Foo.java")), anyList());
  }

  @Test
  void dont_update_file_issues_with_pull() {
    updater.updateFileIssues(serverApi, projectBinding, Path.of("src/main/Foo.java"), "branch", false, IssueApi.MIN_SQ_VERSION_SUPPORTING_PULL);

    verify(issueStore, never()).replaceAllIssuesOfFile(eq("branch"), any(), anyList());
  }
}
