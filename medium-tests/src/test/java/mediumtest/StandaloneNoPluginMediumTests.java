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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.SECURITY_HOTSPOTS;
import static utils.AnalysisUtils.createFile;

class StandaloneNoPluginMediumTests {

  private static final String CONFIG_SCOPE_ID = "configScopeId";

  @SonarLintTest
  void dont_fail_and_detect_language_even_if_no_plugin(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var inputFile = createFile(baseDir, "Foo.java", """
      public class Foo {
      
        void foo() {
          String password = "blue";
        }
      }
      """);
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIG_SCOPE_ID, false, null, inputFile, null, null, true)
      ))
      .build();
    var backend = harness.newBackend()
      .withBackendCapability(SECURITY_HOTSPOTS)
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .start(client);

    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFilesAndTrack(
        new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId, List.of(inputFile.toUri()), Map.of(), true, System.currentTimeMillis()))
      .join();
    assertThat(analysisResult.getFailedAnalysisFiles()).isEmpty();
    await().during(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeIdAsList(CONFIG_SCOPE_ID)).isEmpty());
  }

}
