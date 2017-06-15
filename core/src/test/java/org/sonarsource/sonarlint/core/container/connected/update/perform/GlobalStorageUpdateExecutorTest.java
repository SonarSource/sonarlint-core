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
package org.sonarsource.sonarlint.core.container.connected.update.perform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.utils.TempFolder;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.update.ModuleListDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.PluginListDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.PluginReferencesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.QualityProfilesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.RulesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.SettingsDownloader;
import org.sonarsource.sonarlint.core.container.connected.validate.ServerVersionAndStatusChecker;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;
import org.sonarsource.sonarlint.core.proto.Sonarlint.StorageStatus;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.VersionUtils;

public class GlobalStorageUpdateExecutorTest {
  private TempFolder tempFolder;
  private StoragePaths storageManager;
  private SonarLintWsClient wsClient;
  private GlobalStorageUpdateExecutor globalUpdate;
  private RulesDownloader rulesDownloader;

  private File destDir;
  private File tempDir;

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Before
  public void setUp() throws IOException {
    storageManager = mock(StoragePaths.class);
    tempFolder = mock(TempFolder.class);
    rulesDownloader = mock(RulesDownloader.class);

    wsClient = WsClientTestUtils.createMockWithResponse("api/system/status", "{\"id\": \"20160308094653\",\"version\": \"5.6-SNAPSHOT\",\"status\": \"UP\"}");

    tempDir = temp.newFolder();
    destDir = temp.newFolder();

    when(tempFolder.newDir()).thenReturn(tempDir);
    storageManager = mock(StoragePaths.class);
    when(storageManager.getGlobalStorageRoot()).thenReturn(destDir.toPath());
    globalUpdate = new GlobalStorageUpdateExecutor(storageManager, wsClient, new ServerVersionAndStatusChecker(wsClient),
      mock(PluginReferencesDownloader.class), mock(SettingsDownloader.class), rulesDownloader, mock(ModuleListDownloader.class),
      mock(QualityProfilesDownloader.class), mock(PluginListDownloader.class), tempFolder);
  }

  @Test
  public void testUpdate() throws Exception {
    globalUpdate.update(new ProgressWrapper(null));

    StorageStatus updateStatus = ProtobufUtil.readFile(destDir.toPath().resolve(StoragePaths.STORAGE_STATUS_PB), StorageStatus.parser());
    assertThat(updateStatus.getClientUserAgent()).isEqualTo("UT");
    assertThat(updateStatus.getSonarlintCoreVersion()).isEqualTo(VersionUtils.getLibraryVersion());
    assertThat(updateStatus.getUpdateTimestamp()).isNotEqualTo(0);

    ServerInfos serverInfos = ProtobufUtil.readFile(destDir.toPath().resolve(StoragePaths.SERVER_INFO_PB), ServerInfos.parser());
    assertThat(serverInfos.getId()).isEqualTo("20160308094653");
    assertThat(serverInfos.getVersion()).isEqualTo("5.6-SNAPSHOT");
  }

  @Test
  public void dontCopyOnError() throws IOException {
    Files.createDirectories(destDir.toPath());
    Files.createFile(destDir.toPath().resolve("test"));
    doThrow(IllegalStateException.class).when(rulesDownloader).fetchRulesTo(any(Path.class));
    try {
      globalUpdate.update(new ProgressWrapper(null));
      fail("Expected exception");
    } catch (IllegalStateException e) {
      // dest left untouched
      assertThat(Files.exists(destDir.toPath().resolve("test"))).isTrue();
      // tmp cleaned
      assertThat(Files.exists(tempDir.toPath())).isFalse();
    }

  }
}
