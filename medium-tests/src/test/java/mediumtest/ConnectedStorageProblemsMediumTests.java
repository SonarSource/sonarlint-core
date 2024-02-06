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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import mediumtest.fixtures.TestPlugin;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.legacy.analysis.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.legacy.analysis.EngineConfiguration;
import org.sonarsource.sonarlint.core.client.legacy.analysis.RawIssue;
import org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import testutils.TestUtils;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static org.assertj.core.api.Assertions.assertThat;
import static testutils.TestUtils.createNoOpIssueListener;

class ConnectedStorageProblemsMediumTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();
  private static final String CONNECTION_ID = "localhost";
  private final String CONFIG_SCOPE_ID = "myProject";

  private SonarLintAnalysisEngine engine;

  @AfterEach
  void stop() {
    engine.stop();
  }

  @Test
  void test_no_storage(@TempDir Path slHome, @TempDir Path baseDir) {
    var config = EngineConfiguration.builder()
      .setSonarLintUserHome(slHome)
      .setLogOutput((msg, level) -> {
      })
      .build();
    engine = new SonarLintAnalysisEngine(config, newBackend().build(), CONNECTION_ID);

    var analysisConfig = AnalysisConfiguration.builder()
      .setBaseDir(baseDir)
      .build();

    var rawIssues = new ArrayList<RawIssue>();
    engine.analyze(analysisConfig, rawIssues::add, null, null, CONFIG_SCOPE_ID);

    assertThat(rawIssues).isEmpty();
  }

  @Test
  void corrupted_plugin_should_not_prevent_startup(@TempDir Path slHome, @TempDir Path baseDir) throws Exception {
    List<String> logs = new CopyOnWriteArrayList<>();

    var config = EngineConfiguration.builder()
      .setSonarLintUserHome(slHome)
      .setLogOutput((m, l) -> logs.add(m))
      .build();
    var backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, storage -> storage
        .withPlugin(TestPlugin.JAVA)
        .withPlugin(SonarLanguage.JS.getPluginKey(), createFakePlugin(), "hash")
        .withProject(CONFIG_SCOPE_ID,
          project -> project.withRuleSet(SonarLanguage.JS.getSonarLanguageKey(),
            ruleSet -> ruleSet.withActiveRule("java:S106", "BLOCKER"))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, CONFIG_SCOPE_ID)
      .withEnabledLanguageInStandaloneMode(org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA)
      .withEnabledLanguageInStandaloneMode(org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JS).build();
    engine = new SonarLintAnalysisEngine(config, backend, CONNECTION_ID);

    var inputFile = prepareJavaInputFile(baseDir);

    engine.analyze(AnalysisConfiguration.builder()
      .setBaseDir(baseDir)
      .addInputFile(inputFile).build(),
      createNoOpIssueListener(), null, null, CONFIG_SCOPE_ID);

    assertThat(logs).contains("Execute Sensor: JavaSensor");
  }

  private ClientInputFile prepareJavaInputFile(Path baseDir) throws IOException {
    return prepareInputFile(baseDir, "Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}",
      false);
  }

  private ClientInputFile prepareInputFile(Path baseDir, String relativePath, String content, final boolean isTest) throws IOException {
    final var file = new File(baseDir.toFile(), relativePath);
    FileUtils.write(file, content, StandardCharsets.UTF_8);
    return TestUtils.createInputFile(file.toPath(), relativePath, isTest);
  }

  private static Path createFakePlugin() {
    try {
      return Files.createTempFile("fakePlugin", "jar");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
