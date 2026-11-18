/*
 * SonarLint Core - ITs - Tests
 * Copyright (C) SonarSource Sàrl
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

import com.sonar.orchestrator.build.SonarScanner;
import com.sonar.orchestrator.container.Edition;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.settings.SetRequest;
import org.sonarqube.ws.client.users.CreateRequest;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.client.ConfigScopeNotFoundException;
import org.sonarsource.sonarlint.core.rpc.client.ConnectionNotFoundException;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintCancelChecker;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.HttpConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.taint.vulnerability.DidChangeTaintVulnerabilitiesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.spy;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.CLOUDFORMATION;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.COBOL;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.DOCKER;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.GO;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.HTML;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JS;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.KOTLIN;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.KUBERNETES;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.PHP;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.PYTHON;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.RUBY;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.SCALA;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.SECRETS;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.TERRAFORM;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.XML;

/**
 * Shared setup for the test classes that run against a SonarQube Developer edition.
 * Each subclass registers its own {@link OrchestratorExtension} from {@link #newDeveloperEditionOrchestrator()},
 * so each subclass gets its own server and the subclasses can run in parallel forks.
 */
abstract class AbstractSonarQubeDeveloperEditionTests extends AbstractConnectedTests {

  public static final String CONNECTION_ID = "orchestrator";
  public static final String CONNECTION_ID_WRONG_CREDENTIALS = "wrong-credentials";

  private static OrchestratorExtension orchestrator;

  protected static WsClient adminWsClient;
  protected static MockSonarLintRpcClientDelegate client;
  protected static SonarLintRpcServer backend;

  private static BackendJsonRpcLauncher serverLauncher;

  @TempDir
  private static Path sonarUserHome;

  protected static final List<DidChangeTaintVulnerabilitiesParams> didChangeTaintVulnerabilitiesEvents = new CopyOnWriteArrayList<>();
  protected static final List<String> allBranchNamesForProject = new CopyOnWriteArrayList<>();
  protected static final AtomicReference<String> matchedBranchNameForProject = new AtomicReference<>();
  protected static final List<String> didSynchronizeConfigurationScopes = new CopyOnWriteArrayList<>();
  protected static final Map<String, Boolean> analysisReadinessByConfigScopeId = new ConcurrentHashMap<>();

  static OrchestratorExtension newDeveloperEditionOrchestrator() {
    return OrchestratorUtils.defaultEnvBuilder()
      .setEdition(Edition.DEVELOPER)
      .activateLicense()
      .addPlugin(FileLocation.of("../plugins/global-extension-plugin/target/global-extension-plugin.jar"))
      .addPlugin(FileLocation.of("../plugins/custom-sensor-plugin/target/custom-sensor-plugin.jar"))
      .addPlugin(FileLocation.of("../plugins/java-custom-rules/target/java-custom-rules-plugin.jar"))
      // Ensure SSE are processed correctly just after SQ startup
      .setServerProperty("sonar.pushevents.polling.initial.delay", "2")
      .setServerProperty("sonar.pushevents.polling.period", "1")
      .setServerProperty("sonar.pushevents.polling.last.timestamp", "1")
      .setServerProperty("sonar.projectCreation.mainBranchName", MAIN_BRANCH_NAME)
      .build();
  }

  /**
   * Called from each subclass's {@code @BeforeAll}, once its {@link OrchestratorExtension} has started the server.
   */
  static void startBackend(OrchestratorExtension startedOrchestrator) throws IOException {
    orchestrator = startedOrchestrator;
    adminWsClient = newAdminWsClient(orchestrator);
    adminWsClient.users().create(new CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"));

    var clientToServerOutputStream = new PipedOutputStream();
    var clientToServerInputStream = new PipedInputStream(clientToServerOutputStream);

    var serverToClientOutputStream = new PipedOutputStream();
    var serverToClientInputStream = new PipedInputStream(serverToClientOutputStream);

    serverLauncher = new BackendJsonRpcLauncher(clientToServerInputStream, serverToClientOutputStream);
    client = spy(newDummySonarLintClient());
    var clientLauncher = new ClientJsonRpcLauncher(serverToClientInputStream, clientToServerOutputStream, client);

    backend = clientLauncher.getServerProxy();
    try {
      var languages = Set.of(JAVA, GO, PHP, JS, PYTHON, HTML, RUBY, KOTLIN, SCALA, XML, COBOL, CLOUDFORMATION, DOCKER, KUBERNETES, TERRAFORM, SECRETS);
      backend.initialize(
        new InitializeParams(IT_CLIENT_INFO, IT_TELEMETRY_ATTRIBUTES, HttpConfigurationDto.defaultConfig(), null,
          Set.of(BackendCapability.FULL_SYNCHRONIZATION, BackendCapability.PROJECT_SYNCHRONIZATION, BackendCapability.SERVER_SENT_EVENTS, BackendCapability.SECURITY_HOTSPOTS,
            BackendCapability.DATAFLOW_BUG_DETECTION),
          sonarUserHome.resolve("storage"),
          sonarUserHome.resolve("work"),
          emptySet(), PluginLocator.getEmbeddedPluginsByKeyForTests(),
          languages, emptySet(), emptySet(),
          List.of(new SonarQubeConnectionConfigurationDto(CONNECTION_ID, orchestrator.getServer().getUrl(), false),
            new SonarQubeConnectionConfigurationDto(CONNECTION_ID_WRONG_CREDENTIALS, orchestrator.getServer().getUrl(), false)),
          emptyList(),
          sonarUserHome.toString(),
          Map.of(), false, null, false, null))
        .get();
    } catch (Exception e) {
      throw new IllegalStateException("Cannot initialize the backend", e);
    }
  }

  @BeforeEach
  void clearState() {
    rpcClientLogs.clear();
    didSynchronizeConfigurationScopes.clear();
    analysisReadinessByConfigScopeId.clear();
    allBranchNamesForProject.clear();
    matchedBranchNameForProject.set(null);

    // This profile is altered in a test
    orchestrator.getServer().restoreProfile(FileLocation.ofClasspath("/java-sonarlint.xml"));
  }

  @AfterAll
  static void stop() throws ExecutionException, InterruptedException {
    serverLauncher.getServer().shutdown().get();
  }

  protected String javaRuleKey(String key) {
    return "java:" + key;
  }

  protected void setSettingsMultiValue(@Nullable String moduleKey, String key, String value) {
    adminWsClient.settings().set(new SetRequest()
      .setKey(key)
      .setValues(singletonList(value))
      .setComponent(moduleKey));
  }

  protected static void analyzeMavenProject(String projectDirName, String projectKey) {
    analyzeMavenProject(orchestrator, projectDirName, Map.of("sonar.projectKey", projectKey));
  }

  protected static void openBoundConfigurationScope(String configScopeId, String projectKey, boolean bindingSuggestionDisabled) {
    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
      List.of(new ConfigurationScopeDto(configScopeId, null, true, "My " + configScopeId, new BindingConfigurationDto(CONNECTION_ID, projectKey, bindingSuggestionDisabled)))));
  }

  protected static void waitForSync(String configScopeId) {
    await().atMost(1, TimeUnit.MINUTES).untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(configScopeId));
  }

  protected static void waitForAnalysisToBeReady(String configScopeId) {
    await().atMost(1, TimeUnit.MINUTES).untilAsserted(() -> assertThat(analysisReadinessByConfigScopeId).containsEntry(configScopeId, true));
  }

  protected void analyzeProject(String projectDirName, String projectKey, String... properties) {
    var projectDir = Paths.get("projects/" + projectDirName).toAbsolutePath();
    var scanner = SonarScanner.create(projectDir.toFile())
      .setProjectKey(projectKey)
      .setSourceDirs("src")
      .setProperties(properties);

    scanner.setProperty("sonar.token", orchestrator.getDefaultAdminToken());
    orchestrator.executeBuild(scanner);
  }

  private static MockSonarLintRpcClientDelegate newDummySonarLintClient() {
    return new MockSonarLintRpcClientDelegate() {

      @Override
      public Either<TokenDto, UsernamePasswordDto> getCredentials(String connectionId) throws ConnectionNotFoundException {
        if (connectionId.equals(CONNECTION_ID)) {
          return Either.forRight(new UsernamePasswordDto(SONARLINT_USER, SONARLINT_PWD));
        } else if (connectionId.equals(CONNECTION_ID_WRONG_CREDENTIALS)) {
          return Either.forRight(new UsernamePasswordDto("foo", "bar"));
        } else {
          return super.getCredentials(connectionId);
        }
      }

      @Override
      public void didChangeTaintVulnerabilities(String configurationScopeId, Set<UUID> closedTaintVulnerabilityIds, List<TaintVulnerabilityDto> addedTaintVulnerabilities,
        List<TaintVulnerabilityDto> updatedTaintVulnerabilities) {
        didChangeTaintVulnerabilitiesEvents
          .add(new DidChangeTaintVulnerabilitiesParams(configurationScopeId, closedTaintVulnerabilityIds, addedTaintVulnerabilities, updatedTaintVulnerabilities));
      }

      @Override
      public void didSynchronizeConfigurationScopes(Set<String> configurationScopeIds) {
        didSynchronizeConfigurationScopes.addAll(configurationScopeIds);
      }

      @Override
      public void didChangeAnalysisReadiness(Set<String> configurationScopeIds, boolean areReadyForAnalysis) {
        analysisReadinessByConfigScopeId.putAll(configurationScopeIds.stream().collect(Collectors.toMap(Function.identity(), k -> areReadyForAnalysis)));
      }

      @Override
      public String matchSonarProjectBranch(String configurationScopeId, String mainBranchName, Set<String> allBranchesNames, SonarLintCancelChecker cancelChecker)
        throws ConfigScopeNotFoundException {
        allBranchNamesForProject.addAll(allBranchesNames);
        var matchedBranchName = matchedBranchNameForProject.get();
        return matchedBranchName == null ? super.matchSonarProjectBranch(configurationScopeId, mainBranchName, allBranchesNames, cancelChecker)
          : matchedBranchName;
      }

      @Override
      public void log(LogParams params) {
        // LogOnTestFailure prints these logs when a test fails
        rpcClientLogs.add(params);
      }
    };
  }
}
