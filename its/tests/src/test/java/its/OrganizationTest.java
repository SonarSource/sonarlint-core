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
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.locator.MavenLocation;
import com.sonar.orchestrator.version.Version;
import its.tools.ItUtils;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.wsclient.user.UserParameters;
import org.sonarqube.ws.client.PostRequest;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.WsRequest;
import org.sonarqube.ws.client.WsResponse;
import org.sonarqube.ws.client.organization.CreateWsRequest;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.WsHelperImpl;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine.State;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteOrganization;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.WsHelper;

import static its.tools.ItUtils.SONAR_VERSION;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

public class OrganizationTest extends AbstractConnectedTest {

  private static WsClient adminWsClient;
  private static final String ORGANIZATION = "test-org";
  private static final String PROJECT_KEY_JAVA = "sample-java";

  @ClassRule
  public static Orchestrator ORCHESTRATOR;

  static {
    OrchestratorBuilder orchestratorBuilder = Orchestrator.builderEnv()
      .setSonarVersion(SONAR_VERSION)
      .setServerProperty("sonar.sonarcloud.enabled", "true");

    boolean atLeast67 = ItUtils.isLatestOrDev(SONAR_VERSION) || Version.create(SONAR_VERSION).isGreaterThanOrEquals(6, 7);
    if (atLeast67) {
      orchestratorBuilder
        .addPlugin(MavenLocation.of("org.sonarsource.java", "sonar-java-plugin", "LATEST_RELEASE"));
    } else {
      orchestratorBuilder
        .addPlugin(MavenLocation.of("org.sonarsource.java", "sonar-java-plugin", "4.15.0.12310"));
    }
    ORCHESTRATOR = orchestratorBuilder
      .restoreProfileAtStartup(FileLocation.ofClasspath("/java-sonarlint.xml"))
      .build();
  }

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException exception = ExpectedException.none();

  private static Path sonarUserHome;

  private ConnectedSonarLintEngine engineOnTestOrg;
  private ConnectedSonarLintEngine engineOnDefaultOrg;

  @BeforeClass
  public static void prepare() throws Exception {
    assumeTrue(ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(6, 3));
    adminWsClient = ConnectedModeTest.newAdminWsClient(ORCHESTRATOR);
    sonarUserHome = temp.newFolder().toPath();

    ORCHESTRATOR.getServer().adminWsClient().userClient()
      .create(UserParameters.create()
        .login(SONARLINT_USER)
        .password(SONARLINT_PWD)
        .passwordConfirmation(SONARLINT_PWD)
        .name("SonarLint"));

    enableOrganizationsSupport();
    createOrganization();

    ORCHESTRATOR.getServer().adminWsClient().post("/api/projects/create",
      "key", PROJECT_KEY_JAVA,
      "name", "Sample Java",
      "organization", ORGANIZATION);
  }

  @Before
  public void start() {
    FileUtils.deleteQuietly(sonarUserHome.toFile());
    engineOnTestOrg = new ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration.builder()
      .setServerId("orchestrator-test-org")
      .setSonarLintUserHome(sonarUserHome)
      .setLogOutput((msg, level) -> System.out.println(msg))
      .build());
    engineOnDefaultOrg = new ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration.builder()
      .setServerId("orchestrator-default-org")
      .setSonarLintUserHome(sonarUserHome)
      .setLogOutput((msg, level) -> System.out.println(msg))
      .build());
    assertThat(engineOnTestOrg.getGlobalStorageStatus()).isNull();
    assertThat(engineOnTestOrg.getState()).isEqualTo(State.NEVER_UPDATED);
  }

  @After
  public void stop() {
    try {
      engineOnTestOrg.stop(true);
    } catch (Exception e) {
      // Ignore
    }
    try {
      engineOnDefaultOrg.stop(true);
    } catch (Exception e) {
      // Ignore
    }
  }

  @Test
  public void testConnection() {
    assertThat(new WsHelperImpl().validateConnection(getServerConfigForOrg(ORGANIZATION)).success()).isTrue();
    assertThat(new WsHelperImpl().validateConnection(getServerConfigForOrg(null)).success()).isTrue();
    assertThat(new WsHelperImpl().validateConnection(getServerConfigForOrg("not-exists")).success()).isFalse();
  }

  @Test
  public void downloadModules() {
    updateGlobal();
    assertThat(engineOnTestOrg.allProjectsByKey()).hasSize(1);
    ORCHESTRATOR.getServer().adminWsClient().post("/api/projects/create",
      "key", "foo-bar",
      "name", "Foo",
      "organization", ORGANIZATION);
    // Project in default org is not visible
    ORCHESTRATOR.getServer().provisionProject("foo-bar2", "Foo");
    assertThat(engineOnTestOrg.downloadAllProjects(getServerConfigForOrg(ORGANIZATION), null)).hasSize(2).containsKeys("foo-bar", PROJECT_KEY_JAVA);
  }

  @Test
  public void downloadOrganizations() {
    WsHelper helper = new WsHelperImpl();
    List<RemoteOrganization> organizations = helper.listOrganizations(getServerConfigForOrg(null), null);
    assertThat(organizations).hasSize(2);
  }

  @Test
  public void verifyExtendedDescription() {
    assumeTrue(ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(6, 4));
    updateGlobal();

    String ruleKey = "squid:S106";

    assertThat(engineOnTestOrg.getRuleDetails(ruleKey).getExtendedDescription()).isEmpty();
    assertThat(engineOnDefaultOrg.getRuleDetails(ruleKey).getExtendedDescription()).isEmpty();

    String extendedDescription = "my dummy extended description";

    WsRequest request = new PostRequest("/api/rules/update")
      .setParam("key", ruleKey)
      .setParam("organization", ORGANIZATION)
      .setParam("markdown_note", extendedDescription);
    WsResponse response = adminWsClient.wsConnector().call(request);
    assertThat(response.code()).isEqualTo(200);

    updateGlobal();

    assertThat(engineOnTestOrg.getRuleDetails(ruleKey).getExtendedDescription()).isEqualTo(extendedDescription);
    assertThat(engineOnDefaultOrg.getRuleDetails(ruleKey).getExtendedDescription()).isEmpty();
  }

  @Test
  public void updateModuleInOrga() {
    assumeTrue(ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(6, 4));
    engineOnTestOrg.update(getServerConfigForOrg(ORGANIZATION), null);
    engineOnTestOrg.updateProject(getServerConfigForOrg(ORGANIZATION), PROJECT_KEY_JAVA, null);
  }

  private void updateGlobal() {
    engineOnTestOrg.update(getServerConfigForOrg(ORGANIZATION), null);
    engineOnDefaultOrg.update(getServerConfigForOrg(null), null);
  }

  private ServerConfiguration getServerConfigForOrg(@Nullable String orgKey) {
    return ServerConfiguration.builder()
      .url(ORCHESTRATOR.getServer().getUrl())
      .organizationKey(orgKey)
      .userAgent("SonarLint ITs")
      .credentials(SONARLINT_USER, SONARLINT_PWD)
      .build();
  }

  public static void enableOrganizationsSupport() {
    ORCHESTRATOR.getServer().post("api/organizations/enable_support", emptyMap());
  }

  private static void createOrganization() {
    adminWsClient.organizations().create(new CreateWsRequest.Builder().setKey(ORGANIZATION).setName(ORGANIZATION).build());
  }
}
