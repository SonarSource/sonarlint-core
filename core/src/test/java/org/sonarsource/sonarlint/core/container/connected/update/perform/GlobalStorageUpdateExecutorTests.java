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
import org.sonarsource.sonarlint.core.MockWebServerExtension;
import org.sonarsource.sonarlint.core.container.connected.update.PluginListDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.PluginReferencesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectListDownloader;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalStorageUpdateExecutorTests {

  private static final ProgressWrapper PROGRESS = new ProgressWrapper(null);

  @RegisterExtension
  static MockWebServerExtension mockServer = new MockWebServerExtension();

  private TempFolder tempFolder;
  private StoragePaths storageManager;
  private GlobalStorageUpdateExecutor globalUpdate;
  private RulesDownloader rulesDownloader;

  private Path destDir;
  private File tempDir;

  @BeforeEach
  public void setUp(@TempDir Path temp) throws IOException {
    storageManager = mock(StoragePaths.class);
    tempFolder = mock(TempFolder.class);
    rulesDownloader = mock(RulesDownloader.class);

    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"6.7\",\"status\": \"UP\"}");

    tempDir = temp.resolve("tmp").toFile();
    tempDir.mkdir();
    destDir = temp.resolve("storage");

    when(tempFolder.newDir()).thenReturn(tempDir);
    storageManager = mock(StoragePaths.class);
    when(storageManager.getGlobalStorageRoot()).thenReturn(destDir);
    globalUpdate = new GlobalStorageUpdateExecutor(storageManager, new ServerVersionAndStatusChecker(mockServer.serverApiHelper()),
      mock(PluginReferencesDownloader.class), mock(SettingsDownloader.class), rulesDownloader, mock(ProjectListDownloader.class),
      mock(QualityProfilesDownloader.class), mock(PluginListDownloader.class), tempFolder);
  }

  @Test
  void testUpdate() throws Exception {
    globalUpdate.update(PROGRESS);

    StorageStatus updateStatus = ProtobufUtil.readFile(destDir.resolve(StoragePaths.STORAGE_STATUS_PB), StorageStatus.parser());
    assertThat(updateStatus.getSonarlintCoreVersion()).isEqualTo(VersionUtils.getLibraryVersion());
    assertThat(updateStatus.getUpdateTimestamp()).isNotEqualTo(0);

    ServerInfos serverInfos = ProtobufUtil.readFile(destDir.resolve(StoragePaths.SERVER_INFO_PB), ServerInfos.parser());
    assertThat(serverInfos.getId()).isEqualTo("20160308094653");
    assertThat(serverInfos.getVersion()).isEqualTo("6.7");
  }

  @Test
  void dontCopyOnError() throws IOException {
    Files.createDirectories(destDir);
    Files.createFile(destDir.resolve("test"));
    doThrow(IllegalStateException.class).when(rulesDownloader).fetchRulesTo(any(Path.class), any(ProgressWrapper.class));
    assertThrows(IllegalStateException.class, () -> globalUpdate.update(PROGRESS));
    // dest left untouched
    assertThat(Files.exists(destDir.resolve("test"))).isTrue();
    // tmp cleaned
    assertThat(Files.exists(tempDir.toPath())).isFalse();

  }
}
