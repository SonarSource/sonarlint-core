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

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.container.Server;
import com.sonar.orchestrator.locator.FileLocation;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import its.tools.SonarlintProject;
import its.tools.SonarlintDaemon;
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
import org.sonar.wsclient.services.PropertyDeleteQuery;
import org.sonar.wsclient.user.UserParameters;
import org.sonarqube.ws.client.HttpConnector;
import org.sonarqube.ws.client.HttpWsClient;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.permission.RemoveGroupWsRequest;
import org.sonarsource.sonarlint.daemon.proto.StandaloneSonarLintGrpc;
import org.sonarsource.sonarlint.daemon.proto.ConnectedSonarLintGrpc;
import org.sonarsource.sonarlint.daemon.proto.ConnectedSonarLintGrpc.ConnectedSonarLintBlockingStub;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.InputFile;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.Issue;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.LogEvent;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.ModuleUpdateReq;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.ServerConfig;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.StorageState;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.Void;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.ConnectedAnalysisReq.Builder;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.ServerConfig.Credentials;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.ConnectedAnalysisReq;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.ConnectedConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.sonar.orchestrator.container.Server.ADMIN_LOGIN;
import static com.sonar.orchestrator.container.Server.ADMIN_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

public class ConnectedDaemonTest {
  private static final String PROJECT_KEY_JAVA = "sample-java";
  private static final String SONARLINT_USER = "sonarlint";
  private static final String SONARLINT_PWD = "sonarlintpwd";
  private static final String STORAGE_ID = "storage";

  @ClassRule
  public static Orchestrator ORCHESTRATOR = Orchestrator.builderEnv()
    .addPlugin("java")
    .restoreProfileAtStartup(FileLocation.ofClasspath("/java-sonarlint.xml"))
    .build();

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public SonarlintDaemon daemon = new SonarlintDaemon();

  @Rule
  public SonarlintProject clientTools = new SonarlintProject();

  @Rule
  public ExpectedException exception = ExpectedException.none();

  private static WsClient adminWsClient;
  private static Path sonarUserHome;

  @BeforeClass
  public static void prepare() throws Exception {
    adminWsClient = newAdminWsClient(ORCHESTRATOR);
    ORCHESTRATOR.getServer().getAdminWsClient().create(new PropertyCreateQuery("sonar.forceAuthentication", "true"));
    sonarUserHome = temp.newFolder().toPath();

    removeGroupPermission("anyone", "scan");

    ORCHESTRATOR.getServer().adminWsClient().userClient()
      .create(UserParameters.create().login(SONARLINT_USER).password(SONARLINT_PWD).passwordConfirmation(SONARLINT_PWD).name("SonarLint"));

    // addUserPermission("sonarlint", "dryRunScan");

    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY_JAVA, "Sample Java");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JAVA, "java", "SonarLint IT Java");

    // Build project to have bytecode
    ORCHESTRATOR.executeBuild(MavenBuild.create(new File("projects/sample-java/pom.xml")).setGoals("clean package"));
  }

  @Before
  public void start() {
    FileUtils.deleteQuietly(sonarUserHome.toFile());
    daemon.install();
  }

  @After
  public void stop() {
    ORCHESTRATOR.getServer().getAdminWsClient().delete(new PropertyDeleteQuery("sonar.java.file.suffixes"));
    ORCHESTRATOR.getServer().getAdminWsClient().delete(new PropertyDeleteQuery("sonar.java.file.suffixes", PROJECT_KEY_JAVA));
  }

  @Test
  public void testNormal() throws InterruptedException, IOException {
    daemon.run();
    daemon.waitReady();
    ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 8050)
      .usePlaintext(true)
      .build();

    LogCollector logs = new LogCollector();
    ConnectedSonarLintBlockingStub sonarlint = ConnectedSonarLintGrpc.newBlockingStub(channel);

    // REGISTER
    sonarlint.start(createConnectedConfig());

    // STATE
    assertThat(sonarlint.getState(Void.newBuilder().build()).getState()).isEqualTo(StorageState.State.NEVER_UPDATED);

    // UPDATE GLOBAL
    ServerConfig serverConfig = ServerConfig.newBuilder()
      .setHostUrl(ORCHESTRATOR.getServer().getUrl())
      .setCredentials(Credentials.newBuilder()
        .setLogin(SONARLINT_USER)
        .setPassword(SONARLINT_PWD)
        .build())
      .build();

    sonarlint.update(serverConfig);

    // STATE
    assertThat(sonarlint.getState(Void.newBuilder().build()).getState()).isEqualTo(StorageState.State.UPDATED);

    // UPDATE MODULE
    ModuleUpdateReq moduleUpdate = ModuleUpdateReq.newBuilder()
      .setModuleKey(PROJECT_KEY_JAVA)
      .setServerConfig(serverConfig)
      .build();
    sonarlint.updateModule(moduleUpdate);

    // ANALYSIS
    ClientCall<Void, LogEvent> call = getLogs(logs, channel);
    Iterator<Issue> issues = sonarlint.analyze(createAnalysisConfig(PROJECT_KEY_JAVA));

    assertThat(issues).hasSize(2);
    // assertThat(logs.getLogsAndClear()).contains("2 files indexed");
    call.cancel();

    channel.shutdownNow();
    channel.awaitTermination(2, TimeUnit.SECONDS);
  }

  @Test
  public void testPort() throws IOException {
    daemon.run("--port", "8051");
    daemon.waitReady();

    ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 8051)
      .usePlaintext(true)
      .build();

    ConnectedSonarLintBlockingStub sonarlint = ConnectedSonarLintGrpc.newBlockingStub(channel);
    sonarlint.start(createConnectedConfig());
  }

  @Test
  public void testError() throws IOException {
    daemon.run();
    daemon.waitReady();
    ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 8050)
      .usePlaintext(true)
      .build();

    ConnectedSonarLintBlockingStub sonarlint = ConnectedSonarLintGrpc.newBlockingStub(channel);
    sonarlint.start(createConnectedConfig());

    // Analyze without update -> error
    Iterator<Issue> analyze = sonarlint.analyze(createAnalysisConfig(PROJECT_KEY_JAVA));

    exception.expectMessage("Please update server 'storage'");
    exception.expect(StatusRuntimeException.class);
    analyze.hasNext();
  }

  private ClientCall<Void, LogEvent> getLogs(LogCollector collector, Channel channel) {
    ClientCall<Void, LogEvent> call = channel.newCall(StandaloneSonarLintGrpc.METHOD_STREAM_LOGS, CallOptions.DEFAULT);
    call.start(collector, new Metadata());
    call.sendMessage(Void.newBuilder().build());
    call.halfClose();
    call.request(Integer.MAX_VALUE);
    return call;
  }

  private static ConnectedConfiguration createConnectedConfig() throws IOException {
    return ConnectedConfiguration.newBuilder()
      .setStorageId(STORAGE_ID)
      .setHomePath(temp.newFolder().toString())
      .build();
  }

  private ConnectedAnalysisReq createAnalysisConfig(String projectName) throws IOException {
    Path projectPath = clientTools.deployProject(projectName);
    List<Path> sourceFiles = clientTools.collectAllFiles(projectPath);
    Builder builder = ConnectedAnalysisReq.newBuilder();

    for (Path p : sourceFiles) {
      InputFile file = InputFile.newBuilder()
        .setCharset(StandardCharsets.UTF_8.name())
        .setPath(p.toAbsolutePath().toString())
        .setIsTest(false)
        .build();
      builder.addFile(file);
    }
    return builder
      .setBaseDir(projectPath.toAbsolutePath().toString())
      .setWorkDir(temp.newFolder().getAbsolutePath())
      .setModuleKey(PROJECT_KEY_JAVA)
      .putAllProperties(Collections.singletonMap("sonar.java.binaries",
        new File("projects/sample-java/target/classes").getAbsolutePath()))
      .build();
  }

  private static class LogCollector extends ClientCall.Listener<LogEvent> {
    private List<LogEvent> list = Collections.synchronizedList(new LinkedList<LogEvent>());

    @Override
    public void onMessage(LogEvent log) {
      list.add(log);
    }

    @Override
    public void onClose(Status status, Metadata trailers) {
      System.out.println("LOGS CLOSED " + status);
    }

    public String getLogsAndClear() {
      StringBuilder builder = new StringBuilder();
      synchronized (list) {
        for (LogEvent e : list) {
          if (e.getIsDebug()) {
            continue;
          }

          builder.append(e.getLog()).append("\n");
        }
        // list.clear();
      }

      return builder.toString();
    }

    public List<LogEvent> get() {
      return list;
    }
  }

  public static WsClient newAdminWsClient(Orchestrator orchestrator) {
    Server server = orchestrator.getServer();
    return new HttpWsClient(new HttpConnector.Builder()
      .url(server.getUrl())
      .credentials(ADMIN_LOGIN, ADMIN_PASSWORD)
      .build());
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
}
