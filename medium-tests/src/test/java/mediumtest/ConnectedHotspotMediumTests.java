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
package mediumtest;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.TestPlugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.FULL_SYNCHRONIZATION;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.SECURITY_HOTSPOTS;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;
import static utils.AnalysisUtils.createFile;

class ConnectedHotspotMediumTests {

  @SonarLintTest
  void should_locally_detect_hotspots_when_connected_to_sonarqube(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var inputFile = createFile(baseDir, "Foo.java", """
      public class Foo {
        void foo() {
          String password = "blue";
        }
      }
      """);
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIG_SCOPE_ID, false, null, inputFile, null, null, true)))
      .build();
    var projectKey = "projectKey";
    var branchName = "main";
    var server = harness.newFakeSonarQubeServer("9.9")
      .withQualityProfile("qpKey", qualityProfile -> qualityProfile
        .withLanguage("java").withActiveRule("java:S2068", activeRule -> activeRule
          .withSeverity(org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity.BLOCKER)))
      .withProject(projectKey,
        project -> project
          .withQualityProfile("qpKey")
          .withBranch(branchName, branch -> branch.withHotspot("key", hotspot -> hotspot.withFilePath("Foo.java"))))
      .withPlugin(TestPlugin.JAVA)
      .start();
    var backend = harness.newBackend()
      .withBackendCapability(FULL_SYNCHRONIZATION, SECURITY_HOTSPOTS)
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, projectKey)
      .withExtraEnabledLanguagesInConnectedMode(JAVA)
      .start(client);
    client.waitForSynchronization();

    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFilesAndTrack(
      new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId, List.of(inputFile.toUri()), Map.of(), true, System.currentTimeMillis()))
      .join();
    assertThat(analysisResult.getFailedAnalysisFiles()).isEmpty();
    await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedHotspotsForScopeIdAsList(CONFIG_SCOPE_ID)).isNotEmpty());

    var hotspot = client.getRaisedHotspotsForScopeIdAsList(CONFIG_SCOPE_ID).get(0);
    assertThat(hotspot.getRuleKey()).isEqualTo("java:S2068");
    assertThat(hotspot.getSeverityMode().isLeft()).isTrue();
    assertThat(hotspot.getSeverityMode().getLeft().getSeverity()).isEqualTo(IssueSeverity.BLOCKER);
  }

  private static final String CONNECTION_ID = StringUtils.repeat("very-long-id", 20);
  private static final String CONFIG_SCOPE_ID = "configScopeId";

}
