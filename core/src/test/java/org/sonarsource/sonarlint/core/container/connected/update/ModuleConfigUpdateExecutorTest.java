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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

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
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.sonar.api.utils.TempFolder;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.container.connected.InMemoryIssueStore;
import org.sonarsource.sonarlint.core.container.connected.IssueStore;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;
import org.sonarsource.sonarlint.core.util.StringUtils;

import static org.sonarsource.sonarlint.core.container.connected.update.IssueUtils.createFileKey;
import static org.sonarsource.sonarlint.core.container.storage.ProtobufUtilTest.newEmptyStream;

@RunWith(Parameterized.class)
public class ModuleConfigUpdateExecutorTest {

  private static final String LTS = "4.5.6";

  @Parameters(name = "SQ version={0}")
  public static Object[] data() {
    return new Object[] {LTS, "5.2"};
  }

  private static final String MODULE_KEY_WITH_BRANCH = "module:key/with_branch";
  private static final String MODULE_KEY_WITH_BRANCH_URLENCODED = StringUtils.urlEncode(MODULE_KEY_WITH_BRANCH);

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();
  @Rule
  public ExpectedException exception = ExpectedException.none();

  private SonarLintWsClient wsClient;
  private ModuleConfigUpdateExecutor moduleUpdate;
  private StorageManager storageManager;
  private TempFolder tempFolder;
  private String serverVersion;
  private ModuleHierarchyDownloader moduleHierarchy;
  private IssueStore issueStore;
  private IssueStoreFactory issueStoreFactory;

  public ModuleConfigUpdateExecutorTest(String serverVersion) {
    this.serverVersion = serverVersion;
  }

  @Before
  public void setUp() throws IOException {
    // After 5.2
    wsClient = WsClientTestUtils.createMockWithStreamResponse(getQualityProfileUrl(),
      "/update/qualityprofiles_project.pb");

    // Before 5.2
    WsClientTestUtils.addResponse(wsClient, "/batch/project?preview=true&key=" + MODULE_KEY_WITH_BRANCH_URLENCODED,
      "{\"timestamp\":123456,\"activeRules\":[],"
        + "\"qprofilesByLanguage\":{"
        + "\"java\":{\"key\": \"java-empty-74333\", \"name\": \"Java Empty\", \"language\": \"java\"},"
        + "\"js\":{\"key\": \"js-sonar-way-60746\", \"name\": \"Java Empty\", \"language\": \"js\"},"
        + "\"cs\":{\"key\": \"cs-sonar-way-58886\", \"name\": \"Sonar Way\", \"language\": \"cs\"}"
        + "}}");

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

    ServerInfos serverInfos = ServerInfos.newBuilder().setVersion(serverVersion).build();
    when(storageManager.readServerInfosFromStorage()).thenReturn(serverInfos);
    moduleHierarchy = mock(ModuleHierarchyDownloader.class);
    Map<String, String> modulesPath = new HashMap<>();
    modulesPath.put(MODULE_KEY_WITH_BRANCH, "");
    modulesPath.put(MODULE_KEY_WITH_BRANCH + "child1", "child 1");
    when(moduleHierarchy.fetchModuleHierarchy(MODULE_KEY_WITH_BRANCH)).thenReturn(modulesPath);

    issueStoreFactory = mock(IssueStoreFactory.class);
    issueStore = new InMemoryIssueStore();
    when(issueStoreFactory.apply(any(Path.class))).thenReturn(issueStore);
  }

  @Test
  public void exception_ws_load_qps() throws IOException {
    assumeTrue(!serverVersion.equals(LTS));

    when(wsClient.get(getQualityProfileUrl())).thenThrow(IOException.class);
    File destDir = temp.newFolder();
    QProfiles.Builder builder = QProfiles.newBuilder();

    Map<String, QProfiles.QProfile> mutableQprofilesByKey = builder.getMutableQprofilesByKey();
    mutableQprofilesByKey.put("java-empty-74333", QProfiles.QProfile.newBuilder().build());

    when(storageManager.readQProfilesFromStorage()).thenReturn(builder.build());
    when(storageManager.getModuleStorageRoot(MODULE_KEY_WITH_BRANCH)).thenReturn(destDir.toPath());

    moduleUpdate = new ModuleConfigUpdateExecutor(storageManager, wsClient, (key) -> Collections.emptyIterator(), issueStoreFactory, moduleHierarchy, tempFolder);

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

    moduleUpdate = new ModuleConfigUpdateExecutor(storageManager, wsClient, (key) -> Collections.emptyIterator(), issueStoreFactory, moduleHierarchy, tempFolder);

    moduleUpdate.update(MODULE_KEY_WITH_BRANCH);

    ModuleConfiguration moduleConfiguration = ProtobufUtil.readFile(destDir.toPath().resolve(StorageManager.MODULE_CONFIGURATION_PB), ModuleConfiguration.parser());
    assertThat(moduleConfiguration.getQprofilePerLanguage()).containsOnly(
      entry("cs", "cs-sonar-way-58886"),
      entry("java", "java-empty-74333"),
      entry("js", "js-sonar-way-60746"));

    assertThat(moduleConfiguration.getModulePathByKey()).containsOnly(
      entry(MODULE_KEY_WITH_BRANCH, ""),
      entry( MODULE_KEY_WITH_BRANCH + "child1", "child 1"));
  }

  @Test
  public void test_error_if_qp_doesnt_exist() throws IOException {
    assumeTrue(!serverVersion.equals(LTS));
    File destDir = temp.newFolder();
    QProfiles.Builder builder = QProfiles.newBuilder();

    Map<String, QProfiles.QProfile> mutableQprofilesByKey = builder.getMutableQprofilesByKey();
    mutableQprofilesByKey.put("cs-sonar-way-58886", QProfiles.QProfile.newBuilder().build());
    mutableQprofilesByKey.put("java-empty-74333", QProfiles.QProfile.newBuilder().build());
    mutableQprofilesByKey.put("xoo2-basic-34035", QProfiles.QProfile.newBuilder().build());

    when(storageManager.readQProfilesFromStorage()).thenReturn(builder.build());
    when(storageManager.getModuleStorageRoot(MODULE_KEY_WITH_BRANCH)).thenReturn(destDir.toPath());

    moduleUpdate = new ModuleConfigUpdateExecutor(storageManager, wsClient, (key) -> Collections.emptyIterator(), issueStoreFactory, moduleHierarchy, tempFolder);

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

    moduleUpdate = new ModuleConfigUpdateExecutor(storageManager, wsClient, issueDownloader, issueStoreFactory, moduleHierarchy, tempFolder);
    moduleUpdate.update(MODULE_KEY_WITH_BRANCH);

    assertThat(issueStore.load(createFileKey(fileIssue1))).containsOnly(fileIssue1, fileIssue2);
    assertThat(issueStore.load(createFileKey(anotherFileIssue))).containsOnly(anotherFileIssue);
    assertThat(issueStore.load(createFileKey(notDownloadedIssue))).isEmpty();
  }

  private String getQualityProfileUrl() {
    return "/api/qualityprofiles/search.protobuf?projectKey=" + MODULE_KEY_WITH_BRANCH_URLENCODED;
  }

}
