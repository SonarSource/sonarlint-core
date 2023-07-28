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

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.serverapi.hotspot.HotspotApi;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectServerIssueStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ServerHotspotFixtures.aServerHotspot;

class ServerHotspotUpdaterTest {
  @RegisterExtension
  public SonarLintLogTester logTester = new SonarLintLogTester();

  private static final String PROJECT_KEY = "module";
  private final ProjectServerIssueStore issueStore = mock(ProjectServerIssueStore.class);
  private final ProjectBinding projectBinding = new ProjectBinding(PROJECT_KEY, "", "");

  private ServerHotspotUpdater updater;
  private HotspotApi hotspotApi;
  private HotspotDownloader hotspotDownloader;

  @BeforeEach
  void setUp() {
    hotspotApi = mock(HotspotApi.class);
    hotspotDownloader = mock(HotspotDownloader.class);
    ConnectionStorage storage = mock(ConnectionStorage.class);
    var projectStorage = mock(SonarProjectStorage.class);
    when(storage.project(PROJECT_KEY)).thenReturn(projectStorage);
    when(projectStorage.findings()).thenReturn(issueStore);
    updater = new ServerHotspotUpdater(storage, hotspotDownloader);
  }

  @Test
  void should_not_update_all_hotspots_when_not_supported() {
    when(hotspotApi.permitsTracking(any())).thenReturn(false);

    updater.updateAll(hotspotApi, PROJECT_KEY, "branch", () -> null, null);

    verifyNoInteractions(issueStore);
    assertThat(logTester.logs(ClientLogOutput.Level.INFO)).contains("Skip downloading hotspots from server, not supported");
  }

  @Test
  void should_not_update_file_hotspots_when_sync_is_enabled() {
    when(hotspotApi.supportHotspotsPull(any())).thenReturn(true);

    updater.updateForFile(hotspotApi, projectBinding, "filePath", "branch", () -> null);

    verifyNoInteractions(issueStore);
    assertThat(logTester.logs(ClientLogOutput.Level.DEBUG)).contains("Skip downloading file hotspots on SonarQube 10.1+");
  }

  @Test
  void should_not_update_file_hotspots_when_not_supported() {
    when(hotspotApi.permitsTracking(any())).thenReturn(false);

    updater.updateForFile(hotspotApi, projectBinding, "filePath", "branch", () -> null);

    verifyNoInteractions(issueStore);
    assertThat(logTester.logs(ClientLogOutput.Level.INFO)).contains("Skip downloading hotspots for file, not supported");
  }

  @Test
  void should_not_update_file_hotspots_when_file_path_is_inconsistent() {
    when(hotspotApi.permitsTracking(any())).thenReturn(false);

    updater.updateForFile(hotspotApi, new ProjectBinding(PROJECT_KEY, "", "client"), "ide/filePath", "branch", () -> null);

    verifyNoInteractions(issueStore);
  }

  @Test
  void should_update_all_hotspots() {
    when(hotspotApi.permitsTracking(any())).thenReturn(true);
    var hotspots = List.of(aServerHotspot());
    when(hotspotApi.getAll(eq(PROJECT_KEY), eq("branch"), any())).thenReturn(hotspots);

    updater.updateAll(hotspotApi, PROJECT_KEY, "branch", () -> null, null);

    verify(issueStore).replaceAllHotspotsOfBranch("branch", hotspots);
  }

  @Test
  void should_update_file_hotspots() {
    when(hotspotApi.permitsTracking(any())).thenReturn(true);
    var hotspots = List.of(aServerHotspot("key", "filePath"));
    when(hotspotApi.getFromFile(PROJECT_KEY, "filePath", "branch")).thenReturn(hotspots);

    updater.updateForFile(hotspotApi, projectBinding, "filePath", "branch", () -> null);

    verify(issueStore).replaceAllHotspotsOfFile("branch", "filePath", hotspots);
  }

  @Test
  void should_sync_hotspots() {
    var timestamp = Instant.ofEpochMilli(123456789L);
    var hotspotKey = "hotspotKey";
    var hotspots = List.of(aServerHotspot(hotspotKey));
    when(hotspotDownloader.downloadFromPull(hotspotApi, PROJECT_KEY, "branch", Optional.empty()))
      .thenReturn(new HotspotDownloader.PullResult(timestamp, hotspots, Set.of()));

    updater.sync(hotspotApi, PROJECT_KEY, "branch", Set.of(Language.C));

    var hotspotCaptor = ArgumentCaptor.forClass(List.class);
    verify(issueStore).mergeHotspots(eq("branch"), hotspotCaptor.capture(), eq(Set.of()), eq(timestamp), eq(Set.of(Language.C)));
    assertThat(hotspotCaptor.getValue()).hasSize(1);
    var capturedHotspot = (ServerHotspot) (hotspotCaptor.getValue().get(0));
    assertThat(capturedHotspot.getKey()).isEqualTo(hotspotKey);
  }

  @Test
  void update_hotspots_with_pull_when_enabled_language_not_changed() {
    var timestamp = Instant.ofEpochMilli(123456789L);
    var lastHotspotEnabledLanguages = Set.of(Language.C, Language.GO);
    var hotspotKey = "hotspotKey";
    var hotspots = List.of(aServerHotspot(hotspotKey));
    when(hotspotDownloader.downloadFromPull(hotspotApi, PROJECT_KEY, "branch", Optional.of(timestamp)))
      .thenReturn(new HotspotDownloader.PullResult(timestamp, hotspots, Set.of()));
    when(issueStore.getLastHotspotEnabledLanguages("branch")).thenReturn(lastHotspotEnabledLanguages);
    when(issueStore.getLastHotspotSyncTimestamp("branch")).thenReturn(Optional.of(timestamp));

    updater.sync(hotspotApi, PROJECT_KEY, "branch", Set.of(Language.C, Language.GO));

    var hotspotCaptor = ArgumentCaptor.forClass(List.class);
    verify(issueStore).mergeHotspots(eq("branch"), hotspotCaptor.capture(), eq(Set.of()), eq(timestamp), anySet());
    assertThat(hotspotCaptor.getValue()).hasSize(1);
    var capturedHotspot = (ServerHotspot) (hotspotCaptor.getValue().get(0));
    assertThat(capturedHotspot.getKey()).isEqualTo(hotspotKey);
    verify(hotspotDownloader).downloadFromPull(eq(hotspotApi), eq(projectBinding.projectKey()), eq("branch"), eq(Optional.of(timestamp)));
  }

  @Test
  void update_hotspots_with_pull_when_enabled_language_changed() {
    var timestamp = Instant.ofEpochMilli(123456789L);
    var lastHotspotEnabledLanguages = Set.of(Language.C);
    var hotspotKey = "hotspotKey";
    var hotspots = List.of(aServerHotspot(hotspotKey));
    when(hotspotDownloader.downloadFromPull(hotspotApi, PROJECT_KEY, "branch", Optional.empty()))
      .thenReturn(new HotspotDownloader.PullResult(timestamp, hotspots, Set.of()));
    when(issueStore.getLastHotspotEnabledLanguages("branch")).thenReturn(lastHotspotEnabledLanguages);
    when(issueStore.getLastHotspotSyncTimestamp("branch")).thenReturn(Optional.of(timestamp));

    updater.sync(hotspotApi, PROJECT_KEY, "branch", Set.of(Language.C, Language.GO));

    var hotspotCaptor = ArgumentCaptor.forClass(List.class);
    verify(issueStore).mergeHotspots(eq("branch"), hotspotCaptor.capture(), eq(Set.of()), eq(timestamp), anySet());
    assertThat(hotspotCaptor.getValue()).hasSize(1);
    var capturedHotspot = (ServerHotspot) (hotspotCaptor.getValue().get(0));
    assertThat(capturedHotspot.getKey()).isEqualTo(hotspotKey);
    verify(hotspotDownloader).downloadFromPull(eq(hotspotApi), eq(projectBinding.projectKey()), eq("branch"), eq(Optional.empty()));
  }

  @Test
  void update_hotspots_with_pull_when_last_enabled_language_were_not_there() {
    var timestamp = Instant.ofEpochMilli(123456789L);
    var lastHotspotEnabledLanguages = new HashSet<Language>();
    var hotspotKey = "hotspotKey";
    var hotspots = List.of(aServerHotspot(hotspotKey));
    when(hotspotDownloader.downloadFromPull(hotspotApi, PROJECT_KEY, "branch", Optional.empty()))
      .thenReturn(new HotspotDownloader.PullResult(timestamp, hotspots, Set.of()));
    when(issueStore.getLastHotspotEnabledLanguages("branch")).thenReturn(lastHotspotEnabledLanguages);
    when(issueStore.getLastHotspotSyncTimestamp("branch")).thenReturn(Optional.of(timestamp));

    updater.sync(hotspotApi, PROJECT_KEY, "branch", Set.of(Language.C, Language.GO));

    var hotspotCaptor = ArgumentCaptor.forClass(List.class);
    verify(issueStore).mergeHotspots(eq("branch"), hotspotCaptor.capture(), eq(Set.of()), eq(timestamp), anySet());
    assertThat(hotspotCaptor.getValue()).hasSize(1);
    var capturedHotspot = (ServerHotspot) (hotspotCaptor.getValue().get(0));
    assertThat(capturedHotspot.getKey()).isEqualTo(hotspotKey);
    verify(hotspotDownloader).downloadFromPull(eq(hotspotApi), eq(projectBinding.projectKey()), eq("branch"), eq(Optional.empty()));
  }
}
