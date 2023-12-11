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

import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.junit5.OrchestratorExtension;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.locator.MavenLocation;
import its.utils.OrchestratorUtils;
import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.users.CreateRequest;
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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.GetPathTranslationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ClientTrackedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TextRangeWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TrackWithServerIssuesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

import static its.utils.ItUtils.SONAR_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType.CODE_SMELL;

class SonarQubeCommunityEditionTests extends AbstractConnectedTests {
  private static final String CONNECTION_ID = "orchestrator";
  private static final String CONFIG_SCOPE_ID = "my-ide-project-name";

  @RegisterExtension
  static OrchestratorExtension ORCHESTRATOR = OrchestratorUtils.defaultEnvBuilder()
    .addPlugin(MavenLocation.of("org.sonarsource.sonarqube", "sonar-xoo-plugin", SONAR_VERSION))
    .addPlugin(FileLocation.of("../plugins/java-custom-rules/target/java-custom-rules-plugin.jar"))
    .setServerProperty("sonar.projectCreation.mainBranchName", MAIN_BRANCH_NAME)
    .build();


  @TempDir
  private static Path sonarUserHome;
  private static WsClient adminWsClient;
  private static SonarLintRpcServer backend;
  private static final List<String> didSynchronizeConfigurationScopes = new CopyOnWriteArrayList<>();
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
      var featureFlags = new FeatureFlagsDto(true, true, true, false, true, true, true);
      var enabledLanguages = Set.of(JAVA);
      backend.initialize(
          new InitializeParams(IT_CLIENT_INFO,
            IT_TELEMETRY_ATTRIBUTES, featureFlags, sonarUserHome.resolve("storage"),
            sonarUserHome.resolve("work"),
            Collections.emptySet(), Collections.emptyMap(), enabledLanguages, Collections.emptySet(),
            List.of(new SonarQubeConnectionConfigurationDto(CONNECTION_ID, ORCHESTRATOR.getServer().getUrl(), true)),
            Collections.emptyList(),
            sonarUserHome.toString(),
            Map.of(), false, null))
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
    didSynchronizeConfigurationScopes.clear();
  }

  @AfterEach
  void removeConfigScope() {
    backend.getConfigurationService().didRemoveConfigurationScope(new DidRemoveConfigurationScopeParams(CONFIG_SCOPE_ID));
  }

  @AfterAll
  static void stopBackend() throws ExecutionException, InterruptedException {
    serverLauncher.getJavaImpl().shutdown().get();
  }

  @Nested
  // TODO Can be removed when switching to Java 16+ and changing prepare() to static
  @TestInstance(Lifecycle.PER_CLASS)
  class PathPrefix {

    private static final String MULTI_MODULE_PROJECT_KEY = "com.sonarsource.it.samples:multi-modules-sample";

    @BeforeAll
    void analyzeMultiModuleProject() {
      // Project has 5 modules: B, B/B1, B/B2, A, A/A1 and A/A2
      analyzeMavenProject(ORCHESTRATOR, "multi-modules-sample");
    }

    @Test
    void should_translate_path_prefixes_for_idePath_with_prefix() {
      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "projectName", new BindingConfigurationDto(CONNECTION_ID,
          MULTI_MODULE_PROJECT_KEY, true)))));
      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));

      var idePath = Path.of("src/main/java/com/sonar/it/samples/modules/b1/HelloB1.java");
      var clientFileDto = new ClientFileDto(idePath.toUri(), idePath, CONFIG_SCOPE_ID, null, StandardCharsets.UTF_8.name(),
        idePath.toAbsolutePath(), null);
      var didUpdateFileSystemParams = new DidUpdateFileSystemParams(List.of(), List.of(clientFileDto));
      backend.getFileService().didUpdateFileSystem(didUpdateFileSystemParams);

      await().untilAsserted(() -> assertThat(backend.getFileService().getPathTranslation(new GetPathTranslationParams(CONFIG_SCOPE_ID)).get().getServerPathPrefix()).isEqualTo("module_b/module_b1"));
      await().untilAsserted(() -> assertThat(backend.getFileService().getPathTranslation(new GetPathTranslationParams(CONFIG_SCOPE_ID)).get().getIdePathPrefix()).isEmpty());
    }

    @Test
    void should_translate_path_prefixes_for_idePath_without_prefix() {
      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "projectName", new BindingConfigurationDto(CONNECTION_ID,
          MULTI_MODULE_PROJECT_KEY, true)))));
      await().untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));

      var entireIdePath = Path.of("module_b/module_b1/src/main/java/com/sonar/it/samples/modules/b1/HelloB1.java");
      var clientFileDto = new ClientFileDto(entireIdePath.toUri(), entireIdePath, CONFIG_SCOPE_ID, null, StandardCharsets.UTF_8.name(),
        entireIdePath.toAbsolutePath(), null);
      var didUpdateFileSystemParams = new DidUpdateFileSystemParams(List.of(), List.of(clientFileDto));
      backend.getFileService().didUpdateFileSystem(didUpdateFileSystemParams);

      await().untilAsserted(() -> assertThat(backend.getFileService().getPathTranslation(new GetPathTranslationParams(CONFIG_SCOPE_ID)).get().getServerPathPrefix()).isEmpty());
      await().untilAsserted(() -> assertThat(backend.getFileService().getPathTranslation(new GetPathTranslationParams(CONFIG_SCOPE_ID)).get().getIdePathPrefix()).isEmpty());
    }
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
      ORCHESTRATOR.executeBuild(MavenBuild.create(new File("projects/sample-language-mix/pom.xml"))
        .setCleanPackageSonarGoals()
        .setProperty("sonar.projectKey", PROJECT_KEY_LANGUAGE_MIX)
        .setProperty("sonar.login", com.sonar.orchestrator.container.Server.ADMIN_LOGIN)
        .setProperty("sonar.password", com.sonar.orchestrator.container.Server.ADMIN_PASSWORD));
    }

    @Test
    void should_match_server_issues_of_enabled_languages() throws ExecutionException, InterruptedException {
      backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto("CONFIG_SCOPE_ID", null, true, "sample-language-mix", new BindingConfigurationDto(CONNECTION_ID, PROJECT_KEY_LANGUAGE_MIX,
          true)))));

      var javaClientTrackedFindingDto = new ClientTrackedFindingDto(null, null, new TextRangeWithHashDto(14, 4, 14, 14, "hashedHash"),
        null, "java:S106", "Replace this use of System.out by a logger.");
      var pythonClientTrackedFindingDto = new ClientTrackedFindingDto(null, null, new TextRangeWithHashDto(2, 4, 2, 9, "hashedHash"),
        null, "python:PrintStatementUsage", "Replace print statement by built-in function.");
      var trackWithServerIssuesParams = new TrackWithServerIssuesParams("CONFIG_SCOPE_ID", Map.of("src/main/java/foo/Foo.java",
        List.of(javaClientTrackedFindingDto), "src/main/java/foo/main.py", List.of(pythonClientTrackedFindingDto)), true);
      var issuesByServerRelativePath = backend.getIssueTrackingService().trackWithServerIssues(trackWithServerIssuesParams).get().getIssuesByServerRelativePath();

      var mainPyIssues = issuesByServerRelativePath.get("src/main/java/foo/main.py");
      assertThat(mainPyIssues).hasSize(1);
      assertThat(mainPyIssues.get(0).isRight()).isTrue();

      var fooJavaIssues = issuesByServerRelativePath.get("src/main/java/foo/Foo.java");
      assertThat(fooJavaIssues).hasSize(1);

      if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 5)) {
        assertThat(fooJavaIssues.get(0).isLeft()).isTrue();
        assertThat(fooJavaIssues.get(0).getLeft().getType()).isEqualTo(CODE_SMELL);
      } else {
        assertThat(fooJavaIssues.get(0).isRight()).isTrue();
      }
    }
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
      public void didSynchronizeConfigurationScopes(DidSynchronizeConfigurationScopeParams params) {
        didSynchronizeConfigurationScopes.addAll(params.getConfigurationScopeIds());
      }

    };
  }
}
