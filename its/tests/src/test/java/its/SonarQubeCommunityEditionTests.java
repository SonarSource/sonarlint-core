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

import com.sonar.orchestrator.junit5.OrchestratorExtension;
import com.sonar.orchestrator.locator.FileLocation;
import its.utils.OrchestratorUtils;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.users.CreateRequest;
import org.sonarqube.ws.client.usertokens.GenerateRequest;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.client.ConnectionNotFoundException;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.RevokeTokenParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.HttpConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ClientTrackedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TextRangeWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TrackWithServerIssuesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType.CODE_SMELL;

class SonarQubeCommunityEditionTests extends AbstractConnectedTests {

  private static final String CONNECTION_ID = "orchestrator";

  @RegisterExtension
  static OrchestratorExtension ORCHESTRATOR = OrchestratorUtils.defaultEnvBuilder()
    .addPlugin(FileLocation.of("../plugins/java-custom-rules/target/java-custom-rules-plugin.jar"))
    .setServerProperty("sonar.projectCreation.mainBranchName", MAIN_BRANCH_NAME)
    .build();


  @TempDir
  private static Path sonarUserHome;
  private static WsClient adminWsClient;
  private static SonarLintRpcServer backend;
  private static final Map<String, Boolean> analysisReadinessByConfigScopeId = new ConcurrentHashMap<>();
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
      var featureFlags = new FeatureFlagsDto(true, true, true, false, true, true, false, true, false, true, false);
      var enabledLanguages = Set.of(JAVA);
      backend.initialize(
          new InitializeParams(IT_CLIENT_INFO,
            IT_TELEMETRY_ATTRIBUTES, HttpConfigurationDto.defaultConfig(), null, featureFlags, sonarUserHome.resolve("storage"),
            sonarUserHome.resolve("work"),
            Collections.emptySet(), Collections.emptyMap(), enabledLanguages,  emptySet(), emptySet(),
            List.of(new SonarQubeConnectionConfigurationDto(CONNECTION_ID, ORCHESTRATOR.getServer().getUrl(), true)),
            Collections.emptyList(),
            sonarUserHome.toString(),
            Map.of(), false, null, false, null))
        .get();
    } catch (Exception e) {
      throw new IllegalStateException("Cannot initialize the backend", e);
    }
  }

  @BeforeAll
  static void createSonarLintUser() {
    adminWsClient = newAdminWsClient(ORCHESTRATOR);
    adminWsClient.users().create(new CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"));
  }

  @BeforeEach
  void clearState() {
    analysisReadinessByConfigScopeId.clear();
  }

  @AfterAll
  static void stopBackend() throws ExecutionException, InterruptedException {
    serverLauncher.getServer().shutdown().get();
  }

  @Test
  void test_revoke_token() {
    var tokenName = "testTokenForRevoking";

    var testToken = adminWsClient.userTokens()
      .generate(new GenerateRequest().setName(tokenName));
    assertThat(testToken.getName()).isEqualTo(tokenName);

    // Using the credentials we used throughout the test, we want to remove the additional token!
    assertThat(backend
      .getUserTokenService()
      .revokeToken(new RevokeTokenParams(ORCHESTRATOR.getServer().getUrl(), testToken.getName(), testToken.getToken())))
      .succeedsWithin(Duration.ofSeconds(10));

    var searchResult = adminWsClient.userTokens().search(new org.sonarqube.ws.client.usertokens.SearchRequest().setLogin(testToken.getLogin()));
    assertThat(searchResult.getUserTokensCount()).isZero();
  }

  @Nested
  class StorageSynchronizationWithOnlyJavaEnabled {

    private static final String PROJECT_KEY_LANGUAGE_MIX = "sample-language-mix";

    @BeforeEach
    void prepare() {
      provisionProject(ORCHESTRATOR, PROJECT_KEY_LANGUAGE_MIX, "Sample Language Mix");
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint.xml"));
      ORCHESTRATOR.getServer().restoreProfile(FileLocation.ofClasspath("/python-sonarlint.xml"));
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_LANGUAGE_MIX, "java", "SonarLint IT Java");
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_LANGUAGE_MIX, "py", "SonarLint IT Python");

      // Build project to have bytecode and analyze
      analyzeMavenProject(ORCHESTRATOR, "sample-language-mix", Map.of("sonar.projectKey", PROJECT_KEY_LANGUAGE_MIX));
    }

    @Test
    void should_match_server_issues_of_enabled_languages() throws ExecutionException, InterruptedException {
      var configScopeId = "should_match_server_issues_of_enabled_languages";
      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(configScopeId, null, true, "sample-language-mix", new BindingConfigurationDto(CONNECTION_ID, PROJECT_KEY_LANGUAGE_MIX,
          true)))));
      waitForAnalysisToBeReady(configScopeId);

      var javaClientTrackedFindingDto = new ClientTrackedFindingDto(null, null, new TextRangeWithHashDto(14, 4, 14, 14, "hashedHash"),
        null, "java:S106", "Replace this use of System.out by a logger.");
      var pythonClientTrackedFindingDto = new ClientTrackedFindingDto(null, null, new TextRangeWithHashDto(2, 4, 2, 9, "hashedHash"),
        null, "python:PrintStatementUsage", "Replace print statement by built-in function.");
      var trackWithServerIssuesParams = new TrackWithServerIssuesParams(configScopeId, Map.of(Path.of("src/main/java/foo/Foo.java"),
        List.of(javaClientTrackedFindingDto), Path.of("src/main/java/foo/main.py"), List.of(pythonClientTrackedFindingDto)), true);
      var issuesByIdeRelativePath = backend.getIssueTrackingService().trackWithServerIssues(trackWithServerIssuesParams).get().getIssuesByIdeRelativePath();

      var mainPyIssues = issuesByIdeRelativePath.get(Path.of("src/main/java/foo/main.py"));
      assertThat(mainPyIssues).hasSize(1);
      assertThat(mainPyIssues.get(0).isRight()).isTrue();

      var fooJavaIssues = issuesByIdeRelativePath.get(Path.of("src/main/java/foo/Foo.java"));
      assertThat(fooJavaIssues).hasSize(1);

      if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 5)) {
        assertThat(fooJavaIssues.get(0).isLeft()).isTrue();
        assertThat(fooJavaIssues.get(0).getLeft().getType()).isEqualTo(CODE_SMELL);
      } else {
        assertThat(fooJavaIssues.get(0).isRight()).isTrue();
      }
    }
  }

  private static void waitForAnalysisToBeReady(String configScopeId) {
    await().atMost(1, TimeUnit.MINUTES).untilAsserted(() -> assertThat(analysisReadinessByConfigScopeId).containsEntry(configScopeId, true));
  }

  //TODO Possibly add tests for a method which will replace SonarLintEngine.getPluginDetails()

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
        rpcClientLogs.add(params);
      }

    };
  }
}
