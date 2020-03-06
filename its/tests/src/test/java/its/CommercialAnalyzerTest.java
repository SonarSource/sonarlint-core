/*
 * SonarLint Core - ITs - Tests
 * Copyright (C) 2009-2020 SonarSource SA
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
import com.sonar.orchestrator.container.Edition;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.locator.MavenLocation;
import its.tools.ItUtils;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.wsclient.services.PropertyCreateQuery;
import org.sonar.wsclient.user.UserParameters;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.permission.RemoveGroupWsRequest;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.Language;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;

import static its.tools.ItUtils.SONAR_VERSION;
import static org.assertj.core.api.Assertions.assertThat;

public class CommercialAnalyzerTest extends AbstractConnectedTest {
  private static final String PROJECT_KEY_COBOL = "sample-cobol";
  private static final String PROJECT_KEY_C = "sample-c";
  private static final String PROJECT_KEY_TSQL = "sample-tsql";
  private static final String PROJECT_KEY_APEX = "sample-apex";

  @ClassRule
  public static Orchestrator ORCHESTRATOR = Orchestrator.builderEnv()
    .setSonarVersion(SONAR_VERSION)
    .setEdition(Edition.ENTERPRISE)
    .restoreProfileAtStartup(FileLocation.ofClasspath("/c-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/cobol-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/tsql-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/apex-sonarlint.xml"))
    .addPlugin(MavenLocation.of("com.sonarsource.cpp", "sonar-cfamily-plugin", ItUtils.cppVersion))
    .addPlugin(MavenLocation.of("com.sonarsource.cobol", "sonar-cobol-plugin", ItUtils.cobolVersion))
    .addPlugin(MavenLocation.of("com.sonarsource.tsql", "sonar-tsql-plugin", ItUtils.tsqlVersion))
    .addPlugin(MavenLocation.of("com.sonarsource.slang", "sonar-apex-plugin", ItUtils.apexVersion))
    .build();

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException exception = ExpectedException.none();

  private static WsClient adminWsClient;
  private static Path sonarUserHome;

  private ConnectedSonarLintEngine engine;

  @BeforeClass
  public static void prepare() throws Exception {
    adminWsClient = ConnectedModeTest.newAdminWsClient(ORCHESTRATOR);
    ORCHESTRATOR.getServer().getAdminWsClient().create(new PropertyCreateQuery("sonar.forceAuthentication", "true"));
    sonarUserHome = temp.newFolder().toPath();

    removeGroupPermission("anyone", "scan");

    ORCHESTRATOR.getServer().adminWsClient().userClient()
      .create(UserParameters.create().login(SONARLINT_USER).password(SONARLINT_PWD).passwordConfirmation(SONARLINT_PWD).name("SonarLint"));

    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY_C, "Sample C");
    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY_COBOL, "Sample Cobol");
    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY_TSQL, "Sample TSQL");
    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY_APEX, "Sample APEX");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_C, "c", "SonarLint IT C");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_COBOL, "cobol", "SonarLint IT Cobol");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_TSQL, "tsql", "SonarLint IT TSQL");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_APEX, "apex", "SonarLint IT APEX");
  }

  @Before
  public void start() {
    FileUtils.deleteQuietly(sonarUserHome.toFile());
    engine = new ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration.builder()
      .setServerId("orchestrator")
      .setSonarLintUserHome(sonarUserHome)
      .addEnabledLanguage(Language.COBOL)
      .addEnabledLanguage(Language.C)
      .addEnabledLanguage(Language.TSQL)
      .addEnabledLanguage(Language.APEX)
      .setLogOutput((msg, level) -> System.out.println(msg))
      .build());
  }

  @After
  public void stop() {
    try {
      engine.stop(true);
    } catch (Exception e) {
      // Ignore
    }
  }

  @Test
  public void analysisC() throws Exception {
    updateGlobal();
    updateProject(PROJECT_KEY_C);
    SaveIssueListener issueListener = new SaveIssueListener();

    File buildWrapperOutput = temp.newFolder();
    FileUtils.write(
      new File(buildWrapperOutput, "build-wrapper-dump.json"),
      "{\"version\":0,\"captures\":[" +
        "{" +
        "\"compiler\": \"clang\"," +
        "\"executable\": \"compiler\"," +
        "\"stdout\": \"#define __STDC_VERSION__ 201112L\n\"," +
        "\"stderr\": \"\"" +
        "}," +
        "{" +
        "\"compiler\": \"clang\"," +
        "\"executable\": \"compiler\"," +
        "\"stdout\": \"#define __cplusplus 201703L\n\"," +
        "\"stderr\": \"\"" +
        "}," +
        "{\"compiler\":\"clang\",\"cwd\":\"" +
        Paths.get("projects/" + PROJECT_KEY_C).toAbsolutePath().toString().replace("\\", "\\\\") +
        "\",\"executable\":\"compiler\",\"cmd\":[\"cc\",\"src/file.c\"]}]}",
      StandardCharsets.UTF_8);

    ConnectedAnalysisConfiguration analysisConfiguration = createAnalysisConfiguration(PROJECT_KEY_C, PROJECT_KEY_C, "src/file.c",
      "sonar.cfamily.build-wrapper-output", buildWrapperOutput.getAbsolutePath());

    engine.analyze(analysisConfiguration, issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void analysisCobol() throws Exception {
    updateGlobal();
    updateProject(PROJECT_KEY_COBOL);
    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_COBOL, PROJECT_KEY_COBOL, "src/Custmnt2.cbl",
      "sonar.cobol.file.suffixes", "cbl"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void analysisTsql() throws IOException {
    updateGlobal();
    updateProject(PROJECT_KEY_TSQL);

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_TSQL, PROJECT_KEY_TSQL, "src/file.tsql"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  @Test
  public void analysisApex() throws IOException {
    updateGlobal();
    updateProject(PROJECT_KEY_APEX);

    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_APEX, PROJECT_KEY_APEX, "src/file.cls"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  private void updateProject(String projectKey) {
    engine.updateProject(ServerConfiguration.builder()
      .url(ORCHESTRATOR.getServer().getUrl())
      .userAgent("SonarLint ITs")
      .credentials(SONARLINT_USER, SONARLINT_PWD)
      .build(), projectKey, null);
  }

  private void updateGlobal() {
    engine.update(ServerConfiguration.builder()
      .url(ORCHESTRATOR.getServer().getUrl())
      .userAgent("SonarLint ITs")
      .credentials(SONARLINT_USER, SONARLINT_PWD)
      .build(), null);
  }

  private static void removeGroupPermission(String groupName, String permission) {
    adminWsClient.permissions().removeGroup(new RemoveGroupWsRequest()
      .setGroupName(groupName)
      .setPermission(permission));
  }
}
