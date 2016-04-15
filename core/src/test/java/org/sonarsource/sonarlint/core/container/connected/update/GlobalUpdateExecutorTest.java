/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.container.connected.update;

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
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.container.connected.validate.ServerVersionAndStatusChecker;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;
import org.sonarsource.sonarlint.core.proto.Sonarlint.UpdateStatus;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.VersionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GlobalUpdateExecutorTest {
  private TempFolder tempFolder;
  private StorageManager storageManager;
  private SonarLintWsClient wsClient;
  private GlobalUpdateExecutor globalUpdate;
  private RulesDownloader rulesDownloader;

  private File destDir;
  private File tempDir;

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Before
  public void setUp() throws IOException {
    storageManager = mock(StorageManager.class);
    tempFolder = mock(TempFolder.class);
    rulesDownloader = mock(RulesDownloader.class);

    wsClient = WsClientTestUtils.createMockWithResponse("api/system/status", "{\"id\": \"20160308094653\",\"version\": \"5.5-SNAPSHOT\",\"status\": \"UP\"}");

    tempDir = temp.newFolder();
    destDir = temp.newFolder();

    when(tempFolder.newDir()).thenReturn(tempDir);
    storageManager = mock(StorageManager.class);
    when(storageManager.getGlobalStorageRoot()).thenReturn(destDir.toPath());
    globalUpdate = new GlobalUpdateExecutor(storageManager, wsClient, mock(PluginVersionChecker.class), new ServerVersionAndStatusChecker(wsClient),
      mock(PluginReferencesDownloader.class), mock(GlobalPropertiesDownloader.class), rulesDownloader, mock(ModuleListDownloader.class),
      mock(QualityProfilesDownloader.class), tempFolder);
  }

  @Test
  public void testUpdate() throws Exception {
    globalUpdate.update(new ProgressWrapper(null));

    UpdateStatus updateStatus = ProtobufUtil.readFile(destDir.toPath().resolve(StorageManager.UPDATE_STATUS_PB), UpdateStatus.parser());
    assertThat(updateStatus.getClientUserAgent()).isEqualTo("UT");
    assertThat(updateStatus.getSonarlintCoreVersion()).isEqualTo(VersionUtils.getLibraryVersion());
    assertThat(updateStatus.getUpdateTimestamp()).isNotEqualTo(0);

    ServerInfos serverInfos = ProtobufUtil.readFile(destDir.toPath().resolve(StorageManager.SERVER_INFO_PB), ServerInfos.parser());
    assertThat(serverInfos.getId()).isEqualTo("20160308094653");
    assertThat(serverInfos.getVersion()).isEqualTo("5.5-SNAPSHOT");
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
