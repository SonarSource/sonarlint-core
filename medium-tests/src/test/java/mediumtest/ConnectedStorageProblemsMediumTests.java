/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2025 SonarSource SA
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.TestPlugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static utils.AnalysisUtils.createFile;

class ConnectedStorageProblemsMediumTests {
  private static final String CONNECTION_ID = "localhost";
  private static final String CONFIG_SCOPE_ID = "myProject";

  @SonarLintTest
  void corrupted_plugin_should_not_prevent_startup(SonarLintTestHarness harness, @TempDir Path baseDir) throws Exception {
    var inputFile = createFile(baseDir, "Foo.java", """
      public class Foo {
        public void foo() {
          int x;
          System.out.println("Foo");
          System.out.println("Foo"); //NOSONAR
        }
      }""");
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIG_SCOPE_ID, false, null, inputFile, null, null, true)
      ))
      .build();
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server, storage -> storage
        .withPlugin(TestPlugin.JAVA)
        .withPlugin(SonarLanguage.JS.getPluginKey(), createFakePlugin(), "hash")
        .withProject(CONFIG_SCOPE_ID,
          project -> project.withMainBranch("main").withRuleSet(SonarLanguage.JS.getSonarLanguageKey(),
            ruleSet -> ruleSet.withActiveRule("java:S106", "BLOCKER"))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, CONFIG_SCOPE_ID)
      .withEnabledLanguageInStandaloneMode(org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA)
      .withEnabledLanguageInStandaloneMode(org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JS).start(client);

    backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, UUID.randomUUID(),
      List.of(inputFile.toUri()), Map.of(), false, Instant.now().toEpochMilli())).get();

    await().untilAsserted(() -> assertThat(client.getLogMessages()).contains("Execute Sensor: JavaSensor"));
  }

  private static Path createFakePlugin() {
    try {
      return Files.createTempFile("fakePlugin", "jar");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
