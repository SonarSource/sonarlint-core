/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package mediumtest.analysis;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.TestPlugin;

import static org.assertj.core.api.Assertions.assertThat;
import static utils.AnalysisUtils.createFile;

class ArchitectureAnalysisMediumTests {

  private static final String CONFIG_SCOPE_ID = "ARCHITECTURE_IT";

  private static Map<String, String> architectureAnalysisProperties() {
    return Map.of(
      "sonar.architecture.enable", "true",
      "sonar.architecture.preview.available", "true");
  }

  @SonarLintTest
  void it_should_run_java_analysis_with_architecture_plugins(SonarLintTestHarness harness, @TempDir Path tempDir) throws Exception {
    var filePath = createFile(tempDir, "Hello.java", """
      class Hello {
        void m() {}
      }
      """);
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, tempDir, List.of(
        new ClientFileDto(filePath.toUri(), tempDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();

    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPlugin(TestPlugin.JAVA)
      .withStandaloneEmbeddedPlugin(TestPlugin.ARCHITECTURE)
      .withStandaloneEmbeddedPlugin(TestPlugin.ARCHITECTURE_JAVA_FRONTEND)
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .start(client);

    var analysisId = UUID.randomUUID();
    var result = backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId, List.of(filePath.toUri()),
        architectureAnalysisProperties(), true))
      .get();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
  }

  @SonarLintTest
  void it_should_run_two_sequential_analyses_without_failure(SonarLintTestHarness harness, @TempDir Path tempDir) throws Exception {
    var a = createFile(tempDir, "A.java", "class A { void m() {} }\n");
    var b = createFile(tempDir, "B.java", "class B { void m() {} }\n");
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, tempDir, List.of(
        new ClientFileDto(a.toUri(), tempDir.relativize(a), CONFIG_SCOPE_ID, false, null, a, null, null, true),
        new ClientFileDto(b.toUri(), tempDir.relativize(b), CONFIG_SCOPE_ID, false, null, b, null, null, true)))
      .build();

    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPlugin(TestPlugin.JAVA)
      .withStandaloneEmbeddedPlugin(TestPlugin.ARCHITECTURE)
      .withStandaloneEmbeddedPlugin(TestPlugin.ARCHITECTURE_JAVA_FRONTEND)
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .start(client);

    var props = architectureAnalysisProperties();
    var r1 = backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, UUID.randomUUID(), List.of(a.toUri()), props, true))
      .get();
    assertThat(r1.getFailedAnalysisFiles()).isEmpty();

    var r2 = backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, UUID.randomUUID(), List.of(b.toUri()), props, true))
      .get();
    assertThat(r2.getFailedAnalysisFiles()).isEmpty();
  }
}
