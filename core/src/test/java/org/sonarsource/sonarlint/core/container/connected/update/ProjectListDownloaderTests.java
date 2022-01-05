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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.MockWebServerExtensionWithProtobuf;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.ServerProjectsStore;
import org.sonarsource.sonarlint.core.container.storage.StorageFolder;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectList;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectListDownloaderTests {

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  @Test
  void update_modules(@TempDir Path tempDir) {
    var serverProjectsStore = new ServerProjectsStore(new StorageFolder.Default(tempDir));
    mockServer.addResponseFromResource("/api/components/search.protobuf?qualifiers=TRK&ps=500&p=1", "/update/searchmodulesp1.pb");
    var moduleListUpdate = new ProjectListDownloader(mockServer.serverApiHelper(), serverProjectsStore);

    moduleListUpdate.fetch(new ProgressMonitor(null));

    var moduleList = ProtobufUtil.readFile(tempDir.resolve(ServerProjectsStore.PROJECT_LIST_PB), ProjectList.parser());
    assertThat(moduleList.getProjectsByKeyMap()).hasSize(282);
  }

  @Test
  void update_modules_with_org(@TempDir Path tempDir) {
    var serverProjectsStore = new ServerProjectsStore(new StorageFolder.Default(tempDir));
    mockServer.addResponseFromResource("/api/components/search.protobuf?qualifiers=TRK&organization=myOrg&ps=500&p=1", "/update/searchmodulesp1.pb");
    var moduleListUpdate = new ProjectListDownloader(mockServer.serverApiHelper("myOrg"), serverProjectsStore);

    moduleListUpdate.fetch(new ProgressMonitor(null));

    var moduleList = ProtobufUtil.readFile(tempDir.resolve(ServerProjectsStore.PROJECT_LIST_PB), ProjectList.parser());
    assertThat(moduleList.getProjectsByKeyMap()).hasSize(282);
  }
}
