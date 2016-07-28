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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;

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
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleList;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;
import org.sonarsource.sonarlint.core.proto.Sonarlint.UpdateStatus;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleList.Module;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.VersionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class GlobalUpdateExecutorTest {
  private TempFolder tempFolder;
  private StorageManager storageManager;
  private SonarLintWsClient wsClient;
  private GlobalUpdateExecutor globalUpdate;
  private RulesDownloader rulesDownloader;
  private ModuleConfigUpdateExecutor moduleUpdate;

  private Path destDir;
  private Path tempDir;

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Before
  public void setUp() throws IOException {
    storageManager = mock(StorageManager.class);
    tempFolder = mock(TempFolder.class);
    rulesDownloader = mock(RulesDownloader.class);
    moduleUpdate = mock(ModuleConfigUpdateExecutor.class);

    wsClient = WsClientTestUtils.createMockWithResponse("api/system/status", "{\"id\": \"20160308094653\",\"version\": \"5.5-SNAPSHOT\",\"status\": \"UP\"}");

    tempDir = temp.newFolder().toPath();
    destDir = temp.newFolder().toPath();

    when(tempFolder.newDir()).thenReturn(tempDir.toFile());
    storageManager = mock(StorageManager.class);
    when(storageManager.getGlobalStorageRoot()).thenReturn(destDir);
    when(storageManager.readModuleListFromStorage()).thenReturn(ModuleList.newBuilder().build());
    globalUpdate = new GlobalUpdateExecutor(storageManager, wsClient, mock(PluginVersionChecker.class), new ServerVersionAndStatusChecker(wsClient),
      mock(PluginReferencesDownloader.class), mock(GlobalPropertiesDownloader.class), rulesDownloader, mock(ModuleListDownloader.class),
      mock(QualityProfilesDownloader.class), moduleUpdate, tempFolder);
  }

  @Test
  public void testUpdate() throws Exception {
    globalUpdate.update(new ProgressWrapper(null));

    UpdateStatus updateStatus = ProtobufUtil.readFile(destDir.resolve(StorageManager.UPDATE_STATUS_PB), UpdateStatus.parser());
    assertThat(updateStatus.getClientUserAgent()).isEqualTo("UT");
    assertThat(updateStatus.getSonarlintCoreVersion()).isEqualTo(VersionUtils.getLibraryVersion());
    assertThat(updateStatus.getUpdateTimestamp()).isNotEqualTo(0);

    ServerInfos serverInfos = ProtobufUtil.readFile(destDir.resolve(StorageManager.SERVER_INFO_PB), ServerInfos.parser());
    assertThat(serverInfos.getId()).isEqualTo("20160308094653");
    assertThat(serverInfos.getVersion()).isEqualTo("5.5-SNAPSHOT");
  }

  @Test
  public void testUpdateModules() throws IOException {
    ModuleList.Builder serverModuleListBuilder = ModuleList.newBuilder();
    serverModuleListBuilder.getMutableModulesByKey().put("module1", Module.newBuilder().setKey("module1").build());

    Path modules = destDir.resolve("modules");
    // to be updated
    Path module1 = modules.resolve("module1");
    // to be deleted
    Path module3 = modules.resolve("module3");

    Files.createDirectories(module1);
    Files.createDirectories(module3);

    String[] modulesInStorage = {"module1", "module3"};

    when(storageManager.getModuleStorageRoot("module3")).thenReturn(module3);
    when(storageManager.getModuleKeysInStorage()).thenReturn(new HashSet<>(Arrays.asList(modulesInStorage)));
    when(storageManager.readModuleListFromStorage()).thenReturn(serverModuleListBuilder.build());
    globalUpdate.update(new ProgressWrapper(null));

    verify(moduleUpdate).update("module1");
    verifyNoMoreInteractions(moduleUpdate);
    assertThat(Files.exists(module3)).isFalse();
  }

  @Test
  public void dontCopyOnError() throws IOException {
    Files.createDirectories(destDir);
    Files.createFile(destDir.resolve("test"));
    doThrow(IllegalStateException.class).when(rulesDownloader).fetchRulesTo(any(Path.class), any(String.class));
    try {
      globalUpdate.update(new ProgressWrapper(null));
      fail("Expected exception");
    } catch (IllegalStateException e) {
      // dest left untouched
      assertThat(Files.exists(destDir.resolve("test"))).isTrue();
      // tmp cleaned
      assertThat(Files.exists(tempDir)).isFalse();
    }

  }
}
