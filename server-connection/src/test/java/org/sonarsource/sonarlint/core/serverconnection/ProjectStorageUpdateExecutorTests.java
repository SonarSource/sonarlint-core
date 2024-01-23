/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2024 SonarSource SA
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;
import testutils.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectStorageUpdateExecutorTests {

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  private ProjectStorageUpdateExecutor underTest;
  private final ProjectFileListDownloader projectFileListDownloader = mock(ProjectFileListDownloader.class);

  @Test
  void test_update_components(@TempDir Path tempDir) throws IOException {
    Files.createDirectory(tempDir.resolve("tmp"));

    var storage = new ConnectionStorage(tempDir, tempDir, "connectionId");
    underTest = new ProjectStorageUpdateExecutor(storage, projectFileListDownloader);

    List<String> fileList = new ArrayList<>();
    fileList.add("rootModule:pom.xml");
    fileList.add("rootModule:A/a.java");
    fileList.add("rootModule:B/b.java");

    var serverApi = new ServerApi(mock(ServerApiHelper.class));
    when(projectFileListDownloader.get(eq(serverApi), eq("rootModule"), any(ProgressMonitor.class))).thenReturn(fileList);
    underTest.updateComponents(serverApi, "rootModule", tempDir, mock(ProgressMonitor.class));

    var components = ProtobufFileUtil.readFile(tempDir.resolve(ComponentsStorage.COMPONENT_LIST_PB), Sonarlint.ProjectComponents.parser());
    assertThat(components.getComponentList()).containsOnly(
      "pom.xml", "A/a.java", "B/b.java");
  }

  @Test
  void test_update(@TempDir Path storagePath) {
    var storage = new ConnectionStorage(storagePath, storagePath, "connectionId");
    underTest = new ProjectStorageUpdateExecutor(storage, projectFileListDownloader);

    List<String> fileList = new ArrayList<>();
    fileList.add("rootModule:pom.xml");
    fileList.add("rootModule:A/a.java");
    fileList.add("rootModule:B/b.java");

    var serverApi = new ServerApi(mock(ServerApiHelper.class));
    when(projectFileListDownloader.get(eq(serverApi), eq("rootModule"), any(ProgressMonitor.class))).thenReturn(fileList);
    underTest.update(serverApi, "rootModule", mock(ProgressMonitor.class));

    var components = ProtobufFileUtil.readFile(storagePath.resolve("636f6e6e656374696f6e4964/projects/726f6f744d6f64756c65/" + ComponentsStorage.COMPONENT_LIST_PB), Sonarlint.ProjectComponents.parser());
    assertThat(components.getComponentList()).containsOnly(
      "pom.xml", "A/a.java", "B/b.java");
  }

}
