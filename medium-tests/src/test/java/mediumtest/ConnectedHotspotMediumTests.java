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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import mediumtest.fixtures.SonarLintBackendFixture;
import mediumtest.fixtures.SonarLintTestRpcServer;
import mediumtest.fixtures.TestPlugin;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;

import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;
import static testutils.AnalysisUtils.createFile;

class ConnectedHotspotMediumTests {
  private SonarLintTestRpcServer backend;
  private static SonarLintBackendFixture.FakeSonarLintRpcClient client;

  @AfterEach
  void stopBackend() throws ExecutionException, InterruptedException {
    if (backend != null) {
      backend.shutdown().get();
    }
  }

  @Test
  void should_locally_detect_hotspots_when_connected_to_sonarqube_9_9_plus(@TempDir Path baseDir) {
    var inputFile = createFile(baseDir, "Foo.java", "public class Foo {\n" +
      "\n" +
      "  void foo() {\n" +
      "    String password = \"blue\";\n" +
      "  }\n" +
      "}\n");
    client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIG_SCOPE_ID, false, null, inputFile, null, null, true)
      ))
      .build();
    var projectKey = "projectKey";
    var branchName = "main";
    var server = newSonarQubeServer("9.9")
      .withQualityProfile("qpKey", qualityProfile -> qualityProfile
        .withLanguage("java").withActiveRule("java:S2068", activeRule -> activeRule
          .withSeverity(org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity.BLOCKER)
        ))
      .withProject(projectKey,
        project -> project
          .withQualityProfile("qpKey")
          .withBranch(branchName))
      .withPlugin(TestPlugin.JAVA)
      .start();
    backend = newBackend()
      .withFullSynchronization()
      .withSecurityHotspotsEnabled()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, projectKey)
      .withExtraEnabledLanguagesInConnectedMode(JAVA)
      .build(client);
    client.waitForSynchronization();

    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFilesAndTrack(
        new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId, List.of(inputFile.toUri()), Map.of(), true, System.currentTimeMillis()))
      .join();
    assertThat(analysisResult.getFailedAnalysisFiles()).isEmpty();
    await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedHotspotsForScopeIdAsList(CONFIG_SCOPE_ID)).isNotEmpty());

    var hotspot = client.getRaisedHotspotsForScopeIdAsList(CONFIG_SCOPE_ID).get(0);
    assertThat(hotspot.getRuleKey()).isEqualTo("java:S2068");
    assertThat(hotspot.getSeverity()).isEqualTo(IssueSeverity.BLOCKER);
  }

  private static final String CONNECTION_ID = StringUtils.repeat("very-long-id", 30);
  private static final String CONFIG_SCOPE_ID = "configScopeId";

}
