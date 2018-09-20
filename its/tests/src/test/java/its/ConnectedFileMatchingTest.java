/*
 * SonarLint Core - ITs - Tests
 * Copyright (C) 2009-2018 SonarSource SA
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

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.OrchestratorBuilder;
import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.locator.MavenLocation;
import com.sonar.orchestrator.version.Version;
import its.tools.ItUtils;
import its.tools.SonarlintDaemon;
import its.tools.SonarlintProject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.wsclient.user.UserParameters;
import org.sonarqube.ws.client.WsClient;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;

import static its.tools.ItUtils.SONAR_VERSION;
import static org.assertj.core.api.Assertions.assertThat;

public class ConnectedFileMatchingTest extends AbstractConnectedTest {
  private static final String PROJECT_KEY = "com.sonarsource.it.samples:multi-modules-sample";

  @ClassRule
  public static Orchestrator ORCHESTRATOR;

  static {
    OrchestratorBuilder orchestratorBuilder = Orchestrator.builderEnv()
      .setSonarVersion(SONAR_VERSION);

    boolean atLeast67 = ItUtils.isLatestOrDev(SONAR_VERSION) || Version.create(SONAR_VERSION).isGreaterThanOrEquals(6, 7);
    if (atLeast67) {
      orchestratorBuilder
        .addPlugin(MavenLocation.of("org.sonarsource.java", "sonar-java-plugin", "LATEST_RELEASE"));
    } else {
      orchestratorBuilder
        .addPlugin(MavenLocation.of("org.sonarsource.java", "sonar-java-plugin", "4.15.0.12310"));
    }
    ORCHESTRATOR = orchestratorBuilder.build();
  }

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public SonarlintDaemon daemon = new SonarlintDaemon();

  @Rule
  public SonarlintProject clientTools = new SonarlintProject();

  @Rule
  public ExpectedException exception = ExpectedException.none();

  private static WsClient adminWsClient;
  private static Path sonarUserHome;
  private ConnectedSonarLintEngine engine;
  private List<String> logs = new ArrayList<>();

  @BeforeClass
  public static void prepare() {
    ORCHESTRATOR.getServer().adminWsClient().userClient()
      .create(UserParameters.create()
        .login(SONARLINT_USER)
        .password(SONARLINT_PWD)
        .passwordConfirmation(SONARLINT_PWD)
        .name("SonarLint"));

    // Project has 5 modules: B, B/B1, B/B2, A, A/A1 and A/A2
    analyzeMavenProject("multi-modules-sample");
  }

  @Before
  public void start() throws IOException {
    sonarUserHome = temp.newFolder().toPath();
    engine = new ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration.builder()
      .setServerId("orchestrator")
      .setSonarLintUserHome(sonarUserHome)
      .setLogOutput((msg, level) -> logs.add(msg))
      .setExtraProperties(new HashMap<>())
      .build());
  }

  @After
  public void stop() {
    engine.stop(true);
  }

  @Test
  public void should_match_files_when_importing_entire_project() throws IOException {
    engine.update(getServerConfig(), null);
    engine.updateProject(getServerConfig(), PROJECT_KEY, null);

    // entire project imported in IDE
    Path projectDir = Paths.get("projects/multi-modules-sample").toAbsolutePath();
    List<String> ideFiles = clientTools.collectAllFiles(projectDir).stream()
      .map(f -> projectDir.relativize(f).toString())
      .collect(Collectors.toList());

    ProjectBinding projectBinding = engine.calculatePathPrefixes(PROJECT_KEY, ideFiles);
    assertThat(projectBinding.sqPathPrefix()).isEmpty();
    assertThat(projectBinding.idePathPrefix()).isEmpty();
    List<ServerIssue> serverIssues = engine.downloadServerIssues(getServerConfig(), projectBinding,
      "module_b/module_b1/src/main/java/com/sonar/it/samples/modules/b1/HelloB1.java");
    assertThat(serverIssues).hasSize(2);
  }

  @Test
  public void should_match_files_when_importing_module() throws IOException {
    engine.update(getServerConfig(), null);
    engine.updateProject(getServerConfig(), PROJECT_KEY, null);

    // only module B1 imported in IDE
    Path projectDirB1 = Paths.get("projects/multi-modules-sample/module_b/module_b1").toAbsolutePath();
    List<String> ideFiles = clientTools.collectAllFiles(projectDirB1).stream()
      .map(f -> projectDirB1.relativize(f).toString())
      .collect(Collectors.toList());

    ProjectBinding projectBinding = engine.calculatePathPrefixes(PROJECT_KEY, ideFiles);
    assertThat(projectBinding.sqPathPrefix()).isEqualTo("module_b/module_b1");
    assertThat(projectBinding.idePathPrefix()).isEmpty();
    List<ServerIssue> serverIssues = engine.downloadServerIssues(getServerConfig(), projectBinding,
      "src/main/java/com/sonar/it/samples/modules/b1/HelloB1.java");
    assertThat(serverIssues).hasSize(2);
  }

  private ServerConfiguration getServerConfig() {
    return ServerConfiguration.builder()
      .url(ORCHESTRATOR.getServer().getUrl())
      .userAgent("SonarLint ITs")
      .credentials(SONARLINT_USER, SONARLINT_PWD)
      .build();
  }

  protected ConnectedAnalysisConfiguration createAnalysisConfiguration(String projectKey, String projectDirName, String filePath, String... properties) throws IOException {
    Path projectDir = Paths.get("projects/" + projectDirName).toAbsolutePath();
    List<ClientInputFile> filesToAnalyze = clientTools.collectAllFiles(projectDir)
      .stream()
      .map(f -> new TestClientInputFile(projectDir, f, false, StandardCharsets.UTF_8))
      .collect(Collectors.toList());

    return new ConnectedAnalysisConfiguration(projectKey,
      projectDir,
      t.newFolder().toPath(),
      filesToAnalyze,
      toMap(properties));
  }

  private static void analyzeMavenProject(String projectDirName) {
    Path projectDir = Paths.get("projects/" + projectDirName).toAbsolutePath();
    Path pom = projectDir.resolve("pom.xml");
    ORCHESTRATOR.executeBuild(MavenBuild.create(pom.toFile()).setCleanPackageSonarGoals());
  }
}
