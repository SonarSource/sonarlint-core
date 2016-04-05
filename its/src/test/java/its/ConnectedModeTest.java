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
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.wsclient.services.PropertyCreateQuery;
import org.sonar.wsclient.services.PropertyDeleteQuery;
import org.sonar.wsclient.user.UserParameters;
import org.sonarqube.ws.client.HttpConnector;
import org.sonarqube.ws.client.HttpWsClient;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.permission.AddUserWsRequest;
import org.sonarqube.ws.client.permission.RemoveGroupWsRequest;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.UnsupportedServerException;
import org.sonarsource.sonarlint.core.client.api.connected.WsHelper;
import org.sonarsource.sonarlint.core.container.connected.WsHelperImpl;

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
  
  @Rule
  public ExpectedException exception = ExpectedException.none();

  private static WsClient adminWsClient;
  private static Path sonarUserHome;

  private ConnectedSonarLintEngine engine;

  @BeforeClass
  public static void prepare() throws Exception {
    adminWsClient = newAdminWsClient(ORCHESTRATOR);
    ORCHESTRATOR.getServer().getAdminWsClient().create(new PropertyCreateQuery("sonar.forceAuthentication", "true"));
    sonarUserHome = temp.newFolder().toPath();

    removeGroupPermission("anyone", "scan");

    ORCHESTRATOR.getServer().adminWsClient().userClient()
      .create(UserParameters.create().login(SONARLINT_USER).password(SONARLINT_PWD).passwordConfirmation(SONARLINT_PWD).name("SonarLint"));

    // addUserPermission("sonarlint", "dryRunScan");

    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY, "Sample Java");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY, "java", "SonarLint IT");

    // Build project to have bytecode
    ORCHESTRATOR.executeBuild(MavenBuild.create(new File("projects/sample-java/pom.xml")).setGoals("clean package"));
  }

  @Before
  public void start() {
    FileUtils.deleteQuietly(sonarUserHome.toFile());
    engine = new ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration.builder()
      .setServerId("orchestrator")
      .setSonarLintUserHome(sonarUserHome)
      .build());
  }

  @After
  public void stop() {
    ORCHESTRATOR.getServer().getAdminWsClient().delete(new PropertyDeleteQuery("sonar.java.file.suffixes"));
    ORCHESTRATOR.getServer().getAdminWsClient().delete(new PropertyDeleteQuery("sonar.java.file.suffixes", PROJECT_KEY));
    try {
      engine.stop(true);
    } catch (Exception e) {
      // Ignore
    }
  }

  @Test
  public void updateNoAuth() throws Exception {
    try {
      engine.update(ServerConfiguration.builder()
        .url(ORCHESTRATOR.getServer().getUrl())
        .userAgent("SonarLint ITs")
        .build());
      fail("Exception expected");
    } catch (Exception e) {
      assertThat(e).hasMessage("Not authorized. Please check server credentials.");
    }
  }

  @Test
  public void globalUpdate() throws Exception {
    updateGlobal();

    assertThat(engine.getUpdateStatus()).isNotNull();
    assertThat(engine.getUpdateStatus().getServerVersion()).startsWith(StringUtils.substringBefore(ORCHESTRATOR.getServer().version().toString(), "-"));

    assertThat(engine.getRuleDetails("squid:S106").getHtmlDescription()).contains("When logging a message there are two important requirements");

    assertThat(engine.getModuleUpdateStatus(PROJECT_KEY)).isNull();
  }

  @Test
  public void updateProject() throws Exception {
    updateGlobal();

    updateModule();

    assertThat(engine.getModuleUpdateStatus(PROJECT_KEY)).isNotNull();
  }

  @Test
  public void analysisUseQualityProfile() throws Exception {
    updateGlobal();

    updateModule();

    final List<Issue> issues = new ArrayList<>();

    engine.analyze(new ConnectedAnalysisConfiguration(PROJECT_KEY, new File("projects/sample-java").toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile()),
      ImmutableMap.of("sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath())), new IssueListener() {

        @Override
        public void handle(Issue issue) {
          issues.add(issue);
        }
      });

    assertThat(issues).hasSize(2);
  }
  
  @Test
  public void generateToken() {
    WsHelper ws = new WsHelperImpl();
    ServerConfiguration serverConfig = ServerConfiguration.builder()
    .url(ORCHESTRATOR.getServer().getUrl())
    .userAgent("SonarLint ITs")
    .credentials(SONARLINT_USER, SONARLINT_PWD)
    .build();
    
    if (!ORCHESTRATOR.getServer().version().isGreaterThanOrEquals("5.4")) {
      exception.expect(UnsupportedServerException.class);
    }
    
    String token = ws.generateAuthenticationToken(serverConfig, "name", false);
    assertThat(token).isNotNull();
    
    token = ws.generateAuthenticationToken(serverConfig, "name", true);
    assertThat(token).isNotNull();
  }

  @Test
  public void analysisUseConfiguration() throws Exception {
    updateGlobal();
    updateModule();

    final List<Issue> issues = new ArrayList<>();

    engine.analyze(new ConnectedAnalysisConfiguration(PROJECT_KEY, new File("projects/sample-java").toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile()),
      ImmutableMap.of("sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath())), new IssueListener() {

        @Override
        public void handle(Issue issue) {
          issues.add(issue);
        }
      });
    assertThat(issues).hasSize(2);

    issues.clear();

    // Override default file suffixes in global props so that input file is not considered as a Java file
    ORCHESTRATOR.getServer().getAdminWsClient().create(new PropertyCreateQuery("sonar.java.file.suffixes", ".foo"));
    updateGlobal();
    updateModule();

    engine.analyze(new ConnectedAnalysisConfiguration(PROJECT_KEY, new File("projects/sample-java").toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile()),
      ImmutableMap.of("sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath())), new IssueListener() {

        @Override
        public void handle(Issue issue) {
          issues.add(issue);
        }
      });
    assertThat(issues).isEmpty();

    // Override default file suffixes in project props so that input file is considered as a Java file again
    ORCHESTRATOR.getServer().getAdminWsClient().create(new PropertyCreateQuery("sonar.java.file.suffixes", ".java", PROJECT_KEY));
    updateGlobal();
    updateModule();

    engine.analyze(new ConnectedAnalysisConfiguration(PROJECT_KEY, new File("projects/sample-java").toPath(), temp.newFolder().toPath(), Arrays.asList(inputFile()),
      ImmutableMap.of("sonar.java.binaries", new File("projects/sample-java/target/classes").getAbsolutePath())), new IssueListener() {

        @Override
        public void handle(Issue issue) {
          issues.add(issue);
        }
      });
    assertThat(issues).hasSize(2);

  }

  private void updateModule() {
    engine.updateModule(ServerConfiguration.builder()
      .url(ORCHESTRATOR.getServer().getUrl())
      .userAgent("SonarLint ITs")
      .credentials(SONARLINT_USER, SONARLINT_PWD)
      .build(), PROJECT_KEY);
  }

  private void updateGlobal() {
    engine.update(ServerConfiguration.builder()
      .url(ORCHESTRATOR.getServer().getUrl())
      .userAgent("SonarLint ITs")
      .credentials(SONARLINT_USER, SONARLINT_PWD)
      .build());
  }

  private ClientInputFile inputFile() {
    return new ClientInputFile() {

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
  }

  private static void removeGroupPermission(String groupName, String permission) {
    if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals("5.2")) {
      adminWsClient.permissions().removeGroup(new RemoveGroupWsRequest()
        .setGroupName(groupName)
        .setPermission(permission));
    } else {
      // TODO
    }
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
