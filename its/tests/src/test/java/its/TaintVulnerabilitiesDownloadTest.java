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
/*


Optimisations:
  - allow to fetch taint vulnerabilities separately: new parameter repoKeys=javasecurity,pythonsecurity,...
  - only fetch secondary locations for hotspots and taint vulnerabilities: new parameter  * SonarLint Core - ITs - Tests
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

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.container.Edition;
import com.sonar.orchestrator.locator.FileLocation;
import its.tools.SonarlintProject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarqube.ws.client.issues.SearchRequest;
import org.sonarqube.ws.client.users.CreateRequest;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;

import static its.tools.ItUtils.SONAR_VERSION;
import static org.assertj.core.api.Assertions.assertThat;

public class TaintVulnerabilitiesDownloadTest extends AbstractConnectedTest {
  private static final String PROJECT_KEY = "sample-java-taint";

  @Rule
  public Orchestrator ORCHESTRATOR = Orchestrator.builderEnv()
    .defaultForceAuthentication()
    .setSonarVersion(SONAR_VERSION)
    .setEdition(Edition.DEVELOPER)
    .activateLicense()
    .keepBundledPlugins()
    .restoreProfileAtStartup(FileLocation.ofClasspath("/java-sonarlint-with-taint.xml"))
    .build();

  @Rule
  public SonarlintProject clientTools = new SonarlintProject();

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private static Path sonarUserHome;

  private ConnectedSonarLintEngine engine;
  private final List<String> logs = new ArrayList<>();

  @Before
  public void prepare() throws Exception {
    var adminWsClient = newAdminWsClient(ORCHESTRATOR);
    adminWsClient.users().create(new CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"));

    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY, "Java With Taint Vulnerabilities");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY, "java", "SonarLint Taint Java");

    analyzeMavenProject(PROJECT_KEY);

    // Ensure a vulnerability has been reported on server side
    assertThat(
      adminWsClient.issues().search(new SearchRequest().setTypes(List.of("VULNERABILITY")).setComponentKeys(List.of(PROJECT_KEY))).getIssuesList())
        .isNotEmpty();

    sonarUserHome = temp.newFolder().toPath();
    engine = new ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration.sonarQubeBuilder()
      .setConnectionId("orchestrator")
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
  public void download_all_issues_include_taint_vulnerabilities_and_code_snippets() {
    ProjectBinding projectBinding = new ProjectBinding(PROJECT_KEY, "", "");

    engine.update(endpointParams(ORCHESTRATOR), sqHttpClient(), null);
    engine.updateProject(endpointParams(ORCHESTRATOR), sqHttpClient(), PROJECT_KEY, null);

    // Reload file issues to get taint
    engine.downloadAllServerIssuesForFile(endpointParams(ORCHESTRATOR), sqHttpClient(), projectBinding, "src/main/java/foo/DbHelper.java", "master", null);

    var sinkIssues = engine.getServerTaintIssues(projectBinding, "master", "src/main/java/foo/DbHelper.java");
    assertThat(sinkIssues).hasSize(1);

    var taintIssue = sinkIssues.get(0);
    assertThat(taintIssue.getCodeSnippet()).isEqualTo("statement.executeQuery(query)");
    assertThat(taintIssue.getFlows()).isNotEmpty();
    var flow = taintIssue.getFlows().get(0);
    assertThat(flow.locations()).isNotEmpty();
    assertThat(flow.locations().get(0).getCodeSnippet()).isEqualTo("statement.executeQuery(query)");
    assertThat(flow.locations().get(flow.locations().size() - 1).getCodeSnippet()).isIn("request.getParameter(\"user\")", "request.getParameter(\"pass\")");
  }

  private void analyzeMavenProject(String projectDirName) {
    var projectDir = Paths.get("projects/" + projectDirName).toAbsolutePath();
    var pom = projectDir.resolve("pom.xml");
    ORCHESTRATOR.executeBuild(MavenBuild.create(pom.toFile())
      .setCleanPackageSonarGoals()
      .setProperty("sonar.projectKey", projectDirName)
      .setProperty("sonar.login", com.sonar.orchestrator.container.Server.ADMIN_LOGIN)
      .setProperty("sonar.password", com.sonar.orchestrator.container.Server.ADMIN_PASSWORD));
  }
}
