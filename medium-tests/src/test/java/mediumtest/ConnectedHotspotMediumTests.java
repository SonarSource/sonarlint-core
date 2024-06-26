/*
 * SonarLint Core - Medium Tests
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
package mediumtest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.SonarLintTestRpcServer;
import mediumtest.fixtures.TestPlugin;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileSystem;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.client.legacy.analysis.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.legacy.analysis.EngineConfiguration;
import org.sonarsource.sonarlint.core.client.legacy.analysis.RawIssue;
import org.sonarsource.sonarlint.core.client.legacy.analysis.RawIssueListener;
import org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import testutils.MockWebServerExtensionWithProtobuf;
import testutils.TestUtils;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static testutils.TestUtils.createNoOpLogOutput;

class ConnectedHotspotMediumTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @AfterEach
  void stop() {
    if (engine != null) {
      engine.stop();
      engine = null;
    }
  }

  private SonarLintTestRpcServer backend;

  @AfterEach
  void stopBackend() throws ExecutionException, InterruptedException {
    if (backend != null) {
      backend.shutdown().get();
    }
  }

  @Test
  void should_not_locally_detect_hotspots_when_connected_to_a_never_synced_server(@TempDir Path baseDir) throws Exception {
    createStorageAndEngine(null);
    var inputFile = prepareJavaInputFile(baseDir);

    final List<RawIssue> issues = new ArrayList<>();
    engine.analyze(AnalysisConfiguration.builder()
      .setBaseDir(baseDir)
      .addInputFile(inputFile)
      .setModuleKey("key")
      .build(),
      new StoreIssueListener(issues), null, null, CONFIG_SCOPE_ID);

    assertThat(issues).isEmpty();
  }

  @Test
  void should_locally_detect_hotspots_when_connected_to_sonarqube_9_9_plus(@TempDir Path baseDir) throws Exception {
    createStorageAndEngine("9.9");
    var inputFile = prepareJavaInputFile(baseDir);

    final List<RawIssue> issues = new ArrayList<>();
    engine.analyze(AnalysisConfiguration.builder()
      .setBaseDir(baseDir)
      .addInputFile(inputFile)
      .setModuleKey("key")
      .build(),
      new StoreIssueListener(issues), null, null, CONFIG_SCOPE_ID);

    assertThat(issues).extracting("ruleKey", "textRange", "inputFile.path", "severity")
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(tuple("java:S5852", new TextRangeDto(3, 28, 3, 35), inputFile.getPath(), IssueSeverity.BLOCKER));
  }

  private void createStorageAndEngine(String serverVersion) {
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, mockWebServer.url("/"), storage -> storage
        .withServerVersion(serverVersion)
        .withPlugin(TestPlugin.JAVA)
        .withProject(JAVA_MODULE_KEY, project -> project
          .withRuleSet("java", ruleSet -> ruleSet
            .withActiveRule("java:S5852", "BLOCKER"))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, JAVA_MODULE_KEY)
      .withSecurityHotspotsEnabled()
      .withExtraEnabledLanguagesInConnectedMode(Language.JAVA)
      .build();

    var config = EngineConfiguration.builder()
      .setSonarLintUserHome(backend.getUserHome())
      .setLogOutput(createNoOpLogOutput())
      .setModulesProvider(() -> List.of(new ClientModuleInfo("key", mock(ClientModuleFileSystem.class))))
      .build();
    engine = new SonarLintAnalysisEngine(config, backend, CONNECTION_ID);
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

  static class StoreIssueListener implements RawIssueListener {
    private final List<RawIssue> issues;

    StoreIssueListener(List<RawIssue> issues) {
      this.issues = issues;
    }

    @Override
    public void handle(RawIssue rawIssue) {
      issues.add(rawIssue);
    }
  }

  @RegisterExtension
  private final MockWebServerExtensionWithProtobuf mockWebServer = new MockWebServerExtensionWithProtobuf();

  private static final String CONNECTION_ID = StringUtils.repeat("very-long-id", 30);
  private static final String CONFIG_SCOPE_ID = "configScopeId";
  private static final String JAVA_MODULE_KEY = "test-project-2";
  private static SonarLintAnalysisEngine engine;

}
