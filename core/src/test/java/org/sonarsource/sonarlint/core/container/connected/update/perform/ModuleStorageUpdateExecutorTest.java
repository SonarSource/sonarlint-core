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
package org.sonarsource.sonarlint.core.container.connected.update.perform;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.utils.TempFolder;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.container.connected.InMemoryIssueStore;
import org.sonarsource.sonarlint.core.container.connected.IssueStore;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ModuleConfigurationDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ModuleHierarchyDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ModuleQualityProfilesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.SettingsDownloader;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;
import org.sonarsource.sonarlint.core.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.container.connected.update.IssueUtils.createFileKey;
import static org.sonarsource.sonarlint.core.container.storage.ProtobufUtilTest.newEmptyStream;

public class ModuleStorageUpdateExecutorTest {

  private static final String MODULE_KEY_WITH_BRANCH = "module:key/with_branch";
  private static final String MODULE_KEY_WITH_BRANCH_URLENCODED = StringUtils.urlEncode(MODULE_KEY_WITH_BRANCH);

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();
  @Rule
  public ExpectedException exception = ExpectedException.none();

  private SonarLintWsClient wsClient;
  private ModuleStorageUpdateExecutor moduleUpdate;
  private StorageManager storageManager;
  private TempFolder tempFolder;
  private ModuleHierarchyDownloader moduleHierarchy;
  private IssueStore issueStore;
  private IssueStoreFactory issueStoreFactory;
  private ModuleConfigurationDownloader moduleConfigurationDownloader;

  @Before
  public void setUp() throws IOException {
    wsClient = WsClientTestUtils.createMockWithStreamResponse(getQualityProfileUrl(),
      "/update/qualityprofiles_project.pb");

    WsClientTestUtils.addResponse(wsClient, "/api/properties?format=json&resource=" + MODULE_KEY_WITH_BRANCH_URLENCODED,
      "[{\"key\":\"sonar.qualitygate\",\"value\":\"1\",\"values\": []},"
        + "{\"key\":\"sonar.core.version\",\"value\":\"5.5-SNAPSHOT\"},"
        + "{\"key\":\"sonar.java.someProp\",\"value\":\"foo\"}]");

    WsClientTestUtils.addResponse(wsClient, "/batch/issues?key=" + MODULE_KEY_WITH_BRANCH_URLENCODED, newEmptyStream());

    File tempDir = temp.newFolder();

    tempFolder = mock(TempFolder.class);
    when(tempFolder.newDir()).thenReturn(tempDir);
    storageManager = mock(StorageManager.class);
    org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties.Builder propBuilder = GlobalProperties.newBuilder();
    propBuilder.getMutableProperties().put("sonar.qualitygate", "2");
    propBuilder.getMutableProperties().put("sonar.core.version", "5.5-SNAPSHOT");
    when(storageManager.readGlobalPropertiesFromStorage()).thenReturn(propBuilder.build());
    when(storageManager.readServerInfosFromStorage()).thenReturn(ServerInfos.newBuilder().build());

    moduleHierarchy = mock(ModuleHierarchyDownloader.class);
    Map<String, String> modulesPath = new HashMap<>();
    modulesPath.put(MODULE_KEY_WITH_BRANCH, "");
    modulesPath.put(MODULE_KEY_WITH_BRANCH + "child1", "child 1");
    when(moduleHierarchy.fetchModuleHierarchy(MODULE_KEY_WITH_BRANCH)).thenReturn(modulesPath);

    issueStoreFactory = mock(IssueStoreFactory.class);
    issueStore = new InMemoryIssueStore();
    when(issueStoreFactory.apply(any(Path.class))).thenReturn(issueStore);

    moduleConfigurationDownloader = new ModuleConfigurationDownloader(moduleHierarchy, new ModuleQualityProfilesDownloader(wsClient), mock(SettingsDownloader.class));
  }

  @Test
  public void exception_ws_load_qps() throws IOException {
    when(wsClient.get(getQualityProfileUrl())).thenThrow(IOException.class);
    File destDir = temp.newFolder();
    QProfiles.Builder builder = QProfiles.newBuilder();

    Map<String, QProfiles.QProfile> mutableQprofilesByKey = builder.getMutableQprofilesByKey();
    mutableQprofilesByKey.put("java-empty-74333", QProfiles.QProfile.newBuilder().build());

    when(storageManager.readQProfilesFromStorage()).thenReturn(builder.build());
    when(storageManager.getModuleStorageRoot(MODULE_KEY_WITH_BRANCH)).thenReturn(destDir.toPath());

    moduleUpdate = new ModuleStorageUpdateExecutor(storageManager, wsClient, (key) -> Collections.emptyIterator(), issueStoreFactory, tempFolder, moduleConfigurationDownloader);

    exception.expect(IllegalStateException.class);
    exception.expectMessage("Failed to load module quality profiles");
    moduleUpdate.update(MODULE_KEY_WITH_BRANCH);
  }

  @Test
  public void module_update() throws Exception {
    File destDir = temp.newFolder();
    QProfiles.Builder builder = QProfiles.newBuilder();

    Map<String, QProfiles.QProfile> mutableQprofilesByKey = builder.getMutableQprofilesByKey();
    mutableQprofilesByKey.put("cs-sonar-way-58886", QProfiles.QProfile.newBuilder().build());
    mutableQprofilesByKey.put("java-empty-74333", QProfiles.QProfile.newBuilder().build());
    mutableQprofilesByKey.put("js-sonar-way-60746", QProfiles.QProfile.newBuilder().build());
    mutableQprofilesByKey.put("xoo2-basic-34035", QProfiles.QProfile.newBuilder().build());

    when(storageManager.readQProfilesFromStorage()).thenReturn(builder.build());
    when(storageManager.getModuleStorageRoot(MODULE_KEY_WITH_BRANCH)).thenReturn(destDir.toPath());

    moduleUpdate = new ModuleStorageUpdateExecutor(storageManager, wsClient, (key) -> Collections.emptyIterator(), issueStoreFactory, tempFolder, moduleConfigurationDownloader);

    moduleUpdate.update(MODULE_KEY_WITH_BRANCH);

    ModuleConfiguration moduleConfiguration = ProtobufUtil.readFile(destDir.toPath().resolve(StorageManager.MODULE_CONFIGURATION_PB), ModuleConfiguration.parser());
    assertThat(moduleConfiguration.getQprofilePerLanguage()).containsOnly(
      entry("cs", "cs-sonar-way-58886"),
      entry("java", "java-empty-74333"),
      entry("js", "js-sonar-way-60746"));

    assertThat(moduleConfiguration.getModulePathByKey()).containsOnly(
      entry(MODULE_KEY_WITH_BRANCH, ""),
      entry(MODULE_KEY_WITH_BRANCH + "child1", "child 1"));
  }

  @Test
  public void test_error_if_qp_doesnt_exist() throws IOException {
    File destDir = temp.newFolder();
    QProfiles.Builder builder = QProfiles.newBuilder();

    Map<String, QProfiles.QProfile> mutableQprofilesByKey = builder.getMutableQprofilesByKey();
    mutableQprofilesByKey.put("cs-sonar-way-58886", QProfiles.QProfile.newBuilder().build());
    mutableQprofilesByKey.put("java-empty-74333", QProfiles.QProfile.newBuilder().build());
    mutableQprofilesByKey.put("xoo2-basic-34035", QProfiles.QProfile.newBuilder().build());

    when(storageManager.readQProfilesFromStorage()).thenReturn(builder.build());
    when(storageManager.getModuleStorageRoot(MODULE_KEY_WITH_BRANCH)).thenReturn(destDir.toPath());

    moduleUpdate = new ModuleStorageUpdateExecutor(storageManager, wsClient, (key) -> Collections.emptyIterator(), issueStoreFactory, tempFolder, moduleConfigurationDownloader);

    exception.expect(IllegalStateException.class);
    exception.expectMessage("is associated to quality profile 'js-sonar-way-60746' that is not in storage");
    moduleUpdate.update(MODULE_KEY_WITH_BRANCH);
  }

  @Test
  public void test_server_issues_are_downloaded_and_stored() throws IOException {
    WsClientTestUtils.addResponse(wsClient, getQualityProfileUrl(), newEmptyStream());
    when(storageManager.readQProfilesFromStorage()).thenReturn(QProfiles.getDefaultInstance());

    when(storageManager.getModuleStorageRoot(MODULE_KEY_WITH_BRANCH)).thenReturn(temp.newFolder().toPath());

    ScannerInput.ServerIssue fileIssue1 = ScannerInput.ServerIssue.newBuilder()
      .setModuleKey("someModuleKey")
      .setPath("some/path")
      .setRuleKey("squid:x")
      .build();
    ScannerInput.ServerIssue fileIssue2 = ScannerInput.ServerIssue.newBuilder()
      .setModuleKey("someModuleKey")
      .setPath("some/path")
      .setRuleKey("squid:y")
      .build();
    ScannerInput.ServerIssue anotherFileIssue = ScannerInput.ServerIssue.newBuilder()
      .setModuleKey("someModuleKey")
      .setPath("another/path")
      .build();
    ScannerInput.ServerIssue notDownloadedIssue = ScannerInput.ServerIssue.newBuilder()
      .setModuleKey("someModuleKey")
      .setPath("yet/another/path")
      .build();

    IssueDownloader issueDownloader = moduleKey -> Arrays.asList(fileIssue1, fileIssue2, anotherFileIssue).iterator();

    moduleUpdate = new ModuleStorageUpdateExecutor(storageManager, wsClient, issueDownloader, issueStoreFactory, tempFolder, moduleConfigurationDownloader);
    moduleUpdate.update(MODULE_KEY_WITH_BRANCH);

    assertThat(issueStore.load(createFileKey(fileIssue1))).containsOnly(fileIssue1, fileIssue2);
    assertThat(issueStore.load(createFileKey(anotherFileIssue))).containsOnly(anotherFileIssue);
    assertThat(issueStore.load(createFileKey(notDownloadedIssue))).isEmpty();
  }

  private String getQualityProfileUrl() {
    return "/api/qualityprofiles/search.protobuf?projectKey=" + MODULE_KEY_WITH_BRANCH_URLENCODED;
  }

}
