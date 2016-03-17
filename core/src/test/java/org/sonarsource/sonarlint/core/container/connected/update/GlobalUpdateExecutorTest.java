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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.utils.TempFolder;
import org.sonarsource.sonarlint.core.client.api.connected.UnsupportedServerException;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;
import org.sonarsource.sonarlint.core.proto.Sonarlint.UpdateStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GlobalUpdateExecutorTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void serverNotReady() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithResponse("api/system/status", "{\"id\": \"20160308094653\",\"version\": \"5.5-SNAPSHOT\",\"status\": \"DOWN\"}");

    GlobalUpdateExecutor globalUpdate = new GlobalUpdateExecutor(mock(StorageManager.class), wsClient, mock(PluginReferencesDownloader.class),
      mock(GlobalPropertiesDownloader.class), mock(RulesDownloader.class), mock(ModuleListDownloader.class), mock(TempFolder.class));

    try {
      globalUpdate.update();
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).hasMessage("Server not ready (DOWN)");
    }
  }

  @Test
  public void incompatibleVersion() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithResponse("api/system/status", "{\"id\": \"20160308094653\",\"version\": \"5.1\",\"status\": \"UP\"}");

    GlobalUpdateExecutor globalUpdate = new GlobalUpdateExecutor(mock(StorageManager.class), wsClient, mock(PluginReferencesDownloader.class),
      mock(GlobalPropertiesDownloader.class), mock(RulesDownloader.class), mock(ModuleListDownloader.class), mock(TempFolder.class));

    try {
      globalUpdate.update();
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).isExactlyInstanceOf(UnsupportedServerException.class).hasMessage("SonarQube server has version 5.1. Version should be greater or equal to 5.2");
    }
  }

  @Test
  public void responseParsingError() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithResponse("api/system/status", "bla bla");

    GlobalUpdateExecutor globalUpdate = new GlobalUpdateExecutor(mock(StorageManager.class), wsClient, mock(PluginReferencesDownloader.class),
      mock(GlobalPropertiesDownloader.class), mock(RulesDownloader.class), mock(ModuleListDownloader.class), mock(TempFolder.class));

    try {
      globalUpdate.update();
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).hasMessage("Unable to parse server infos from: bla bla");
    }
  }

  @Test
  public void testUpdate() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithResponse("api/system/status", "{\"id\": \"20160308094653\",\"version\": \"5.5-SNAPSHOT\",\"status\": \"UP\"}");

    File tempDir = temp.newFolder();
    File destDir = temp.newFolder();

    TempFolder tempFolder = mock(TempFolder.class);
    when(tempFolder.newDir()).thenReturn(tempDir);
    StorageManager storageManager = mock(StorageManager.class);
    when(storageManager.getGlobalStorageRoot()).thenReturn(destDir.toPath());
    GlobalUpdateExecutor globalUpdate = new GlobalUpdateExecutor(storageManager, wsClient, mock(PluginReferencesDownloader.class),
      mock(GlobalPropertiesDownloader.class), mock(RulesDownloader.class), mock(ModuleListDownloader.class), tempFolder);

    globalUpdate.update();

    UpdateStatus updateStatus = ProtobufUtil.readFile(destDir.toPath().resolve(StorageManager.UPDATE_STATUS_PB), UpdateStatus.parser());
    assertThat(updateStatus.getClientUserAgent()).isEqualTo("UT");
    assertThat(updateStatus.getSonarlintCoreVersion()).isEqualTo("unknown");
    assertThat(updateStatus.getUpdateTimestamp()).isNotEqualTo(0);

    ServerInfos serverInfos = ProtobufUtil.readFile(destDir.toPath().resolve(StorageManager.SERVER_INFO_PB), ServerInfos.parser());
    assertThat(serverInfos.getId()).isEqualTo("20160308094653");
    assertThat(serverInfos.getVersion()).isEqualTo("5.5-SNAPSHOT");
  }
}
