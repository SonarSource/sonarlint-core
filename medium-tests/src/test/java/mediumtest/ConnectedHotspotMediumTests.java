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
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.SonarLintBackendFixture;
import mediumtest.fixtures.SonarLintTestRpcServer;
import mediumtest.fixtures.TestPlugin;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import testutils.MockWebServerExtensionWithProtobuf;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static testutils.AnalysisUtils.analyzeAndGetAllHotspotsByFile;
import static testutils.AnalysisUtils.analyzeFileAndGetHotspot;
import static testutils.AnalysisUtils.createFile;

class ConnectedHotspotMediumTests {

  private SonarLintTestRpcServer backend;
  private Path filePathToAnalyze;
  private SonarLintBackendFixture.FakeSonarLintRpcClient client;

  @AfterEach
  void stopBackend() throws ExecutionException, InterruptedException {
    if (backend != null) {
      backend.shutdown().get();
    }
  }

  @Test
  void should_not_locally_detect_hotspots_when_connected_to_a_never_synced_server(@TempDir Path baseDir) {
    createStorage(baseDir, null);

    var raisedIssuesByFile = analyzeAndGetAllHotspotsByFile(filePathToAnalyze.toUri(), client, backend, CONFIG_SCOPE_ID);

    assertThat(raisedIssuesByFile).isEmpty();
  }

  @Test
  void should_not_locally_detect_hotspots_when_connected_to_a_server_not_permitting_hotspot_tracking(@TempDir Path baseDir) {
    createStorage(baseDir, "9.6");

    var raisedIssuesByFile = analyzeAndGetAllHotspotsByFile(filePathToAnalyze.toUri(), client, backend, CONFIG_SCOPE_ID);

    assertThat(raisedIssuesByFile).isEmpty();
  }

  @Test
  void should_locally_detect_hotspots_when_connected_to_sonarqube_9_7_plus(@TempDir Path baseDir) {
    createStorage(baseDir, "9.7");

    var raisedIssue = analyzeFileAndGetHotspot(filePathToAnalyze.toUri(), client, backend, CONFIG_SCOPE_ID);

    assertThat(raisedIssue).extracting(RaisedFindingDto::getRuleKey, RaisedFindingDto::getTextRange, RaisedFindingDto::getSeverity)
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly("java:S5852", new TextRangeDto(3, 28, 3, 35), IssueSeverity.BLOCKER);
  }

  private void createStorage(Path baseDir, String serverVersion) {
    filePathToAnalyze = prepareJavaInputFile(baseDir);
    client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir,
        List.of(new ClientFileDto(filePathToAnalyze.toUri(), baseDir.relativize(filePathToAnalyze), CONFIG_SCOPE_ID, false, null, filePathToAnalyze, null, null)))
      .build();
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
      .build(client);
  }

  private Path prepareJavaInputFile(Path baseDir) {
    return createFile(baseDir, "Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    java.util.regex.Pattern.compile(\".*PATH=\\\"(.*)\\\"; export PATH;.*\");\n"
        + "  }\n"
        + "}");
  }

  @RegisterExtension
  private final MockWebServerExtensionWithProtobuf mockWebServer = new MockWebServerExtensionWithProtobuf();

  private static final String CONNECTION_ID = StringUtils.repeat("very-long-id", 30);
  private static final String CONFIG_SCOPE_ID = "configScopeId";
  private static final String JAVA_MODULE_KEY = "test-project-2";

}
