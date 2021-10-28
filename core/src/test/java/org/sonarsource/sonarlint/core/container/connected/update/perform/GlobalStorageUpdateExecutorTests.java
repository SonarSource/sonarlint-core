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
package org.sonarsource.sonarlint.core.container.connected.update.perform;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.utils.TempFolder;
import org.sonarqube.ws.Components;
import org.sonarqube.ws.Qualityprofiles;
import org.sonarqube.ws.Rules;
import org.sonarqube.ws.Settings;
import org.sonarsource.sonarlint.core.MockWebServerExtension;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.container.connected.update.PluginListDownloader;
import org.sonarsource.sonarlint.core.container.connected.validate.ServerVersionAndStatusChecker;
import org.sonarsource.sonarlint.core.container.storage.ActiveRulesStore;
import org.sonarsource.sonarlint.core.container.storage.GlobalSettingsStore;
import org.sonarsource.sonarlint.core.container.storage.PluginReferenceStore;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.QualityProfileStore;
import org.sonarsource.sonarlint.core.container.storage.RulesStore;
import org.sonarsource.sonarlint.core.container.storage.ServerInfoStore;
import org.sonarsource.sonarlint.core.container.storage.ServerStorage;
import org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;
import org.sonarsource.sonarlint.core.proto.Sonarlint.StorageStatus;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.VersionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalStorageUpdateExecutorTests {

  private static final ProgressWrapper PROGRESS = new ProgressWrapper(null);

  @RegisterExtension
  static MockWebServerExtension mockServer = new MockWebServerExtension();

  private GlobalStorageUpdateExecutor globalUpdate;

  private Path destDir;
  private File tempDir;

  @BeforeEach
  public void setUp(@TempDir Path temp) throws IOException {
    TempFolder tempFolder = mock(TempFolder.class);

    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"7.9\",\"status\": \"UP\"}");
    mockServer.addProtobufResponse("/api/settings/values.protobuf", Settings.ValuesWsResponse.newBuilder().build());
    mockServer.addProtobufResponse("/api/components/search.protobuf?qualifiers=TRK&ps=500&p=1", Components.SearchWsResponse.newBuilder().build());
    mockServer.addProtobufResponse("/api/qualityprofiles/search.protobuf", Qualityprofiles.SearchWsResponse.newBuilder().build());
    mockServer.addProtobufResponse("/api/rules/search.protobuf?f=repo,name,severity,lang,htmlDesc,htmlNote,internalKey,isTemplate,templateKey,actives&statuses=BETA,DEPRECATED,READY&types=CODE_SMELL,BUG,VULNERABILITY&severities=INFO&languages=&p=1&ps=500", Rules.SearchResponse.newBuilder().build());
    mockServer.addProtobufResponse("/api/rules/search.protobuf?f=repo,name,severity,lang,htmlDesc,htmlNote,internalKey,isTemplate,templateKey,actives&statuses=BETA,DEPRECATED,READY&types=CODE_SMELL,BUG,VULNERABILITY&severities=MINOR&languages=&p=1&ps=500", Rules.SearchResponse.newBuilder().build());
    mockServer.addProtobufResponse("/api/rules/search.protobuf?f=repo,name,severity,lang,htmlDesc,htmlNote,internalKey,isTemplate,templateKey,actives&statuses=BETA,DEPRECATED,READY&types=CODE_SMELL,BUG,VULNERABILITY&severities=MAJOR&languages=&p=1&ps=500", Rules.SearchResponse.newBuilder().build());
    mockServer.addProtobufResponse("/api/rules/search.protobuf?f=repo,name,severity,lang,htmlDesc,htmlNote,internalKey,isTemplate,templateKey,actives&statuses=BETA,DEPRECATED,READY&types=CODE_SMELL,BUG,VULNERABILITY&severities=CRITICAL&languages=&p=1&ps=500", Rules.SearchResponse.newBuilder().build());
    mockServer.addProtobufResponse("/api/rules/search.protobuf?f=repo,name,severity,lang,htmlDesc,htmlNote,internalKey,isTemplate,templateKey,actives&statuses=BETA,DEPRECATED,READY&types=CODE_SMELL,BUG,VULNERABILITY&severities=BLOCKER&languages=&p=1&ps=500", Rules.SearchResponse.newBuilder().build());

    tempDir = temp.resolve("tmp").toFile();
    tempDir.mkdir();
    destDir = temp.resolve("storage/6964/global");

    when(tempFolder.newDir()).thenReturn(tempDir);
    GlobalSettingsStore currentGlobalSettingsStore = mock(GlobalSettingsStore.class);
    when(currentGlobalSettingsStore.getAllOrEmpty()).thenReturn(Sonarlint.GlobalProperties.newBuilder().build());
    PluginReferenceStore currentPluginReferenceStore = mock(PluginReferenceStore.class);
    when(currentPluginReferenceStore.getAllOrEmpty()).thenReturn(Sonarlint.PluginReferences.newBuilder().build());
    ActiveRulesStore currentActiveRulesStore = mock(ActiveRulesStore.class);
    when(currentActiveRulesStore.getActiveRules(any())).thenReturn(Sonarlint.ActiveRules.newBuilder().build());
    RulesStore currentRulesStore = mock(RulesStore.class);
    when(currentRulesStore.getAllOrEmpty()).thenReturn(Sonarlint.Rules.newBuilder().build());
    QualityProfileStore currentQualityProfileStore = mock(QualityProfileStore.class);
    when(currentQualityProfileStore.getAllOrEmpty()).thenReturn(Sonarlint.QProfiles.newBuilder().build());
    globalUpdate = new GlobalStorageUpdateExecutor(new ServerStorage(destDir), mockServer.serverApiHelper(), new ServerVersionAndStatusChecker(mockServer.serverApiHelper()),
      mock(PluginCache.class), mock(PluginListDownloader.class), mock(ConnectedGlobalConfiguration.class), tempFolder, currentGlobalSettingsStore, currentPluginReferenceStore, currentActiveRulesStore, currentRulesStore, currentQualityProfileStore);
  }

  @Test
  void testUpdate() {
    globalUpdate.update(PROGRESS);

    StorageStatus updateStatus = ProtobufUtil.readFile(destDir.resolve(ProjectStoragePaths.STORAGE_STATUS_PB), StorageStatus.parser());
    assertThat(updateStatus.getSonarlintCoreVersion()).isEqualTo(VersionUtils.getLibraryVersion());
    assertThat(updateStatus.getUpdateTimestamp()).isNotZero();

    ServerInfos serverInfos = ProtobufUtil.readFile(destDir.resolve(ServerInfoStore.SERVER_INFO_PB), ServerInfos.parser());
    assertThat(serverInfos.getId()).isEqualTo("20160308094653");
    assertThat(serverInfos.getVersion()).isEqualTo("7.9");
  }

  @Test
  void dontCopyOnError() throws IOException {
    Files.createDirectories(destDir);
    Files.createFile(destDir.resolve("test"));
    ProgressWrapper mockProgress = mock(ProgressWrapper.class);
    when(mockProgress.subProgress(anyFloat(), anyFloat(), anyString())).thenReturn(mockProgress);
    doThrow(new IllegalStateException("Boom")).when(mockProgress).executeNonCancelableSection(any());
    Throwable throwable = catchThrowable(() -> globalUpdate.update(mockProgress));

    assertThat(throwable).isInstanceOf(IllegalStateException.class);
    // dest left untouched
    assertThat(Files.exists(destDir.resolve("test"))).isTrue();
    // tmp cleaned
    assertThat(Files.exists(tempDir.toPath())).isFalse();

  }
}
