/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2022 SonarSource SA
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.system.ServerInfo;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerInfoStore;
import org.sonarsource.sonarlint.core.serverconnection.storage.StorageFolder;
import testutils.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueFixtures.aServerIssue;

class ProjectStorageUpdateExecutorTests {

  private static final ProgressMonitor PROGRESS = new ProgressMonitor(null);
  private static final String ORGA_KEY = "myOrga";
  private static final String MODULE_KEY_WITH_BRANCH = "module:key/with_branch";
  private static final String MODULE_KEY_WITH_BRANCH_URLENCODED = URLEncoder.encode(MODULE_KEY_WITH_BRANCH, StandardCharsets.UTF_8);

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  private ProjectStorageUpdateExecutor underTest;
  private final ProjectStoragePaths projectStoragePaths = mock(ProjectStoragePaths.class);
  private final ServerIssueUpdater serverIssueUpdater = mock(ServerIssueUpdater.class);
  private final ProjectFileListDownloader projectFileListDownloader = mock(ProjectFileListDownloader.class);

  public void setUp(Path tempDir) throws IOException {
    Files.createDirectory(tempDir);

    var serverInfoStore = new ServerInfoStore(new StorageFolder.Default(tempDir));
    serverInfoStore.store(new ServerInfo("", "", ""));

    underTest = new ProjectStorageUpdateExecutor(projectStoragePaths, projectFileListDownloader, serverIssueUpdater);
  }

  @ParameterizedTest(name = "organizationKey=[{0}]")
  @NullSource
  @ValueSource(strings = {ORGA_KEY})
  void test_server_issues_are_downloaded_and_stored(@Nullable String organizationKey, @TempDir Path tempDir) throws IOException {
    setUp(tempDir.resolve("tmp"));

    var storageDir = tempDir.resolve("destDir");
    var globalStoragePath = storageDir.resolve("global");
    FileUtils.mkdirs(globalStoragePath);

    mockServer.addStringResponse(getQualityProfileUrl(organizationKey), "");

    when(projectStoragePaths.getProjectStorageRoot(MODULE_KEY_WITH_BRANCH)).thenReturn(storageDir);

    var fileIssue1 = aServerIssue()
      .setFilePath("some/path")
      .setRuleKey("squid:x");
    var fileIssue2 = aServerIssue()
      .setFilePath("some/path")
      .setRuleKey("squid:y");
    var anotherFileIssue = aServerIssue()
      .setFilePath("another/path");

    var issueDownloader = mock(IssueDownloader.class);
    var serverApiHelper = mock(ServerApiHelper.class);
    when(issueDownloader.download(eq(serverApiHelper), eq(MODULE_KEY_WITH_BRANCH), eq(false), eq(null), any(ProgressMonitor.class)))
      .thenReturn(Arrays.asList(fileIssue1, fileIssue2, anotherFileIssue));

    underTest = new ProjectStorageUpdateExecutor(projectStoragePaths, projectFileListDownloader, serverIssueUpdater);
    underTest.update(serverApiHelper, MODULE_KEY_WITH_BRANCH, false, null, PROGRESS);

    verify(serverIssueUpdater).updateServerIssues(eq(serverApiHelper), eq(MODULE_KEY_WITH_BRANCH), any(Path.class), eq(false),
      eq(null), any(ProgressMonitor.class));
  }

  @Test
  void test_update_components(@TempDir Path tempDir) throws IOException {
    setUp(tempDir.resolve("tmp"));

    var temp = tempDir.resolve("tmp2");
    Files.createDirectories(temp);
    underTest = new ProjectStorageUpdateExecutor(projectStoragePaths, projectFileListDownloader, serverIssueUpdater);

    List<String> fileList = new ArrayList<>();
    fileList.add("rootModule:pom.xml");
    fileList.add("rootModule:A/a.java");
    fileList.add("rootModule:B/b.java");

    var serverApiHelper = mock(ServerApiHelper.class);
    when(projectFileListDownloader.get(eq(serverApiHelper), eq("rootModule"), any(ProgressMonitor.class))).thenReturn(fileList);
    underTest.updateComponents(serverApiHelper, "rootModule", temp, mock(ProgressMonitor.class));

    var components = ProtobufUtil.readFile(temp.resolve(ProjectStoragePaths.COMPONENT_LIST_PB), Sonarlint.ProjectComponents.parser());
    assertThat(components.getComponentList()).containsOnly(
      "pom.xml", "A/a.java", "B/b.java");
  }

  private String getQualityProfileUrl(@Nullable String organizationKey) {
    var url = "/api/qualityprofiles/search.protobuf?project=" + MODULE_KEY_WITH_BRANCH_URLENCODED;
    if (organizationKey != null) {
      url += "&organization=" + organizationKey;
    }
    return url;
  }

}
