/*
 * SonarLint Core - ITs - Tests
 * Copyright (C) 2016-2021 SonarSource SA
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
import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.locator.MavenLocation;
import its.tools.ItUtils;
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
import org.sonarqube.ws.client.users.CreateRequest;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;

import static its.tools.ItUtils.SONAR_VERSION;
import static org.assertj.core.api.Assertions.assertThat;

public class ConnectedFileMatchingTest extends AbstractConnectedTest {
  private static final String PROJECT_KEY = "com.sonarsource.it.samples:multi-modules-sample";

  @ClassRule
  public static Orchestrator ORCHESTRATOR = Orchestrator.builderEnv()
    .defaultForceAuthentication()
    .setSonarVersion(SONAR_VERSION)
    .addPlugin(MavenLocation.of("org.sonarsource.java", "sonar-java-plugin", ItUtils.javaVersion))
    .build();

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public SonarlintProject clientTools = new SonarlintProject();

  @Rule
  public ExpectedException exception = ExpectedException.none();

  private static Path sonarUserHome;

  private ConnectedSonarLintEngine engine;
  private final List<String> logs = new ArrayList<>();

  @BeforeClass
  public static void prepare() {
    newAdminWsClient(ORCHESTRATOR).users().create(new CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"));

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
    engine.update(endpoint(ORCHESTRATOR), sqHttpClient(), null);
    engine.updateProject(endpoint(ORCHESTRATOR), sqHttpClient(), PROJECT_KEY, null);

    // entire project imported in IDE
    Path projectDir = Paths.get("projects/multi-modules-sample").toAbsolutePath();
    List<String> ideFiles = clientTools.collectAllFiles(projectDir).stream()
      .map(f -> projectDir.relativize(f).toString())
      .collect(Collectors.toList());

    ProjectBinding projectBinding = engine.calculatePathPrefixes(PROJECT_KEY, ideFiles);
    assertThat(projectBinding.sqPathPrefix()).isEmpty();
    assertThat(projectBinding.idePathPrefix()).isEmpty();
    List<ServerIssue> serverIssues = engine.downloadServerIssues(endpoint(ORCHESTRATOR), sqHttpClient(), projectBinding,
      "module_b/module_b1/src/main/java/com/sonar/it/samples/modules/b1/HelloB1.java", null);
    assertThat(serverIssues).hasSize(2);
  }

  @Test
  public void should_match_files_when_importing_module() throws IOException {
    engine.update(endpoint(ORCHESTRATOR), sqHttpClient(), null);
    engine.updateProject(endpoint(ORCHESTRATOR), sqHttpClient(), PROJECT_KEY, null);

    // only module B1 imported in IDE
    Path projectDirB1 = Paths.get("projects/multi-modules-sample/module_b/module_b1").toAbsolutePath();
    List<String> ideFiles = clientTools.collectAllFiles(projectDirB1).stream()
      .map(f -> projectDirB1.relativize(f).toString())
      .collect(Collectors.toList());

    ProjectBinding projectBinding = engine.calculatePathPrefixes(PROJECT_KEY, ideFiles);
    assertThat(projectBinding.sqPathPrefix()).isEqualTo("module_b/module_b1");
    assertThat(projectBinding.idePathPrefix()).isEmpty();
    List<ServerIssue> serverIssues = engine.downloadServerIssues(endpoint(ORCHESTRATOR), sqHttpClient(), projectBinding,
      "src/main/java/com/sonar/it/samples/modules/b1/HelloB1.java", null);
    assertThat(serverIssues).hasSize(2);
  }

  @Override
  protected ConnectedAnalysisConfiguration createAnalysisConfiguration(String projectKey, String projectDirName, String filePath, String... properties) throws IOException {
    Path projectDir = Paths.get("projects/" + projectDirName).toAbsolutePath();
    List<ClientInputFile> filesToAnalyze = clientTools.collectAllFiles(projectDir)
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

  private static void analyzeMavenProject(String projectDirName) {
    Path projectDir = Paths.get("projects/" + projectDirName).toAbsolutePath();
    Path pom = projectDir.resolve("pom.xml");
    ORCHESTRATOR.executeBuild(MavenBuild.create(pom.toFile())
      .setCleanPackageSonarGoals()
      .setProperty("sonar.login", com.sonar.orchestrator.container.Server.ADMIN_LOGIN)
      .setProperty("sonar.password", com.sonar.orchestrator.container.Server.ADMIN_PASSWORD));
  }
}
