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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.sonar.api.utils.TempFolder;
import org.sonarqube.ws.Settings.Setting;
import org.sonarqube.ws.Settings.ValuesWsResponse;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.container.connected.InMemoryIssueStore;
import org.sonarsource.sonarlint.core.container.connected.IssueStore;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ModuleHierarchyDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectConfigurationDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectFileListDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectQualityProfilesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.SettingsDownloader;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectConfiguration;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.StringUtils;
import org.sonarsource.sonarlint.core.util.ws.WsResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.container.storage.ProtobufUtilTest.newEmptyStream;

@RunWith(Parameterized.class)
public class ProjectStorageUpdateExecutorTest {

  private static final String ORGA_KEY = "myOrga";
  private static final String MODULE_KEY_WITH_BRANCH = "module:key/with_branch";
  private static final String MODULE_KEY_WITH_BRANCH_URLENCODED = StringUtils.urlEncode(MODULE_KEY_WITH_BRANCH);

  @Parameters(name = "organizationKey=[{0}]")
  public static Iterable<Object[]> data() {
    return Arrays.asList(new Object[][] {{null}, {ORGA_KEY}});
  }

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();
  @Rule
  public ExpectedException exception = ExpectedException.none();

  private final String organizationKey;
  private SonarLintWsClient wsClient;
  private ProjectStorageUpdateExecutor projectUpdate;
  private final StoragePaths storagePaths = mock(StoragePaths.class);
  private final StorageReader storageReader = mock(StorageReader.class);
  private final TempFolder tempFolder = mock(TempFolder.class);
  private final ModuleHierarchyDownloader moduleHierarchy = mock(ModuleHierarchyDownloader.class);
  private final IssueStore issueStore = new InMemoryIssueStore();
  private final IssueStoreFactory issueStoreFactory = mock(IssueStoreFactory.class);
  private final ServerIssueUpdater serverIssueUpdater = mock(ServerIssueUpdater.class);
  private ProjectConfigurationDownloader projectConfigurationDownloader;
  private final ProjectFileListDownloader projectFileListDownloader = mock(ProjectFileListDownloader.class);

  public ProjectStorageUpdateExecutorTest(@Nullable String organizationKey) {
    this.organizationKey = organizationKey;
  }

  @Before
  public void setUp() throws IOException {
    wsClient = WsClientTestUtils.createMockWithStreamResponse(getQualityProfileUrl(),
      "/update/qualityprofiles_project.pb");
    when(wsClient.getOrganizationKey()).thenReturn(Optional.ofNullable(organizationKey));

    ValuesWsResponse response = ValuesWsResponse.newBuilder()
      .addSettings(Setting.newBuilder()
        .setKey("sonar.qualitygate")
        .setValue("1")
        .setInherited(true))
      .addSettings(Setting.newBuilder()
        .setKey("sonar.core.version")
        .setValue("6.7.1.23"))
      .addSettings(Setting.newBuilder()
        .setKey("sonar.java.someProp")
        .setValue("foo"))
      .build();
    PipedInputStream in = new PipedInputStream();
    final PipedOutputStream out = new PipedOutputStream(in);
    response.writeTo(out);
    out.close();
    WsClientTestUtils.addResponse(wsClient, "/api/settings/values.protobufcomponent=" + MODULE_KEY_WITH_BRANCH_URLENCODED, in);

    WsClientTestUtils.addResponse(wsClient, "/batch/issues?key=" + MODULE_KEY_WITH_BRANCH_URLENCODED, newEmptyStream());

    File tempDir = temp.newFolder();

    when(tempFolder.newDir()).thenReturn(tempDir);
    org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties.Builder propBuilder = GlobalProperties.newBuilder();
    propBuilder.putProperties("sonar.qualitygate", "2");
    propBuilder.putProperties("sonar.core.version", "6.7.1.23");
    when(storageReader.readGlobalProperties()).thenReturn(propBuilder.build());
    when(storageReader.readServerInfos()).thenReturn(ServerInfos.newBuilder().build());

    Map<String, String> modulesPath = new HashMap<>();
    modulesPath.put(MODULE_KEY_WITH_BRANCH, "");
    modulesPath.put(MODULE_KEY_WITH_BRANCH + "child1", "child 1");
    when(moduleHierarchy.fetchModuleHierarchy(eq(MODULE_KEY_WITH_BRANCH), any(ProgressWrapper.class)))
      .thenReturn(modulesPath);

    when(issueStoreFactory.apply(any(Path.class))).thenReturn(issueStore);

    projectConfigurationDownloader = new ProjectConfigurationDownloader(moduleHierarchy, new ProjectQualityProfilesDownloader(wsClient), mock(SettingsDownloader.class));

    projectUpdate = new ProjectStorageUpdateExecutor(storageReader, storagePaths, wsClient, tempFolder,
      projectConfigurationDownloader, projectFileListDownloader, serverIssueUpdater);
  }

  @Test
  public void exception_ws_load_qps() throws IOException {
    // return trash from WS
    WsResponse response = mock(WsResponse.class);
    when(response.contentStream()).thenReturn(new ByteArrayInputStream(new byte[] {0, 1, 2}));
    when(wsClient.get(getQualityProfileUrl())).thenReturn(response);
    File destDir = temp.newFolder();
    QProfiles.Builder builder = QProfiles.newBuilder();

    builder.putQprofilesByKey("java-empty-74333", QProfiles.QProfile.newBuilder().build());

    when(storageReader.readQProfiles()).thenReturn(builder.build());
    when(storagePaths.getProjectStorageRoot(MODULE_KEY_WITH_BRANCH)).thenReturn(destDir.toPath());

    exception.expect(IllegalStateException.class);
    exception.expectMessage("Protocol message contained an invalid tag");
    projectUpdate.update(MODULE_KEY_WITH_BRANCH, new ProgressWrapper(null));
  }

  @Test
  public void project_update() throws Exception {
    File destDir = temp.newFolder();
    QProfiles.Builder builder = QProfiles.newBuilder();

    builder.putQprofilesByKey("cs-sonar-way-58886", QProfiles.QProfile.newBuilder().build());
    builder.putQprofilesByKey("java-empty-74333", QProfiles.QProfile.newBuilder().build());
    builder.putQprofilesByKey("js-sonar-way-60746", QProfiles.QProfile.newBuilder().build());
    builder.putQprofilesByKey("xoo2-basic-34035", QProfiles.QProfile.newBuilder().build());

    when(storageReader.readQProfiles()).thenReturn(builder.build());
    when(storagePaths.getProjectStorageRoot(MODULE_KEY_WITH_BRANCH)).thenReturn(destDir.toPath());

    projectUpdate.update(MODULE_KEY_WITH_BRANCH, new ProgressWrapper(null));

    ProjectConfiguration projectConfiguration = ProtobufUtil.readFile(destDir.toPath().resolve(StoragePaths.PROJECT_CONFIGURATION_PB), ProjectConfiguration.parser());
    assertThat(projectConfiguration.getQprofilePerLanguageMap()).containsOnly(
      entry("cs", "cs-sonar-way-58886"),
      entry("java", "java-empty-74333"),
      entry("js", "js-sonar-way-60746"));

    assertThat(projectConfiguration.getModulePathByKeyMap()).containsOnly(
      entry(MODULE_KEY_WITH_BRANCH, ""),
      entry(MODULE_KEY_WITH_BRANCH + "child1", "child 1"));
  }

  @Test
  public void test_error_if_qp_doesnt_exist() throws IOException {
    File destDir = temp.newFolder();
    QProfiles.Builder builder = QProfiles.newBuilder();

    builder.putQprofilesByKey("cs-sonar-way-58886", QProfiles.QProfile.newBuilder().build());
    builder.putQprofilesByKey("java-empty-74333", QProfiles.QProfile.newBuilder().build());
    builder.putQprofilesByKey("xoo2-basic-34035", QProfiles.QProfile.newBuilder().build());

    when(storageReader.readQProfiles()).thenReturn(builder.build());
    when(storagePaths.getProjectStorageRoot(MODULE_KEY_WITH_BRANCH)).thenReturn(destDir.toPath());

    exception.expect(IllegalStateException.class);
    exception.expectMessage("is associated to quality profile 'js-sonar-way-60746' that is not in the storage");
    projectUpdate.update(MODULE_KEY_WITH_BRANCH, new ProgressWrapper(null));
  }

  @Test
  public void test_server_issues_are_downloaded_and_stored() throws IOException {
    WsClientTestUtils.addResponse(wsClient, getQualityProfileUrl(), newEmptyStream());
    when(storageReader.readQProfiles()).thenReturn(QProfiles.getDefaultInstance());

    when(storagePaths.getProjectStorageRoot(MODULE_KEY_WITH_BRANCH)).thenReturn(temp.newFolder().toPath());

    ServerIssue fileIssue1 = ServerIssue.newBuilder()
      .setPath("some/path")
      .setRuleKey("squid:x")
      .build();
    ServerIssue fileIssue2 = ServerIssue.newBuilder()
      .setPath("some/path")
      .setRuleKey("squid:y")
      .build();
    ServerIssue anotherFileIssue = ServerIssue.newBuilder()
      .setPath("another/path")
      .build();
    ServerIssue notDownloadedIssue = ServerIssue.newBuilder()
      .setPath("yet/another/path")
      .build();

    IssueDownloader issueDownloader = mock(IssueDownloader.class);
    when(issueDownloader.download(eq(MODULE_KEY_WITH_BRANCH), any(ProjectConfiguration.class), any(ProgressWrapper.class)))
      .thenReturn(Arrays.asList(fileIssue1, fileIssue2, anotherFileIssue));

    projectUpdate = new ProjectStorageUpdateExecutor(storageReader, storagePaths, wsClient, tempFolder, projectConfigurationDownloader,
      projectFileListDownloader, serverIssueUpdater);
    projectUpdate.update(MODULE_KEY_WITH_BRANCH, new ProgressWrapper(null));

    verify(serverIssueUpdater).updateServerIssues(eq(MODULE_KEY_WITH_BRANCH), any(ProjectConfiguration.class), any(Path.class), any(ProgressWrapper.class));
  }

  @Test
  public void test_update_components() {
    Path temp = tempFolder.newDir().toPath();
    projectUpdate = new ProjectStorageUpdateExecutor(storageReader, storagePaths, wsClient, tempFolder, projectConfigurationDownloader,
      projectFileListDownloader, serverIssueUpdater);
    ProjectConfiguration.Builder projectConfigurationBuilder = ProjectConfiguration.newBuilder();
    projectConfigurationBuilder.putModulePathByKey("rootModule", "");
    projectConfigurationBuilder.putModulePathByKey("moduleA", "A");
    projectConfigurationBuilder.putModulePathByKey("moduleB", "B");

    List<String> fileList = new ArrayList<>();
    fileList.add("rootModule:pom.xml");
    fileList.add("unknownModule:unknownFile");
    fileList.add("moduleA:a.java");
    fileList.add("moduleB:b.java");

    when(projectFileListDownloader.get(eq("rootModule"), any(ProgressWrapper.class))).thenReturn(fileList);
    projectUpdate.updateComponents("rootModule", temp, projectConfigurationBuilder.build(), mock(ProgressWrapper.class));

    Sonarlint.ProjectComponents components = ProtobufUtil.readFile(temp.resolve(StoragePaths.COMPONENT_LIST_PB), Sonarlint.ProjectComponents.parser());
    assertThat(components.getComponentList()).containsOnly(
      "pom.xml", "unknownFile", "A/a.java", "B/b.java");
  }

  private String getQualityProfileUrl() {
    String url = "/api/qualityprofiles/search.protobuf?project=" + MODULE_KEY_WITH_BRANCH_URLENCODED;
    if (organizationKey != null) {
      url += "&organization=" + organizationKey;
    }
    return url;
  }

}
