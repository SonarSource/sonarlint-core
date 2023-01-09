/*
 * SonarLint Core - ITs - Tests
 * Copyright (C) 2016-2022 SonarSource SA
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
package its;

import com.sonar.orchestrator.OrchestratorExtension;
import its.utils.ItUtils;
import its.utils.OrchestratorUtils;
import its.utils.TestClientInputFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.client.users.CreateRequest;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectedFileMatchingTest extends AbstractConnectedTest {
  private static final String PROJECT_KEY = "com.sonarsource.it.samples:multi-modules-sample";

  @RegisterExtension
  static OrchestratorExtension ORCHESTRATOR = OrchestratorUtils.defaultEnvBuilder()
    .keepBundledPlugins()
    .setServerProperty("sonar.projectCreation.mainBranchName", MAIN_BRANCH_NAME)
    .build();

  @TempDir
  private static Path sonarUserHome;

  private ConnectedSonarLintEngine engine;
  private final List<String> logs = new ArrayList<>();

  @BeforeAll
  static void prepare() {
    newAdminWsClient(ORCHESTRATOR).users().create(new CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"));

    // Project has 5 modules: B, B/B1, B/B2, A, A/A1 and A/A2
    analyzeMavenProject(ORCHESTRATOR, "multi-modules-sample");
  }

  @BeforeEach
  void start() throws IOException {
    engine = new ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration.sonarQubeBuilder()
      .setConnectionId("orchestrator")
      .setSonarLintUserHome(sonarUserHome)
      .setLogOutput((msg, level) -> {
        logs.add(msg);
        System.out.println(msg);
      })
      .setExtraProperties(new HashMap<>())
      .build());
  }

  @AfterEach
  void stop() {
    engine.stop(true);
  }

  @Test
  void should_match_files_when_importing_entire_project() throws IOException {
    engine.updateProject(endpointParams(ORCHESTRATOR), sqHttpClient(), PROJECT_KEY, null);

    // entire project imported in IDE
    var projectDir = Paths.get("projects/multi-modules-sample").toAbsolutePath();
    List<String> ideFiles = ItUtils.collectAllFiles(projectDir).stream()
      .map(f -> projectDir.relativize(f).toString())
      .collect(Collectors.toList());

    var projectBinding = engine.calculatePathPrefixes(PROJECT_KEY, ideFiles);
    assertThat(projectBinding.serverPathPrefix()).isEmpty();
    assertThat(projectBinding.idePathPrefix()).isEmpty();
    engine.downloadAllServerIssuesForFile(endpointParams(ORCHESTRATOR), sqHttpClient(), projectBinding,
      "module_b/module_b1/src/main/java/com/sonar/it/samples/modules/b1/HelloB1.java", MAIN_BRANCH_NAME, null);
    var serverIssues = engine.getServerIssues(projectBinding, MAIN_BRANCH_NAME, "module_b/module_b1/src/main/java/com/sonar/it/samples/modules/b1/HelloB1.java");
    if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 6)) {
      assertThat(serverIssues).isEmpty();
      assertThat(logs).contains("Skip downloading file issues on SonarQube 9.6+");
    } else {
      assertThat(serverIssues).hasSize(2);
    }
    engine.syncServerIssues(endpointParams(ORCHESTRATOR), sqHttpClient(), PROJECT_KEY, MAIN_BRANCH_NAME, null);
    if (!ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 6)) {
      assertThat(logs).contains("Incremental issue sync is not supported. Skipping.");
    }
    serverIssues = engine.getServerIssues(projectBinding, MAIN_BRANCH_NAME, "module_b/module_b1/src/main/java/com/sonar/it/samples/modules/b1/HelloB1.java");
    assertThat(serverIssues).hasSize(2);
  }

  @Test
  void should_match_files_when_importing_module() throws IOException {
    engine.updateProject(endpointParams(ORCHESTRATOR), sqHttpClient(), PROJECT_KEY, null);

    // only module B1 imported in IDE
    var projectDirB1 = Paths.get("projects/multi-modules-sample/module_b/module_b1").toAbsolutePath();
    List<String> ideFiles = ItUtils.collectAllFiles(projectDirB1).stream()
      .map(f -> projectDirB1.relativize(f).toString())
      .collect(Collectors.toList());

    var projectBinding = engine.calculatePathPrefixes(PROJECT_KEY, ideFiles);
    assertThat(projectBinding.serverPathPrefix()).isEqualTo("module_b/module_b1");
    assertThat(projectBinding.idePathPrefix()).isEmpty();
    engine.downloadAllServerIssuesForFile(endpointParams(ORCHESTRATOR), sqHttpClient(), projectBinding,
      "src/main/java/com/sonar/it/samples/modules/b1/HelloB1.java", MAIN_BRANCH_NAME, null);
    var serverIssues = engine.getServerIssues(projectBinding, MAIN_BRANCH_NAME, "src/main/java/com/sonar/it/samples/modules/b1/HelloB1.java");
    if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 6)) {
      assertThat(serverIssues).isEmpty();
      assertThat(logs).contains("Skip downloading file issues on SonarQube 9.6+");
    } else {
      assertThat(serverIssues).hasSize(2);
    }
    engine.syncServerIssues(endpointParams(ORCHESTRATOR), sqHttpClient(), PROJECT_KEY, MAIN_BRANCH_NAME, null);
    if (!ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 6)) {
      assertThat(logs).contains("Incremental issue sync is not supported. Skipping.");
    }
    serverIssues = engine.getServerIssues(projectBinding, MAIN_BRANCH_NAME, "src/main/java/com/sonar/it/samples/modules/b1/HelloB1.java");
    assertThat(serverIssues).hasSize(2);
  }

  @Override
  protected ConnectedAnalysisConfiguration createAnalysisConfiguration(String projectKey, String projectDirName, String filePath, String... properties) throws IOException {
    var projectDir = Paths.get("projects/" + projectDirName).toAbsolutePath();
    List<ClientInputFile> filesToAnalyze = ItUtils.collectAllFiles(projectDir)
      .stream()
      .map(f -> new TestClientInputFile(projectDir, f, false, StandardCharsets.UTF_8))
      .collect(Collectors.toList());

    return ConnectedAnalysisConfiguration.builder()
      .setProjectKey(projectKey)
      .setBaseDir(projectDir)
      .addInputFiles(filesToAnalyze)
      .putAllExtraProperties(toMap(properties))
      .build();
  }

}
