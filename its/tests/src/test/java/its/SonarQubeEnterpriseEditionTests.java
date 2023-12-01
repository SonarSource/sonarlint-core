/*
 * SonarLint Core - ITs - Tests
 * Copyright (C) 2016-2023 SonarSource SA
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

import com.sonar.orchestrator.container.Edition;
import com.sonar.orchestrator.junit5.OnlyOnSonarQube;
import com.sonar.orchestrator.junit5.OrchestratorExtension;
import com.sonar.orchestrator.locator.FileLocation;
import its.utils.OrchestratorUtils;
import its.utils.PluginLocator;
import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.permissions.RemoveGroupRequest;
import org.sonarqube.ws.client.settings.SetRequest;
import org.sonarqube.ws.client.users.CreateRequest;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.client.ConnectionNotFoundException;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;

class SonarQubeEnterpriseEditionTests extends AbstractConnectedTests {
  public static final String CONNECTION_ID = "orchestrator";
  private static final String PROJECT_KEY_COBOL = "sample-cobol";
  private static final String PROJECT_KEY_C = "sample-c";
  private static final String PROJECT_KEY_TSQL = "sample-tsql";
  private static final String PROJECT_KEY_APEX = "sample-apex";

  @RegisterExtension
  static OrchestratorExtension ORCHESTRATOR = OrchestratorUtils.defaultEnvBuilder()
    .setEdition(Edition.ENTERPRISE)
    .activateLicense()
    .restoreProfileAtStartup(FileLocation.ofClasspath("/c-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/cobol-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/tsql-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/apex-sonarlint.xml"))
    .build();

  private static WsClient adminWsClient;

  @TempDir
  private static Path sonarUserHome;

  private static SonarLintRpcServer backend;

  private static BackendJsonRpcLauncher serverLauncher;

  @BeforeAll
  static void startBackend() throws IOException {
    var clientToServerOutputStream = new PipedOutputStream();
    var clientToServerInputStream = new PipedInputStream(clientToServerOutputStream);

    var serverToClientOutputStream = new PipedOutputStream();
    var serverToClientInputStream = new PipedInputStream(serverToClientOutputStream);

    serverLauncher = new BackendJsonRpcLauncher(clientToServerInputStream, serverToClientOutputStream);
    var clientLauncher = new ClientJsonRpcLauncher(serverToClientInputStream, clientToServerOutputStream, newDummySonarLintClient());

    backend = clientLauncher.getServerProxy();
    try {
      backend.initialize(
        new InitializeParams(IT_CLIENT_INFO, IT_TELEMETRY_ATTRIBUTES, new FeatureFlagsDto(false, true, false, false, false, false, false), sonarUserHome.resolve("storage"),
          sonarUserHome.resolve("workDir"),
          Collections.emptySet(),
          Collections.emptyMap(), Set.of(JAVA), Collections.emptySet(),
          List.of(new SonarQubeConnectionConfigurationDto(CONNECTION_ID, ORCHESTRATOR.getServer().getUrl(), true)), Collections.emptyList(), sonarUserHome.toString(),
          Map.of(), false))
        .get();
    } catch (Exception e) {
      throw new IllegalStateException("Cannot initialize the backend", e);
    }
  }

  @AfterAll
  static void stopBackend() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  private ConnectedSonarLintEngine engine;
  private static String singlePointOfExitRuleKey;

  @BeforeAll
  static void prepare() throws Exception {
    adminWsClient = newAdminWsClient(ORCHESTRATOR);
    adminWsClient.settings().set(new SetRequest().setKey("sonar.forceAuthentication").setValue("true"));

    removeGroupPermission("anyone", "scan");

    adminWsClient.users().create(new CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"));

    provisionProject(ORCHESTRATOR, PROJECT_KEY_C, "Sample C");
    provisionProject(ORCHESTRATOR, PROJECT_KEY_COBOL, "Sample Cobol");
    provisionProject(ORCHESTRATOR, PROJECT_KEY_TSQL, "Sample TSQL");
    provisionProject(ORCHESTRATOR, PROJECT_KEY_APEX, "Sample APEX");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_C, "c", "SonarLint IT C");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_COBOL, "cobol", "SonarLint IT Cobol");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_TSQL, "tsql", "SonarLint IT TSQL");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_APEX, "apex", "SonarLint IT APEX");

    if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 4)) {
      singlePointOfExitRuleKey = "c:S1005";
    } else {
      singlePointOfExitRuleKey = "c:FunctionSinglePointOfExit";
    }
  }

  @AfterEach
  void stop() {
    try {
      engine.stop();
    } catch (Exception e) {
      // Ignore
    }
  }

  @Nested
  class CommercialAnalyzers {

    @BeforeEach
    void start() {
      FileUtils.deleteQuietly(sonarUserHome.toFile());
      engine = new ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration.sonarQubeBuilder()
        .setConnectionId("orchestrator")
        .setSonarLintUserHome(sonarUserHome)
        .addEnabledLanguage(Language.COBOL)
        .addEnabledLanguage(Language.C)
        .addEnabledLanguage(Language.TSQL)
        .addEnabledLanguage(Language.APEX)
        .setLogOutput((msg, level) -> System.out.println(msg))
        .build());
    }

    @Test
    void analysisC_old_build_wrapper_prop(@TempDir File buildWrapperOutput) throws Exception {
      updateProject(PROJECT_KEY_C);
      var issueListener = new SaveIssueListener();

      var buildWrapperContent = "{\"version\":0,\"captures\":[" +
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
        "\",\"executable\":\"compiler\",\"cmd\":[\"cc\",\"src/file.c\"]}]}";

      FileUtils.write(new File(buildWrapperOutput, "build-wrapper-dump.json"), buildWrapperContent, StandardCharsets.UTF_8);
      var analysisConfiguration = createAnalysisConfiguration(PROJECT_KEY_C, PROJECT_KEY_C, "src/file.c", "sonar.cfamily.build-wrapper-output",
        buildWrapperOutput.getAbsolutePath());

      engine.analyze(analysisConfiguration, issueListener, null, null);

      assertThat(issueListener.getIssues()).hasSize(2).extracting(Issue::getRuleKey).containsOnly("c:S3805", singlePointOfExitRuleKey);
    }

    @Test
    // New property was introduced in SonarCFamily 6.18 part of SQ 8.8
    @OnlyOnSonarQube(from = "8.8")
    void analysisC_new_prop() throws Exception {

      updateProject(PROJECT_KEY_C);
      var issueListener = new SaveIssueListener();

      var buildWrapperContent = "{\"version\":0,\"captures\":[" +
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
        "\",\"executable\":\"compiler\",\"cmd\":[\"cc\",\"src/file.c\"]}]}";

      var analysisConfiguration = createAnalysisConfiguration(PROJECT_KEY_C, PROJECT_KEY_C, "src/file.c", "sonar.cfamily.build-wrapper-content",
        buildWrapperContent);

      engine.analyze(analysisConfiguration, issueListener, null, null);

      assertThat(issueListener.getIssues()).hasSize(2).extracting(Issue::getRuleKey).containsOnly("c:S3805", singlePointOfExitRuleKey);
    }

    @Test
    void analysisCobol() throws Exception {
      updateProject(PROJECT_KEY_COBOL);
      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(PROJECT_KEY_COBOL, PROJECT_KEY_COBOL, "src/Custmnt2.cbl",
        "sonar.cobol.file.suffixes", "cbl"), issueListener, null, null);
      assertThat(issueListener.getIssues()).hasSize(1);
    }

    @Test
    void analysisTsql() throws IOException {
      updateProject(PROJECT_KEY_TSQL);

      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(PROJECT_KEY_TSQL, PROJECT_KEY_TSQL, "src/file.tsql"), issueListener, null, null);
      assertThat(issueListener.getIssues()).hasSize(1);
    }

    @Test
    void analysisApex() throws IOException {
      updateProject(PROJECT_KEY_APEX);

      var issueListener = new SaveIssueListener();
      engine.analyze(createAnalysisConfiguration(PROJECT_KEY_APEX, PROJECT_KEY_APEX, "src/file.cls"), issueListener, null, null);
      assertThat(issueListener.getIssues()).hasSize(1);
    }
  }

  @Nested
  class WithEmbeddedAnalyzer {

    @BeforeEach
    void start() {
      FileUtils.deleteQuietly(sonarUserHome.toFile());
      engine = new ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration.sonarQubeBuilder()
        .setConnectionId("orchestrator")
        .setSonarLintUserHome(sonarUserHome)
        .addEnabledLanguage(Language.C)
        .useEmbeddedPlugin(Language.C.getPluginKey(), PluginLocator.getCppPluginPath())
        .setLogOutput((msg, level) -> System.out.println(msg))
        .build());
    }

    /**
     * SLCORE-365 c:FunctionSinglePointOfExit has been deprecated in SonarCFamily 6.32.0.44918 (SQ 9.4) so older versions of SQ will return a QP with rule c:FunctionSinglePointOfExit,
     * while embedded analyzer contains the new rule key. So SLCORE should do the translation.
     */
    @Test
    void analysisWithDeprecatedRuleKey() throws Exception {
      updateProject(PROJECT_KEY_C);
      var issueListener = new SaveIssueListener();

      var buildWrapperContent = "{\"version\":0,\"captures\":[" +
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
        "\",\"executable\":\"compiler\",\"cmd\":[\"cc\",\"src/file.c\"]}]}";

      var analysisConfiguration = createAnalysisConfiguration(PROJECT_KEY_C, PROJECT_KEY_C, "src/file.c", "sonar.cfamily.build-wrapper-content",
        buildWrapperContent);

      engine.analyze(analysisConfiguration, issueListener, null, null);
      assertThat(issueListener.getIssues()).hasSize(2).extracting(Issue::getRuleKey).containsOnly("c:S3805", "c:S1005");
    }
  }

  private void updateProject(String projectKey) {
    engine.updateProject(endpointParams(ORCHESTRATOR), serverLauncher.getJavaImpl().getHttpClient(CONNECTION_ID), projectKey, null);
    engine.sync(endpointParams(ORCHESTRATOR), serverLauncher.getJavaImpl().getHttpClient(CONNECTION_ID), Set.of(projectKey), null);
  }

  private static void removeGroupPermission(String groupName, String permission) {
    adminWsClient.permissions().removeGroup(new RemoveGroupRequest()
      .setGroupName(groupName)
      .setPermission(permission));
  }

  private static SonarLintRpcClientDelegate newDummySonarLintClient() {
    return new MockSonarLintRpcClientDelegate() {

      @Override
      public Either<TokenDto, UsernamePasswordDto> getCredentials(String connectionId) throws ConnectionNotFoundException {
        if (connectionId.equals(CONNECTION_ID)) {
          return Either.forRight(new UsernamePasswordDto(SONARLINT_USER, SONARLINT_PWD));
        }
        return super.getCredentials(connectionId);
      }

    };
  }
}
