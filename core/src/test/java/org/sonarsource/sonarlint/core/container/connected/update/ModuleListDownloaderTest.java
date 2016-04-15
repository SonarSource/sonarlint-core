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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleList;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

public class ModuleListDownloaderTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void update_modules() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithReaderResponse("api/projects/index?format=json&subprojects=true",
      new InputStreamReader(requireNonNull(WsClientTestUtils.class.getResourceAsStream("/update/all_projects.json")), StandardCharsets.UTF_8));

    File tempDir = temp.newFolder();

    ModuleListDownloader moduleListUpdate = new ModuleListDownloader(wsClient);
    moduleListUpdate.fetchModulesList(tempDir.toPath());

    ModuleList moduleList = ProtobufUtil.readFile(tempDir.toPath().resolve(StorageManager.MODULE_LIST_PB), ModuleList.parser());
    assertThat(moduleList.getModulesByKey()).hasSize(1559);
    assertThat(moduleList.getModulesByKey().values()).extracting("qu").contains("TRK", "BRC");
  }
}
