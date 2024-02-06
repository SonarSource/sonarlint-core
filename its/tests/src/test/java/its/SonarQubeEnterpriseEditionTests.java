/*
 * SonarLint Core - ITs - Tests
 * Copyright (C) 2016-2024 SonarSource SA
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.permissions.RemoveGroupRequest;
import org.sonarqube.ws.client.settings.SetRequest;
import org.sonarqube.ws.client.users.CreateRequest;
import org.sonarsource.sonarlint.core.client.legacy.analysis.EngineConfiguration;
import org.sonarsource.sonarlint.core.client.legacy.analysis.RawIssue;
import org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.client.ConnectionNotFoundException;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidRemoveConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.APEX;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.C;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.COBOL;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.TSQL;

class SonarQubeEnterpriseEditionTests extends AbstractConnectedTests {
  public static final String CONNECTION_ID = "orchestrator";
  private static final String PROJECT_KEY_COBOL = "sample-cobol";
  private static final String PROJECT_KEY_C = "sample-c";
  private static final String PROJECT_KEY_TSQL = "sample-tsql";
  private static final String PROJECT_KEY_APEX = "sample-apex";
  private static final String CONFIG_SCOPE_ID = "my-ide-project-name";

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

  private static final List<String> didSynchronizeConfigurationScopes = new CopyOnWriteArrayList<>();

  @AfterAll
  static void stopBackend() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  private static SonarLintAnalysisEngine engine;
  private static String singlePointOfExitRuleKey;

  @BeforeAll
  static void prepare() {
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
    didSynchronizeConfigurationScopes.forEach(s -> backend.getConfigurationService().didRemoveConfigurationScope(new DidRemoveConfigurationScopeParams(s)));
    didSynchronizeConfigurationScopes.clear();
    rpcClientLogs.clear();
    try {
      engine.stop();
    } catch (Exception e) {
      // Ignore
    }
  }

  @Nested
  // TODO Can be removed when switching to Java 16+ and changing prepare() to static
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class CommercialAnalyzers {

    @BeforeAll
    void prepare() throws IOException {
      startBackend(Map.of());
    }

    void start(String projectKey) {
      bindProject("project-" + projectKey, projectKey);

      engine = new SonarLintAnalysisEngine(EngineConfiguration.builder()
        .setSonarLintUserHome(sonarUserHome)
        .setLogOutput((msg, level) -> System.out.println(msg))
        .build(), backend, "orchestrator");
    }

    @Test
    void analysisC_old_build_wrapper_prop(@TempDir File buildWrapperOutput) throws Exception {
      start(PROJECT_KEY_C);

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

      var rawIssues = analyzeFile(PROJECT_KEY_C, "src/file.c", "sonar.cfamily.build-wrapper-output", buildWrapperOutput.getAbsolutePath());

      assertThat(rawIssues)
        .extracting(RawIssue::getRuleKey)
        .containsOnly("c:S3805", singlePointOfExitRuleKey);
    }

    @Test
    // New property was introduced in SonarCFamily 6.18 part of SQ 8.8
    @OnlyOnSonarQube(from = "8.8")
    void analysisC_new_prop() {
      start(PROJECT_KEY_C);

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

      var rawIssues = analyzeFile(PROJECT_KEY_C, "src/file.c", "sonar.cfamily.build-wrapper-content", buildWrapperContent);

      assertThat(rawIssues)
        .extracting(RawIssue::getRuleKey)
        .containsOnly("c:S3805", singlePointOfExitRuleKey);
    }

    @Test
    void analysisCobol() {
      start(PROJECT_KEY_COBOL);

      var rawIssues = analyzeFile(PROJECT_KEY_COBOL, "src/Custmnt2.cbl", "sonar.cobol.file.suffixes", "cbl");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    void analysisTsql() {
      start(PROJECT_KEY_TSQL);

      var rawIssues = analyzeFile(PROJECT_KEY_TSQL, "src/file.tsql");

      assertThat(rawIssues).hasSize(1);
    }

    @Test
    void analysisApex() {
      start(PROJECT_KEY_APEX);

      var rawIssues = analyzeFile(PROJECT_KEY_APEX, "src/file.cls");

      assertThat(rawIssues).hasSize(1);
    }
  }

  @Nested
  // TODO Can be removed when switching to Java 16+ and changing prepare() to static
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class WithEmbeddedAnalyzer {

    @BeforeAll
    void setup() throws IOException {
      startBackend(Map.of("cpp", PluginLocator.getCppPluginPath()));
      bindProject("project-" + PROJECT_KEY_C, PROJECT_KEY_C);

      engine = new SonarLintAnalysisEngine(EngineConfiguration.builder()
        .setSonarLintUserHome(sonarUserHome)
        .setLogOutput((msg, level) -> System.out.println(msg))
        .build(), backend, "orchestrator");
    }

    /**
     * SLCORE-365 c:FunctionSinglePointOfExit has been deprecated in SonarCFamily 6.32.0.44918 (SQ 9.4) so older versions of SQ will
     * return a QP with rule c:FunctionSinglePointOfExit,
     * while embedded analyzer contains the new rule key. So SLCORE should do the translation.
     */
    @Test
    void analysisWithDeprecatedRuleKey() {
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

      var rawIssues = analyzeFile(PROJECT_KEY_C, "src/file.c", "sonar.cfamily.build-wrapper-content", buildWrapperContent);

      assertThat(rawIssues)
        .extracting(RawIssue::getRuleKey)
        .containsOnly("c:S3805", "c:S1005");
    }
  }

  private static void bindProject(String projectName, String projectKey) {
    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
      List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, projectName,
        new BindingConfigurationDto(CONNECTION_ID, projectKey, true)))));
    await().atMost(30, SECONDS).untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));
    // TODO FIX ME and remove this check for a log after https://sonarsource.atlassian.net/browse/SLCORE-396 is fixed
    await().untilAsserted(() ->
      assertThat(rpcClientLogs).anyMatch(s -> Objects.equals(s.getMessage(), "Stored project analyzer configuration")));
  }

  private List<RawIssue> analyzeFile(String projectDir, String filePath, String... properties) {
    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(projectDir, filePath, properties),
      issueListener, null, null, CONFIG_SCOPE_ID);
    return issueListener.issues;
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

      @Override
      public void didSynchronizeConfigurationScopes(Set<String> configurationScopeIds) {
        didSynchronizeConfigurationScopes.addAll(configurationScopeIds);
      }

      @Override
      public void log(LogParams params) {
        System.out.println(params.toString());
        rpcClientLogs.add(params);
      }

      @Override
      public void didUpdatePlugins(String connectionId) {
        engine.restartAsync();
      }

      @Override
      public void didChangeNodeJs(@org.jetbrains.annotations.Nullable Path nodeJsPath, @org.jetbrains.annotations.Nullable String version) {
        engine.restartAsync();
      }
    };
  }

  static void startBackend(Map<String, Path> connectedModeEmbeddedPluginPathsByKey) throws IOException {
    var clientToServerOutputStream = new PipedOutputStream();
    var clientToServerInputStream = new PipedInputStream(clientToServerOutputStream);

    var serverToClientOutputStream = new PipedOutputStream();
    var serverToClientInputStream = new PipedInputStream(serverToClientOutputStream);

    new BackendJsonRpcLauncher(clientToServerInputStream, serverToClientOutputStream);
    var clientLauncher = new ClientJsonRpcLauncher(serverToClientInputStream, clientToServerOutputStream, newDummySonarLintClient());

    backend = clientLauncher.getServerProxy();
    try {
      var languages = Set.of(JAVA, COBOL, C, TSQL, APEX);
      var featureFlags = new FeatureFlagsDto(true, true, true, false, true, false, false, true);
      backend.initialize(
          new InitializeParams(IT_CLIENT_INFO, IT_TELEMETRY_ATTRIBUTES, featureFlags,
            sonarUserHome.resolve("storage"),
            sonarUserHome.resolve("work"),
            emptySet(),
            connectedModeEmbeddedPluginPathsByKey, languages, emptySet(),
            List.of(new SonarQubeConnectionConfigurationDto(CONNECTION_ID, ORCHESTRATOR.getServer().getUrl(), true)), emptyList(),
            sonarUserHome.toString(),
            Map.of(), false, null))
        .get();
    } catch (Exception e) {
      throw new IllegalStateException("Cannot initialize the backend", e);
    }
  }
}
