/*
 * SonarLint Core - ITs - Tests
 * Copyright (C) 2016-2020 SonarSource SA
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
import its.tools.ItUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
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
import org.sonarqube.ws.client.PostRequest;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.WsRequest;
import org.sonarqube.ws.client.WsResponse;
import org.sonarqube.ws.client.organization.CreateWsRequest;
import org.sonarqube.ws.client.project.CreateRequest;
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
  private static final String ORGANIZATION_NAME = "Test Org";
  private static final String PROJECT_KEY_JAVA = "sample-java";

  @ClassRule
  public static Orchestrator ORCHESTRATOR;

  static {
    OrchestratorBuilder orchestratorBuilder = Orchestrator.builderEnv()
      .setSonarVersion(SONAR_VERSION)
      .setServerProperty("sonar.sonarcloud.enabled", "true")
      .addPlugin(MavenLocation.of("org.sonarsource.java", "sonar-java-plugin", ItUtils.javaVersion));
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
  public static void prepare() throws IOException {
    adminWsClient = ConnectedModeTest.newAdminWsClient(ORCHESTRATOR);
    sonarUserHome = temp.newFolder().toPath();

    adminWsClient.users().create(org.sonarqube.ws.client.user.CreateRequest.builder()
      .setLogin(SONARLINT_USER)
      .setPassword(SONARLINT_PWD)
      .setName("SonarLint")
      .build());

    enableOrganizationsSupport();
    createOrganization();

    adminWsClient.projects()
      .create(CreateRequest.builder()
        .setKey(PROJECT_KEY_JAVA)
        .setName("Sample Java")
        .setOrganization(ORGANIZATION)
        .build());
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
    adminWsClient.projects()
      .create(CreateRequest.builder()
        .setKey("foo-bar")
        .setName("Foo")
        .setOrganization(ORGANIZATION)
        .build());
    // Project in default org is not visible
    ORCHESTRATOR.getServer().provisionProject("foo-bar2", "Foo");
    assertThat(engineOnTestOrg.downloadAllProjects(getServerConfigForOrg(ORGANIZATION), null)).hasSize(2).containsKeys("foo-bar", PROJECT_KEY_JAVA);
  }

  @Test
  public void downloadOrganizations() {
    WsHelper helper = new WsHelperImpl();
    List<RemoteOrganization> organizations = helper.listOrganizations(getServerConfigForOrg(null), null);
    assertThat(organizations).extracting(RemoteOrganization::getKey).hasSize(2);
  }

  @Test
  public void downloadUserOrganizations() {
    // 'member' WS parameter was introduced in 7.0
    assumeTrue(ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(7, 0));
    WsHelper helper = new WsHelperImpl();
    List<RemoteOrganization> organizations = helper.listUserOrganizations(getServerConfigForOrg(null), null);
    assertThat(organizations).extracting(RemoteOrganization::getKey).hasSize(1);
  }

  @Test
  public void getOrganization() {
    WsHelper helper = new WsHelperImpl();
    Optional<RemoteOrganization> org = helper.getOrganization(getServerConfigForOrg(null), ORGANIZATION, null);
    assertThat(org).isPresent();
    assertThat(org.get().getKey()).isEqualTo(ORGANIZATION);
    assertThat(org.get().getName()).isEqualTo(ORGANIZATION_NAME);
  }

  @Test
  public void verifyExtendedDescription() {
    updateGlobal();

    String ruleKey = ItUtils.javaVersion.equals("LATEST_RELEASE") ? "java:S106" : "squid:S106";

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
    adminWsClient.organizations().create(new CreateWsRequest.Builder().setKey(ORGANIZATION).setName(ORGANIZATION_NAME).build());
  }
}
