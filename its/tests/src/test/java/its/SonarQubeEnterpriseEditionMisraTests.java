/*
 * SonarLint Core - ITs - Tests
 * Copyright (C) 2016-2025 SonarSource SA
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
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
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
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.client.ConnectionNotFoundException;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidRemoveConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.HttpConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.C;

class SonarQubeEnterpriseEditionMisraTests extends AbstractConnectedTests {
  public static final String CONNECTION_ID = "orchestrator";
  private static final String PROJECT_KEY_C = "sample-c";

  @RegisterExtension
  static OrchestratorExtension ORCHESTRATOR = OrchestratorUtils.defaultEnvBuilder()
    // Using enterprise "lightweight" with force-bundled plugin until it is shipped with 2025.4 (?)
    .setEdition(Edition.ENTERPRISE_LW)
    .addBundledPlugin(FileLocation.of(PluginLocator.getCppPluginPath().toFile()))
    // This has to be set at startup for the rules to register
    .setServerProperty("sonar.earlyAccess.misra.enabled", "true")
    .activateLicense()
    .restoreProfileAtStartup(FileLocation.ofClasspath("/c-sonarlint.xml"))
    .build();

  private static WsClient adminWsClient;

  @TempDir
  private static Path sonarUserHome;

  private static SonarLintRpcServer backend;
  private static SonarLintRpcClientDelegate client;

  private static final Map<String, Boolean> analysisReadinessByConfigScopeId = new ConcurrentHashMap<>();

  @AfterAll
  static void stopBackend() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  @BeforeAll
  static void prepare() {
    adminWsClient = newAdminWsClient(ORCHESTRATOR);
    adminWsClient.settings().set(new SetRequest().setKey("sonar.forceAuthentication").setValue("true"));
    // For some reason, it also has to be set manually after the server starts
    adminWsClient.settings().set(new SetRequest().setKey("sonar.earlyAccess.misra.enabled").setValue("true"));

    adminWsClient.permissions().removeGroup(new RemoveGroupRequest().setGroupName("anyone").setPermission("scan"));

    adminWsClient.users().create(new CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"));

    provisionProject(ORCHESTRATOR, PROJECT_KEY_C, "Sample C");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_C, "c", "SonarLint IT C");
  }

  @AfterEach
  void stop() {
    analysisReadinessByConfigScopeId.forEach((scopeId, readiness) -> backend.getConfigurationService().didRemoveConfigurationScope(new DidRemoveConfigurationScopeParams(scopeId)));
    analysisReadinessByConfigScopeId.clear();
    rpcClientLogs.clear();
    ((MockSonarLintRpcClientDelegate) client).clear();
  }

  @Nested
  // TODO Can be removed when switching to Java 16+ and changing prepare() to static
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class MisraEarlyAccess {

    @BeforeAll
    void prepare() throws IOException {
      startBackend(Map.of("cpp", PluginLocator.getCppPluginPath()));
    }

    @AfterEach
    void stop() {
      ((MockSonarLintRpcClientDelegate) client).getRaisedIssues().clear();
    }

    @Test
    @OnlyOnSonarQube(from = "2025.3")
    void analysisC_misra_early_access() {
      String configScopeId = "analysisC_misra_early_access";
      bindProject(configScopeId, "project-" + PROJECT_KEY_C, PROJECT_KEY_C);

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

      var filePath = Path.of("projects").resolve(PROJECT_KEY_C).resolve("src/file.c");
      var fileUri = filePath.toUri();
      backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(
        List.of(new ClientFileDto(fileUri, Path.of("src/file.c"), configScopeId, false, null, filePath.toAbsolutePath(), null, null, true)),
        List.of(),
        List.of()
      ));

      var analyzeResponse = backend.getAnalysisService().analyzeFilesAndTrack(
        new AnalyzeFilesAndTrackParams(configScopeId, UUID.randomUUID(), List.of(fileUri), toMap(new String[]{"sonar.cfamily.build-wrapper-content", buildWrapperContent}), true, System.currentTimeMillis())
      ).join();

      assertThat(analyzeResponse.getFailedAnalysisFiles()).isEmpty();
      var raisedIssues = ((MockSonarLintRpcClientDelegate) client).getRaisedIssues(configScopeId);
      ((MockSonarLintRpcClientDelegate) client).getRaisedIssues().clear();
      var rawIssues = (List<RaisedIssueDto>) (raisedIssues != null ? raisedIssues.values().stream().flatMap(List::stream).toList() : List.of());

      assertThat(rawIssues)
        .extracting(RaisedIssueDto::getRuleKey)
        .containsOnly("c:S3805", "c:S1005", "c:M23_037");
    }
  }

  private static void bindProject(String configScopeId, String projectName, String projectKey) {
    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
      List.of(new ConfigurationScopeDto(configScopeId, null, true, projectName,
        new BindingConfigurationDto(CONNECTION_ID, projectKey, true)))));
    await().atMost(30, SECONDS).untilAsserted(() -> assertThat(analysisReadinessByConfigScopeId).containsEntry(configScopeId, true));
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
      public void didChangeAnalysisReadiness(Set<String> configurationScopeIds, boolean areReadyForAnalysis) {
        analysisReadinessByConfigScopeId.putAll(configurationScopeIds.stream().collect(Collectors.toMap(Function.identity(), k -> areReadyForAnalysis)));
      }

      @Override
      public void log(LogParams params) {
        System.out.println(params);
        rpcClientLogs.add(params);
      }
    };
  }

  static void startBackend(Map<String, Path> connectedModeEmbeddedPluginPathsByKey) throws IOException {
    var clientToServerOutputStream = new PipedOutputStream();
    var clientToServerInputStream = new PipedInputStream(clientToServerOutputStream);

    var serverToClientOutputStream = new PipedOutputStream();
    var serverToClientInputStream = new PipedInputStream(serverToClientOutputStream);

    new BackendJsonRpcLauncher(clientToServerInputStream, serverToClientOutputStream);
    client = newDummySonarLintClient();
    var clientLauncher = new ClientJsonRpcLauncher(serverToClientInputStream, clientToServerOutputStream, client);

    backend = clientLauncher.getServerProxy();
    try {
      var languages = Set.of(C);
      var featureFlags = new FeatureFlagsDto(true, true, true, false, true, false, false, true, false, true, false);
      backend.initialize(
          new InitializeParams(IT_CLIENT_INFO, IT_TELEMETRY_ATTRIBUTES, HttpConfigurationDto.defaultConfig(), null, featureFlags,
            sonarUserHome.resolve("storage"),
            sonarUserHome.resolve("work"),
            emptySet(),
            connectedModeEmbeddedPluginPathsByKey, languages, emptySet(), emptySet(),
            List.of(new SonarQubeConnectionConfigurationDto(CONNECTION_ID, ORCHESTRATOR.getServer().getUrl(), true)), emptyList(),
            sonarUserHome.toString(),
            Map.of(), false, null, false, null))
        .get();
    } catch (Exception e) {
      throw new IllegalStateException("Cannot initialize the backend", e);
    }
  }
}
