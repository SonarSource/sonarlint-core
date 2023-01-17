/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2023 SonarSource SA
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
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufUtil;
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
  private final ProjectStoragePaths projectStoragePaths = mock(ProjectStoragePaths.class);
  private final ProjectFileListDownloader projectFileListDownloader = mock(ProjectFileListDownloader.class);

  @Test
  void test_update_components(@TempDir Path tempDir) throws IOException {
    Files.createDirectory(tempDir.resolve("tmp"));

    underTest = new ProjectStorageUpdateExecutor(projectStoragePaths, projectFileListDownloader);

    List<String> fileList = new ArrayList<>();
    fileList.add("rootModule:pom.xml");
    fileList.add("rootModule:A/a.java");
    fileList.add("rootModule:B/b.java");

    var serverApi = new ServerApi(mock(ServerApiHelper.class));
    when(projectFileListDownloader.get(eq(serverApi), eq("rootModule"), any(ProgressMonitor.class))).thenReturn(fileList);
    underTest.updateComponents(serverApi, "rootModule", tempDir, mock(ProgressMonitor.class));

    var components = ProtobufUtil.readFile(tempDir.resolve(ProjectStoragePaths.COMPONENT_LIST_PB), Sonarlint.ProjectComponents.parser());
    assertThat(components.getComponentList()).containsOnly(
      "pom.xml", "A/a.java", "B/b.java");
  }

  @Test
  void test_update(@TempDir Path storagePath) throws IOException {
    when(projectStoragePaths.getProjectStorageRoot("rootModule")).thenReturn(storagePath);
    underTest = new ProjectStorageUpdateExecutor(projectStoragePaths, projectFileListDownloader);

    List<String> fileList = new ArrayList<>();
    fileList.add("rootModule:pom.xml");
    fileList.add("rootModule:A/a.java");
    fileList.add("rootModule:B/b.java");

    var serverApi = new ServerApi(mock(ServerApiHelper.class));
    when(projectFileListDownloader.get(eq(serverApi), eq("rootModule"), any(ProgressMonitor.class))).thenReturn(fileList);
    underTest.update(serverApi, "rootModule", mock(ProgressMonitor.class));

    var components = ProtobufUtil.readFile(storagePath.resolve(ProjectStoragePaths.COMPONENT_LIST_PB), Sonarlint.ProjectComponents.parser());
    assertThat(components.getComponentList()).containsOnly(
      "pom.xml", "A/a.java", "B/b.java");
  }

}
