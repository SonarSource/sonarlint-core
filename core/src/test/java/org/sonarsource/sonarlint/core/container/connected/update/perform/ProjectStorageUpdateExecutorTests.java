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

import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonar.api.utils.TempFolder;
import org.sonarqube.ws.Settings.Setting;
import org.sonarqube.ws.Settings.ValuesWsResponse;
import org.sonarsource.sonarlint.core.MockWebServerExtension;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.container.connected.InMemoryIssueStore;
import org.sonarsource.sonarlint.core.container.connected.IssueStore;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ModuleHierarchyDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectConfigurationDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectFileListDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectQualityProfilesDownloader;
import org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.QualityProfileStore;
import org.sonarsource.sonarlint.core.container.storage.ServerInfoStore;
import org.sonarsource.sonarlint.core.container.storage.StorageFolder;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectConfiguration;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue.Location;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectStorageUpdateExecutorTests {

  private static final ProgressWrapper PROGRESS = new ProgressWrapper(null);
  private static final String ORGA_KEY = "myOrga";
  private static final String MODULE_KEY_WITH_BRANCH = "module:key/with_branch";
  private static final String MODULE_KEY_WITH_BRANCH_URLENCODED = StringUtils.urlEncode(MODULE_KEY_WITH_BRANCH);

  @RegisterExtension
  static MockWebServerExtension mockServer = new MockWebServerExtension();

  private ProjectStorageUpdateExecutor underTest;
  private final ProjectStoragePaths projectStoragePaths = mock(ProjectStoragePaths.class);
  private final TempFolder tempFolder = mock(TempFolder.class);
  private final ModuleHierarchyDownloader moduleHierarchy = mock(ModuleHierarchyDownloader.class);
  private final IssueStore issueStore = new InMemoryIssueStore();
  private final IssueStoreFactory issueStoreFactory = mock(IssueStoreFactory.class);
  private final ServerIssueUpdater serverIssueUpdater = mock(ServerIssueUpdater.class);
  private ProjectConfigurationDownloader projectConfigurationDownloader;
  private final ProjectFileListDownloader projectFileListDownloader = mock(ProjectFileListDownloader.class);
  private QualityProfileStore qualityProfileStore;

  public void setUp(@Nullable String organizationKey, Path tempDir) throws IOException {
    Files.createDirectory(tempDir);

    mockServer.addResponseFromResource(getQualityProfileUrl(organizationKey), "/update/qualityprofiles_project.pb");
    mockServer.addProtobufResponse("/api/settings/values.protobuf?component=" + StringUtils.urlEncode(MODULE_KEY_WITH_BRANCH), ValuesWsResponse.newBuilder().build());

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

    mockServer.addProtobufResponse("/api/settings/values.protobufcomponent=" + MODULE_KEY_WITH_BRANCH_URLENCODED, response);

    when(tempFolder.newDir()).thenReturn(tempDir.toFile());
    ServerInfoStore serverInfoStore = new ServerInfoStore(new StorageFolder.Default(tempDir));
    serverInfoStore.store(ServerInfos.newBuilder().build());

    Map<String, String> modulesPath = new HashMap<>();
    modulesPath.put(MODULE_KEY_WITH_BRANCH, "");
    modulesPath.put(MODULE_KEY_WITH_BRANCH + "child1", "child 1");
    when(moduleHierarchy.fetchModuleHierarchy(eq(MODULE_KEY_WITH_BRANCH), any(ProgressWrapper.class)))
      .thenReturn(modulesPath);

    when(issueStoreFactory.apply(any(Path.class))).thenReturn(issueStore);

    ServerApiHelper serverApiHelper = mockServer.serverApiHelper(organizationKey);
    projectConfigurationDownloader = new ProjectConfigurationDownloader(moduleHierarchy, new ProjectQualityProfilesDownloader(serverApiHelper), serverApiHelper);

    qualityProfileStore = new QualityProfileStore(new StorageFolder.Default(tempDir));
    underTest = new ProjectStorageUpdateExecutor(projectStoragePaths, tempFolder, projectConfigurationDownloader, projectFileListDownloader,  serverIssueUpdater, qualityProfileStore);
  }

  @ParameterizedTest(name = "organizationKey=[{0}]")
  @NullSource
  @ValueSource(strings = {ORGA_KEY})
  void exception_ws_load_qps(@Nullable String organizationKey, @TempDir Path tempDir) throws IOException {
    setUp(organizationKey, tempDir.resolve("tmp"));

    // return trash from WS
    mockServer.addStringResponse(getQualityProfileUrl(organizationKey), "foo");

    Path storageDir = tempDir.resolve("destDir");
    QProfiles.Builder builder = QProfiles.newBuilder();

    builder.putQprofilesByKey("java-empty-74333", QProfiles.QProfile.newBuilder().build());

    qualityProfileStore.store(builder.build());
    when(projectStoragePaths.getProjectStorageRoot(MODULE_KEY_WITH_BRANCH)).thenReturn(storageDir);

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> underTest.update(MODULE_KEY_WITH_BRANCH, false, PROGRESS));
    assertThat(thrown)
      .hasMessage("Unable to parse WS response: Protocol message tag had invalid wire type.")
      .hasCauseInstanceOf(InvalidProtocolBufferException.class);
  }

  @ParameterizedTest(name = "organizationKey=[{0}]")
  @NullSource
  @ValueSource(strings = {ORGA_KEY})
  void project_update(@Nullable String organizationKey, @TempDir Path tempDir) throws Exception {
    setUp(organizationKey, tempDir.resolve("tmp"));

    Path storageDir = tempDir.resolve("destDir");
    Path globalStoragePath = storageDir.resolve("global");
    FileUtils.mkdirs(globalStoragePath);

    QProfiles.Builder builder = QProfiles.newBuilder();
    builder.putQprofilesByKey("cs-sonar-way-58886", QProfiles.QProfile.newBuilder().build());
    builder.putQprofilesByKey("java-empty-74333", QProfiles.QProfile.newBuilder().build());
    builder.putQprofilesByKey("js-sonar-way-60746", QProfiles.QProfile.newBuilder().build());
    builder.putQprofilesByKey("xoo2-basic-34035", QProfiles.QProfile.newBuilder().build());

    qualityProfileStore.store(builder.build());
    when(projectStoragePaths.getProjectStorageRoot(MODULE_KEY_WITH_BRANCH)).thenReturn(storageDir);

    underTest.update(MODULE_KEY_WITH_BRANCH, false, PROGRESS);

    ProjectConfiguration projectConfiguration = ProtobufUtil.readFile(storageDir.resolve(ProjectStoragePaths.PROJECT_CONFIGURATION_PB), ProjectConfiguration.parser());
    assertThat(projectConfiguration.getQprofilePerLanguageMap()).containsOnly(
      entry("cs", "cs-sonar-way-58886"),
      entry("java", "java-empty-74333"),
      entry("js", "js-sonar-way-60746"));

    assertThat(projectConfiguration.getModulePathByKeyMap()).containsOnly(
      entry(MODULE_KEY_WITH_BRANCH, ""),
      entry(MODULE_KEY_WITH_BRANCH + "child1", "child 1"));
  }

  @ParameterizedTest(name = "organizationKey=[{0}]")
  @NullSource
  @ValueSource(strings = {ORGA_KEY})
  void test_error_if_qp_doesnt_exist(@Nullable String organizationKey, @TempDir Path tempDir) throws IOException {
    setUp(organizationKey, tempDir.resolve("tmp"));

    Path storageDir = tempDir.resolve("destDir");
    Path globalStoragePath = storageDir.resolve("global");
    FileUtils.mkdirs(globalStoragePath);

    QProfiles.Builder builder = QProfiles.newBuilder();
    builder.putQprofilesByKey("cs-sonar-way-58886", QProfiles.QProfile.newBuilder().build());
    builder.putQprofilesByKey("java-empty-74333", QProfiles.QProfile.newBuilder().build());
    builder.putQprofilesByKey("xoo2-basic-34035", QProfiles.QProfile.newBuilder().build());

    qualityProfileStore.store(builder.build());
    when(projectStoragePaths.getProjectStorageRoot(MODULE_KEY_WITH_BRANCH)).thenReturn(storageDir);

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> underTest.update(MODULE_KEY_WITH_BRANCH, false, PROGRESS));
    assertThat(thrown).hasMessageContaining("is associated to quality profile 'js-sonar-way-60746' that is not in the storage");
  }

  @ParameterizedTest(name = "organizationKey=[{0}]")
  @NullSource
  @ValueSource(strings = {ORGA_KEY})
  void test_server_issues_are_downloaded_and_stored(@Nullable String organizationKey, @TempDir Path tempDir) throws IOException {
    setUp(organizationKey, tempDir.resolve("tmp"));

    Path storageDir = tempDir.resolve("destDir");
    Path globalStoragePath = storageDir.resolve("global");
    FileUtils.mkdirs(globalStoragePath);

    mockServer.addStringResponse(getQualityProfileUrl(organizationKey), "");

    qualityProfileStore.store(QProfiles.getDefaultInstance());

    when(projectStoragePaths.getProjectStorageRoot(MODULE_KEY_WITH_BRANCH)).thenReturn(storageDir);

    ServerIssue fileIssue1 = ServerIssue.newBuilder()
      .setPrimaryLocation(Location.newBuilder().setPath("some/path"))
      .setRuleKey("squid:x")
      .build();
    ServerIssue fileIssue2 = ServerIssue.newBuilder()
      .setPrimaryLocation(Location.newBuilder().setPath("some/path"))
      .setRuleKey("squid:y")
      .build();
    ServerIssue anotherFileIssue = ServerIssue.newBuilder()
      .setPrimaryLocation(Location.newBuilder().setPath("another/path"))
      .build();

    IssueDownloader issueDownloader = mock(IssueDownloader.class);
    when(issueDownloader.download(eq(MODULE_KEY_WITH_BRANCH), any(ProjectConfiguration.class), eq(false), eq(null), any(ProgressWrapper.class)))
      .thenReturn(Arrays.asList(fileIssue1, fileIssue2, anotherFileIssue));

    underTest = new ProjectStorageUpdateExecutor(projectStoragePaths, tempFolder, projectConfigurationDownloader,
      projectFileListDownloader, serverIssueUpdater, qualityProfileStore);
    underTest.update(MODULE_KEY_WITH_BRANCH, false, PROGRESS);

    verify(serverIssueUpdater).updateServerIssues(eq(MODULE_KEY_WITH_BRANCH), any(ProjectConfiguration.class), any(Path.class), eq(false), any(ProgressWrapper.class));
  }

  @ParameterizedTest(name = "organizationKey=[{0}]")
  @NullSource
  @ValueSource(strings = {ORGA_KEY})
  void test_update_components(@Nullable String organizationKey, @TempDir Path tempDir) throws IOException {
    setUp(organizationKey, tempDir.resolve("tmp"));

    Path temp = tempFolder.newDir().toPath();
    underTest = new ProjectStorageUpdateExecutor(projectStoragePaths, tempFolder, projectConfigurationDownloader,
      projectFileListDownloader, serverIssueUpdater, qualityProfileStore);
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
    underTest.updateComponents("rootModule", temp, projectConfigurationBuilder.build(), mock(ProgressWrapper.class));

    Sonarlint.ProjectComponents components = ProtobufUtil.readFile(temp.resolve(ProjectStoragePaths.COMPONENT_LIST_PB), Sonarlint.ProjectComponents.parser());
    assertThat(components.getComponentList()).containsOnly(
      "pom.xml", "unknownFile", "A/a.java", "B/b.java");
  }

  private String getQualityProfileUrl(@Nullable String organizationKey) {
    String url = "/api/qualityprofiles/search.protobuf?project=" + MODULE_KEY_WITH_BRANCH_URLENCODED;
    if (organizationKey != null) {
      url += "&organization=" + organizationKey;
    }
    return url;
  }

}
