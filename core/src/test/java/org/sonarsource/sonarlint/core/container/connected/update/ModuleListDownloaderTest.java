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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleList;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class ModuleListDownloaderTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void update_modules_before_6_dot_3() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMock();
    WsClientTestUtils.addReaderResponse(wsClient, "api/projects/index?format=json&subprojects=true", "/update/all_projects.json");

    File tempDir = temp.newFolder();

    ModuleListDownloader moduleListUpdate = new ModuleListDownloader(wsClient);
    moduleListUpdate.fetchModulesListTo(tempDir.toPath(), "6.2", new ProgressWrapper(null));

    ModuleList moduleList = ProtobufUtil.readFile(tempDir.toPath().resolve(StoragePaths.MODULE_LIST_PB), ModuleList.parser());
    assertThat(moduleList.getModulesByKeyMap()).hasSize(1559);
    assertThat(moduleList.getModulesByKeyMap().values()).extracting("qu").contains("TRK", "BRC");
  }

  @Test
  public void update_modules_after_6_dot_3() throws Exception {

    SonarLintWsClient wsClient = WsClientTestUtils.createMock();
    WsClientTestUtils.addStreamResponse(wsClient, "api/components/search.protobuf?qualifiers=TRK,BRC&ps=500&p=1", "/update/searchmodulesp1.pb");

    File tempDir = temp.newFolder();

    ModuleListDownloader moduleListUpdate = new ModuleListDownloader(wsClient);
    moduleListUpdate.fetchModulesListTo(tempDir.toPath(), "6.3", new ProgressWrapper(null));

    ModuleList moduleList = ProtobufUtil.readFile(tempDir.toPath().resolve(StoragePaths.MODULE_LIST_PB), ModuleList.parser());
    assertThat(moduleList.getModulesByKeyMap()).hasSize(282);
    assertThat(moduleList.getModulesByKeyMap().values()).extracting("qu").contains("TRK", "BRC");
  }

  @Test
  public void update_modules_after_6_dot_3_with_org() throws Exception {

    SonarLintWsClient wsClient = WsClientTestUtils.createMock();
    when(wsClient.getOrganizationKey()).thenReturn("myOrg");
    WsClientTestUtils.addStreamResponse(wsClient, "api/components/search.protobuf?qualifiers=TRK,BRC&organization=myOrg&ps=500&p=1", "/update/searchmodulesp1.pb");

    File tempDir = temp.newFolder();

    ModuleListDownloader moduleListUpdate = new ModuleListDownloader(wsClient);
    moduleListUpdate.fetchModulesListTo(tempDir.toPath(), "6.3", new ProgressWrapper(null));

    ModuleList moduleList = ProtobufUtil.readFile(tempDir.toPath().resolve(StoragePaths.MODULE_LIST_PB), ModuleList.parser());
    assertThat(moduleList.getModulesByKeyMap()).hasSize(282);
    assertThat(moduleList.getModulesByKeyMap().values()).extracting("qu").contains("TRK", "BRC");
  }
}
