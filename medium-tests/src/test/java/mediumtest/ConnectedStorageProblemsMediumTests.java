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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.TestPlugin;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.analysis.AnalysisEngine;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.command.AnalyzeCommand;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import testutils.TestUtils;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ConnectedStorageProblemsMediumTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();
  private static final String CONNECTION_ID = "localhost";
  private final String CONFIG_SCOPE_ID = "myProject";

  @Disabled("relies on engine API")
  @Test
  void test_no_storage(@TempDir Path slHome, @TempDir Path baseDir) throws ExecutionException, InterruptedException {
    var fakeClient = newFakeClient().build();
    var backend = newBackend().build(fakeClient);

    backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, UUID.randomUUID(),
      List.of(), Map.of(), false, Instant.now().toEpochMilli())).get();

    assertThat(fakeClient.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID)).isEmpty();
  }

  @Disabled("relies on engine API")
  @Test
  void corrupted_plugin_should_not_prevent_startup(@TempDir Path slHome, @TempDir Path baseDir) throws Exception {
    List<String> logs = new CopyOnWriteArrayList<>();

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

    var inputFile = prepareJavaInputFile(baseDir);

    backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, UUID.randomUUID(),
      List.of(inputFile.uri()), Map.of(), false, Instant.now().toEpochMilli())).get();

    await().untilAsserted(() -> assertThat(logs).contains("Execute Sensor: JavaSensor"));
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
