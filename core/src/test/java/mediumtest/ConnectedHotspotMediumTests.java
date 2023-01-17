/*
 * SonarLint Core - Implementation
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
package mediumtest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileSystem;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Hotspots;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import testutils.MockWebServerExtensionWithProtobuf;
import testutils.TestUtils;

import static mediumtest.fixtures.StorageFixture.newStorage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.sonarsource.sonarlint.core.commons.testutils.MockWebServerExtension.httpClient;
import static testutils.TestUtils.createNoOpLogOutput;

class ConnectedHotspotMediumTests {

  @AfterEach
  void stop() {
    if (sonarlint != null) {
      sonarlint.stop(true);
      sonarlint = null;
    }
  }

  @Test
  void should_not_locally_detect_hotspots_when_connected_to_a_never_synced_server(@TempDir Path baseDir) throws Exception {
    createStorageAndEngine(null);
    var inputFile = prepareJavaInputFile(baseDir);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(ConnectedAnalysisConfiguration.builder()
        .setProjectKey(JAVA_MODULE_KEY)
        .setBaseDir(baseDir)
        .addInputFile(inputFile)
        .setModuleKey("key")
        .build(),
      new StoreIssueListener(issues), null, null);

    assertThat(issues).isEmpty();
  }

  @Test
  void should_not_locally_detect_hotspots_when_connected_to_a_server_not_permitting_hotspot_tracking(@TempDir Path baseDir) throws Exception {
    createStorageAndEngine("9.6");
    var inputFile = prepareJavaInputFile(baseDir);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(ConnectedAnalysisConfiguration.builder()
        .setProjectKey(JAVA_MODULE_KEY)
        .setBaseDir(baseDir)
        .addInputFile(inputFile)
        .setModuleKey("key")
        .build(),
      new StoreIssueListener(issues), null, null);

    assertThat(issues).isEmpty();
  }

  @Test
  void should_locally_detect_hotspots_when_connected_to_sonarqube_9_7_plus(@TempDir Path baseDir) throws Exception {
    createStorageAndEngine("9.7");
    var inputFile = prepareJavaInputFile(baseDir);

    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(ConnectedAnalysisConfiguration.builder()
      .setProjectKey(JAVA_MODULE_KEY)
      .setBaseDir(baseDir)
      .addInputFile(inputFile)
      .setModuleKey("key")
      .build(),
      new StoreIssueListener(issues), null, null);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("java:S5852", 3, inputFile.getPath(), IssueSeverity.BLOCKER));
  }

  @Test
  void should_return_project_hotspots_after_downloading_them() {
    createStorageAndEngine("9.7");
    mockWebServer.addProtobufResponse("/api/hotspots/search.protobuf?projectKey=" + JAVA_MODULE_KEY + "&branch=master&ps=500&p=1", Hotspots.SearchWsResponse.newBuilder()
      .setPaging(Common.Paging.newBuilder().setTotal(1).build())
      .addHotspots(Hotspots.SearchWsResponse.Hotspot.newBuilder()
        .setComponent("component:file/path")
        .setTextRange(Common.TextRange.newBuilder().setStartLine(1).setStartOffset(2).setEndLine(3).setEndOffset(4).build())
        .setStatus("TO_REVIEW")
        .setKey("hotspotKey1")
        .setCreationDate("2020-09-21T12:46:39+0000")
        .setRuleKey("ruleKey1")
        .setMessage("message1")
        .setVulnerabilityProbability("MEDIUM")
        .build())
      .addComponents(Hotspots.Component.newBuilder().setKey("component:file/path").setPath("file/path").build())
      .build());
    sonarlint.downloadAllServerHotspots(mockWebServer.endpointParams(), httpClient(), JAVA_MODULE_KEY, "master", null);

    var serverHotspots = sonarlint.getServerHotspots(new ProjectBinding(JAVA_MODULE_KEY, "", ""), "master", "file/path");

    assertThat(serverHotspots)
      .extracting("key", "ruleKey", "message", "filePath", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset", "creationDate",
      "resolved")
      .containsExactly(
        tuple("hotspotKey1", "ruleKey1", "message1", "file/path", 1, 2, 3, 4, LocalDateTime.of(2020, 9, 21, 12, 46, 39).toInstant(ZoneOffset.UTC), false));
  }

  @Test
  void should_return_file_hotspots_after_downloading_them() {
    createStorageAndEngine("9.7");
    mockWebServer.addProtobufResponse("/api/hotspots/search.protobuf?projectKey=" + JAVA_MODULE_KEY + "&files=file%2Fpath&branch=master&ps=500&p=1", Hotspots.SearchWsResponse.newBuilder()
      .setPaging(Common.Paging.newBuilder().setTotal(1).build())
      .addHotspots(Hotspots.SearchWsResponse.Hotspot.newBuilder()
        .setComponent("component:file/path")
        .setTextRange(Common.TextRange.newBuilder().setStartLine(1).setStartOffset(2).setEndLine(3).setEndOffset(4).build())
        .setStatus("TO_REVIEW")
        .setKey("hotspotKey1")
        .setCreationDate("2020-09-21T12:46:39+0000")
        .setRuleKey("ruleKey1")
        .setMessage("message1")
        .setVulnerabilityProbability("LOW")
        .build())
      .addComponents(Hotspots.Component.newBuilder().setKey("component:file/path").setPath("file/path").build())
      .build());
    var projectBinding = new ProjectBinding(JAVA_MODULE_KEY, "", "");
    sonarlint.downloadAllServerHotspotsForFile(mockWebServer.endpointParams(), httpClient(), projectBinding, "file/path", "master", null);

    var serverHotspots = sonarlint.getServerHotspots(projectBinding, "master", "file/path");

    assertThat(serverHotspots)
      .extracting("key", "ruleKey", "message", "filePath", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset", "creationDate",
        "resolved")
      .containsExactly(
        tuple("hotspotKey1", "ruleKey1", "message1", "file/path", 1, 2, 3, 4, LocalDateTime.of(2020, 9, 21, 12, 46, 39).toInstant(ZoneOffset.UTC), false));
  }

  private void createStorageAndEngine(String serverVersion) {
    var storage = newStorage(SERVER_ID)
      .withServerVersion(serverVersion)
      .withJavaPlugin()
      .withProject(JAVA_MODULE_KEY, project -> project
        .withRuleSet("java", ruleSet -> ruleSet
          .withActiveRule("java:S5852", "BLOCKER")))
      .create(storageDir);

    var config = ConnectedGlobalConfiguration.sonarQubeBuilder()
      .setConnectionId(SERVER_ID)
      .setSonarLintUserHome(slHome)
      .setStorageRoot(storage.getPath())
      .setLogOutput(createNoOpLogOutput())
      .addEnabledLanguages(Language.JAVA)
      .enableHotspots()
      .setModulesProvider(() -> List.of(new ClientModuleInfo("key", mock(ClientModuleFileSystem.class))))
      .build();
    sonarlint = new ConnectedSonarLintEngineImpl(config);
  }

  private ClientInputFile prepareJavaInputFile(Path baseDir) throws IOException {
    return prepareInputFile(baseDir, "Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    java.util.regex.Pattern.compile(\".*PATH=\\\"(.*)\\\"; export PATH;.*\");\n"
        + "  }\n"
        + "}",
      false);
  }

  private ClientInputFile prepareInputFile(Path baseDir, String relativePath, String content, final boolean isTest) throws IOException {
    final var file = new File(baseDir.toFile(), relativePath);
    FileUtils.write(file, content, StandardCharsets.UTF_8);
    return TestUtils.createInputFile(file.toPath(), relativePath, isTest);
  }

  static class StoreIssueListener implements IssueListener {
    private final List<Issue> issues;

    StoreIssueListener(List<Issue> issues) {
      this.issues = issues;
    }

    @Override
    public void handle(Issue issue) {
      issues.add(issue);
    }
  }

  @RegisterExtension
  private final MockWebServerExtensionWithProtobuf mockWebServer = new MockWebServerExtensionWithProtobuf();

  @TempDir
  Path slHome;
  @TempDir
  Path storageDir;
  private static final String SERVER_ID = StringUtils.repeat("very-long-id", 30);
  private static final String JAVA_MODULE_KEY = "test-project-2";
  private static ConnectedSonarLintEngineImpl sonarlint;

}
