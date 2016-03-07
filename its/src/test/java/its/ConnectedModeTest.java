/*
 * SonarLint Core - ITs
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import com.google.common.collect.ImmutableMap;
import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.container.Server;
import com.sonar.orchestrator.locator.FileLocation;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.wsclient.services.PropertyCreateQuery;
import org.sonar.wsclient.user.UserParameters;
import org.sonarqube.ws.client.HttpConnector;
import org.sonarqube.ws.client.HttpWsClient;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.permission.AddUserWsRequest;
import org.sonarqube.ws.client.permission.RemoveGroupWsRequest;
import org.sonarsource.sonarlint.core.SonarLintClientImpl;
import org.sonarsource.sonarlint.core.client.api.GlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.SonarLintClient;
import org.sonarsource.sonarlint.core.client.api.analysis.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;

import static com.sonar.orchestrator.container.Server.ADMIN_LOGIN;
import static com.sonar.orchestrator.container.Server.ADMIN_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class ConnectedModeTest {

  private static final String PROJECT_KEY = "sample-java";
  private static final String SONARLINT_USER = "sonarlint";
  private static final String SONARLINT_PWD = "sonarlintpwd";

  @ClassRule
  public static Orchestrator ORCHESTRATOR = Orchestrator.builderEnv()
    .setOrchestratorProperty("javaVersion", "LATEST_RELEASE")
    .addPlugin("java")
    .restoreProfileAtStartup(FileLocation.ofClasspath("/java-sonarlint.xml"))
    .build();

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();

  private static SonarLintClient client;
  private static WsClient adminWsClient;
  private static Path sonarUserHome;

  @BeforeClass
  public static void prepare() throws Exception {
    adminWsClient = newAdminWsClient(ORCHESTRATOR);
    ORCHESTRATOR.getServer().getAdminWsClient().create(new PropertyCreateQuery("sonar.forceAuthentication", "true"));
    sonarUserHome = temp.newFolder().toPath();
    client = new SonarLintClientImpl(GlobalConfiguration.builder()
      .setServerId("orchestrator")
      .setSonarLintUserHome(sonarUserHome)
      .build());

    removeGroupPermission("anyone", "scan");

    ORCHESTRATOR.getServer().adminWsClient().userClient()
      .create(UserParameters.create().login(SONARLINT_USER).password(SONARLINT_PWD).passwordConfirmation(SONARLINT_PWD).name("SonarLint"));

    // addUserPermission("sonarlint", "dryRunScan");

    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY, "Sample Java");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY, "java", "SonarLint IT");

    // Build project to have bytecode
    ORCHESTRATOR.executeBuild(MavenBuild.create(new File("projects/sample-java/pom.xml")).setGoals("clean package"));
  }

  @After
  public void stop() {
    FileUtils.deleteQuietly(sonarUserHome.toFile());
    try {
      client.stop();
    } catch (Exception e) {
      // Ignore
    }
  }

  @Test
  public void syncNoAuth() throws Exception {
    try {
      client.sync(ServerConfiguration.builder()
        .url(ORCHESTRATOR.getServer().getUrl())
        .userAgent("SonarLint ITs")
        .build());
      fail("Exception expected");
    } catch (Exception e) {
      assertThat(e).hasMessage("Not authorized. Please check server credentials.");
    }
  }

  @Test
  public void globalSync() throws Exception {
    client.sync(ServerConfiguration.builder()
      .url(ORCHESTRATOR.getServer().getUrl())
      .userAgent("SonarLint ITs")
      .credentials(SONARLINT_USER, SONARLINT_PWD)
      .build());

    client.start();

    assertThat(client.getSyncStatus()).isNotNull();
    assertThat(client.getSyncStatus().getServerVersion()).startsWith(StringUtils.substringBefore(ORCHESTRATOR.getServer().version().toString(), "-"));

    assertThat(client.getRuleDetails("squid:S106").getHtmlDescription()).contains("When logging a message there are two important requirements");

    assertThat(client.getModuleSyncStatus(PROJECT_KEY)).isNull();
  }

  @Test
  public void syncProject() throws Exception {
    client.sync(ServerConfiguration.builder()
      .url(ORCHESTRATOR.getServer().getUrl())
      .userAgent("SonarLint ITs")
      .credentials(SONARLINT_USER, SONARLINT_PWD)
      .build());

    client.syncModule(ServerConfiguration.builder()
      .url(ORCHESTRATOR.getServer().getUrl())
      .userAgent("SonarLint ITs")
      .credentials(SONARLINT_USER, SONARLINT_PWD)
      .build(), PROJECT_KEY);

    client.start();

    assertThat(client.getModuleSyncStatus(PROJECT_KEY)).isNotNull();
  }

  @Test
  public void analysisUseQualityProfile() throws Exception {
    client.sync(ServerConfiguration.builder()
      .url(ORCHESTRATOR.getServer().getUrl())
      .userAgent("SonarLint ITs")
      .credentials(SONARLINT_USER, SONARLINT_PWD)
      .build());

    client.syncModule(ServerConfiguration.builder()
      .url(ORCHESTRATOR.getServer().getUrl())
      .userAgent("SonarLint ITs")
      .credentials(SONARLINT_USER, SONARLINT_PWD)
      .build(), PROJECT_KEY);

    client.start();

    ClientInputFile inputFile = new ClientInputFile() {

      @Override
      public boolean isTest() {
        return false;
      }

      @Override
      public Path getPath() {
        return Paths.get("projects/sample-java/src/main/java/foo/Foo.java");
      }

      @Override
      public <G> G getClientObject() {
        return null;
      }

      @Override
      public Charset getCharset() {
        return null;
      }
    };

    final List<Issue> issues = new ArrayList<>();

    client.analyze(new AnalysisConfiguration(PROJECT_KEY, new File("projects/sample-java").toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile),
      ImmutableMap.of("sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath())), new IssueListener() {

        @Override
        public void handle(Issue issue) {
          issues.add(issue);
        }
      });

    assertThat(issues).hasSize(2);
  }

  private static void removeGroupPermission(String groupName, String permission) {
    adminWsClient.permissions().removeGroup(new RemoveGroupWsRequest()
      .setGroupName(groupName)
      .setPermission(permission));
  }

  private static void addUserPermission(String login, String permission) {
    adminWsClient.permissions().addUser(new AddUserWsRequest()
      .setLogin(login)
      .setPermission(permission));
  }

  public static WsClient newAdminWsClient(Orchestrator orchestrator) {
    Server server = orchestrator.getServer();
    return new HttpWsClient(new HttpConnector.Builder()
      .url(server.getUrl())
      .credentials(ADMIN_LOGIN, ADMIN_PASSWORD)
      .build());
  }

}
