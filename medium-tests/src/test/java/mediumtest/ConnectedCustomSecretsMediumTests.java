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
import mediumtest.fixtures.SonarLintTestBackend;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import testutils.MockWebServerExtensionWithProtobuf;
import testutils.TestUtils;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static mediumtest.fixtures.storage.StorageFixture.newStorage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 *  INFO: This test is only a placeholder for coverage on SLCORE-668 and will be replaced by a proper integration test
 *        once `sonar-text-enterprise` 2.8 is released and part of SonarQube 10.4!
 */
class ConnectedCustomSecretsMediumTests {
  @RegisterExtension
  private final MockWebServerExtensionWithProtobuf mockWebServerExtension = new MockWebServerExtensionWithProtobuf();

  private static final String CONNECTION_ID = StringUtils.repeat("secret-connection", 30);
  private static final String PROJECT_KEY = "secret-connected-project";
  private static ConnectedSonarLintEngineImpl sonarlint;
  private SonarLintTestBackend backend;

  @BeforeAll
  static void prepare(@TempDir Path slHome) {
    var storage = newStorage(CONNECTION_ID)
      .withServerVersion("10.4")
      .withTextPlugin()
      .withProject(PROJECT_KEY)
      .withProject(PROJECT_KEY, project -> project
        .withRuleSet("secrets", ruleSet -> ruleSet
          .withActiveRule("secrets:S6290", "BLOCKER")))
      .create(slHome);

    var config = ConnectedGlobalConfiguration.sonarQubeBuilder()
      .setConnectionId(CONNECTION_ID)
      .setSonarLintUserHome(slHome)
      .setStorageRoot(storage.getPath())
      .setLogOutput((m, l) -> System.out.println(m))
      .addEnabledLanguages(Language.SECRETS)
      .build();
    sonarlint = new ConnectedSonarLintEngineImpl(config);
  }

  @AfterAll
  static void stop() throws ExecutionException, InterruptedException {
    if (sonarlint != null) {
      sonarlint.stop(true);
      sonarlint = null;
    }
  }

  @BeforeEach
  void prepareBackend() {
    var fakeClient = newFakeClient()
      .build();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, mockWebServerExtension.url("/"))
      .build(fakeClient);
  }

  @AfterEach
  void stopBackend() throws ExecutionException, InterruptedException {
    if (backend != null) {
      backend.shutdown().get();
    }
  }

  @Test
  void test_analysis_with_text_analyzer_from_connection(@TempDir Path baseDir) throws Exception {
    var inputFile = prepareInputFile(baseDir, "Foo.java",
      "package com;\n"
      + "public class Foo {\n"
      + "  public static final String KEY = \"AKIAIGKECZXA7AEIJLMQ\";\n"
      + "}", false);
    final List<Issue> issues = new ArrayList<>();
    sonarlint.analyze(ConnectedAnalysisConfiguration.builder()
        .setProjectKey(PROJECT_KEY)
        .setBaseDir(baseDir)
        .addInputFile(inputFile)
        .setModuleKey("key")
        .build(),
      new ConnectedIssueMediumTests.StoreIssueListener(issues), null, null);

    assertThat(issues).extracting("ruleKey", "startLine", "inputFile.path", "severity").containsOnly(
      tuple("secrets:S6290", 3, inputFile.getPath(), IssueSeverity.BLOCKER));
  }

  private ClientInputFile prepareInputFile(Path baseDir, String relativePath, String content, final boolean isTest) throws IOException {
    final var file = new File(baseDir.toFile(), relativePath);
    FileUtils.write(file, content, StandardCharsets.UTF_8);
    return TestUtils.createInputFile(file.toPath(), relativePath, isTest);
  }
}
