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
import com.sonar.orchestrator.container.Edition;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.locator.MavenLocation;
import com.sonar.orchestrator.version.Version;
import its.tools.ItUtils;
import java.nio.file.Path;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
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
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.exceptions.SonarLintWrappedException;

import static its.tools.ItUtils.SONAR_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

public class LicenseTest extends AbstractConnectedTest {
  private static final String PROJECT_KEY_COBOL = "sample-cobol";

  private static Orchestrator ORCHESTRATOR;

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException exception = ExpectedException.none();

  private static WsClient adminWsClient;
  private static Path sonarUserHome;

  private ConnectedSonarLintEngine engine;

  @BeforeClass
  public static void prepare() throws Exception {
    // orchestrator automatically adds dev license plugin when older than SQ 7.2,
    // and dev license plugin requires at least SQ 6.7
    assumeTrue(ItUtils.isLatestOrDev(SONAR_VERSION) || Version.create(SONAR_VERSION).isGreaterThanOrEquals(6, 7));

    OrchestratorBuilder builder = Orchestrator.builderEnv()
      .setSonarVersion(SONAR_VERSION)
      .setEdition(Edition.ENTERPRISE)
      .restoreProfileAtStartup(FileLocation.ofClasspath("/cobol-sonarlint.xml"));

    builder.addPlugin(MavenLocation.of("com.sonarsource.cobol", "sonar-cobol-plugin", "4.3.0.3019"));
    ORCHESTRATOR = builder.build();
    ORCHESTRATOR.start();
    adminWsClient = ConnectedModeTest.newAdminWsClient(ORCHESTRATOR);
    ORCHESTRATOR.getServer().getAdminWsClient().create(new PropertyCreateQuery("sonar.forceAuthentication", "true"));
    sonarUserHome = temp.newFolder().toPath();

    removeGroupPermission("anyone", "scan");

    ORCHESTRATOR.getServer().adminWsClient().userClient()
      .create(UserParameters.create().login(SONARLINT_USER).password(SONARLINT_PWD).passwordConfirmation(SONARLINT_PWD).name("SonarLint"));

    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY_COBOL, "Sample Cobol");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_COBOL, "cobol", "SonarLint IT Cobol");
  }

  @AfterClass
  public static void afterClass() {
    if (ORCHESTRATOR != null) {
      ORCHESTRATOR.stop();
    }
  }

  @Before
  public void start() {
    FileUtils.deleteQuietly(sonarUserHome.toFile());
    engine = new ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration.builder()
      .setServerId("orchestrator")
      .setSonarLintUserHome(sonarUserHome)
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
  public void analysisNoLicense() throws Exception {
    Assume.assumeFalse(ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(6, 7));
    ORCHESTRATOR.clearLicense();
    updateGlobal();
    updateModule(PROJECT_KEY_COBOL);

    exception.expect(SonarLintWrappedException.class);
    exception.expectMessage("No license for cobol");
    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_COBOL, PROJECT_KEY_COBOL, "src/Custmnt2.cbl",
      "sonar.cobol.file.suffixes", "cbl"), issueListener, null, null);
  }

  @Test
  public void analysisCobol() throws Exception {
    ORCHESTRATOR.activateLicense();
    updateGlobal();
    updateModule(PROJECT_KEY_COBOL);
    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_COBOL, PROJECT_KEY_COBOL, "src/Custmnt2.cbl",
      "sonar.cobol.file.suffixes", "cbl"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  private void updateModule(String projectKey) {
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
