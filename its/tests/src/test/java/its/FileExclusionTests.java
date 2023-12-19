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

import com.sonar.orchestrator.junit5.OrchestratorExtension;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.locator.MavenLocation;
import its.utils.OrchestratorUtils;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.settings.ResetRequest;
import org.sonarqube.ws.client.settings.SetRequest;
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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.GetFilesStatusParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

import static its.utils.ItUtils.SONAR_VERSION;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;

class FileExclusionTests extends AbstractConnectedTests {
  @RegisterExtension
  static OrchestratorExtension ORCHESTRATOR = OrchestratorUtils.defaultEnvBuilder()
    .addPlugin(MavenLocation.of("org.sonarsource.sonarqube", "sonar-xoo-plugin", SONAR_VERSION))
    .addPlugin(FileLocation.of("../plugins/java-custom-rules/target/java-custom-rules-plugin.jar"))
    .setServerProperty("sonar.projectCreation.mainBranchName", MAIN_BRANCH_NAME)
    .build();

  private static final String CONNECTION_ID = "orchestrator";
  private static final String CONFIG_SCOPE_ID = "my-ide-project-name";

  @TempDir
  private static Path sonarUserHome;
  private static WsClient adminWsClient;
  private static SonarLintRpcServer backend;
  private static final List<String> didSynchronizeConfigurationScopes = new CopyOnWriteArrayList<>();
  private static BackendJsonRpcLauncher serverLauncher;

  @BeforeAll
  static void startBackend() throws IOException {
    System.setProperty("sonarlint.internal.synchronization.initialDelay", "3");
    System.setProperty("sonarlint.internal.synchronization.period", "5");
    System.setProperty("sonarlint.internal.synchronization.scope.period", "3");

    var clientToServerOutputStream = new PipedOutputStream();
    var clientToServerInputStream = new PipedInputStream(clientToServerOutputStream);

    var serverToClientOutputStream = new PipedOutputStream();
    var serverToClientInputStream = new PipedInputStream(serverToClientOutputStream);

    serverLauncher = new BackendJsonRpcLauncher(clientToServerInputStream, serverToClientOutputStream);
    var clientLauncher = new ClientJsonRpcLauncher(serverToClientInputStream, clientToServerOutputStream, newDummySonarLintClient());

    backend = clientLauncher.getServerProxy();
    try {
      var featureFlags = new FeatureFlagsDto(true, true, true, false, true, true, false, true);
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
  static void stop() throws ExecutionException, InterruptedException {
    serverLauncher.getJavaImpl().shutdown().get();
    System.clearProperty("sonarlint.internal.synchronization.initialDelay");
    System.clearProperty("sonarlint.internal.synchronization.period");
    System.clearProperty("sonarlint.internal.synchronization.scope.period");
  }

  @Test
  void should_respect_exclusion_settings_on_SQ() {
    var projectKey = "sample-java";
    var projectName = "my-sample-java";
    provisionProject(ORCHESTRATOR, projectKey, projectName);

    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
      List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, projectName, new BindingConfigurationDto(CONNECTION_ID, projectKey,
        true)))));
    await().atMost(20, SECONDS).untilAsserted(() -> assertThat(didSynchronizeConfigurationScopes).contains(CONFIG_SCOPE_ID));

    var filePath = Path.of("src/main/java/foo/Foo.java");
    var clientFileDto = new ClientFileDto(filePath.toUri(), filePath, CONFIG_SCOPE_ID, null, StandardCharsets.UTF_8.name(),
      filePath.toAbsolutePath(), null);
    var didUpdateFileSystemParams = new DidUpdateFileSystemParams(List.of(), List.of(clientFileDto));
    backend.getFileService().didUpdateFileSystem(didUpdateFileSystemParams);

    // Firstly check file is included
    var getFilesStatusParams = new GetFilesStatusParams(Map.of(CONFIG_SCOPE_ID, List.of(filePath.toUri())));
    await().atMost(10, SECONDS).untilAsserted(() ->
      assertThat(backend.getFileService().getFilesStatus(getFilesStatusParams).get().getFileStatuses().get(filePath.toUri()).isExcluded()).isFalse());

    // Change file exclusion settings on SQ which should not affect Foo.java
    adminWsClient.settings().set(new SetRequest()
      .setKey("sonar.exclusions")
      .setValues(singletonList("**/*.js"))
      .setComponent(projectKey));

    // Check Foo.java is still included
    await().atMost(10, SECONDS).untilAsserted(() ->
      assertThat(backend.getFileService().getFilesStatus(getFilesStatusParams).get().getFileStatuses().get(filePath.toUri()).isExcluded()).isFalse());

    // Change file exclusion settings on SQ which should affect Foo.java
    adminWsClient.settings().set(new SetRequest()
      .setKey("sonar.exclusions")
      .setValues(singletonList("**/*.java"))
      .setComponent(projectKey));

    // Check Foo.java is excluded
    await().atMost(30, SECONDS).untilAsserted(() ->
      assertThat(backend.getFileService().getFilesStatus(getFilesStatusParams).get().getFileStatuses().get(filePath.toUri()).isExcluded()).isTrue());

    // Reset file exclusion settings on SQ
    adminWsClient.settings().reset(new ResetRequest()
      .setKeys(Collections.singletonList("sonar.exclusions"))
      .setComponent(projectKey));

    // Check Foo.java is included again
    await().atMost(30, SECONDS).untilAsserted(() ->
      assertThat(backend.getFileService().getFilesStatus(getFilesStatusParams).get().getFileStatuses().get(filePath.toUri()).isExcluded()).isFalse());
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
      public void didSynchronizeConfigurationScopes(DidSynchronizeConfigurationScopeParams params) {
        didSynchronizeConfigurationScopes.addAll(params.getConfigurationScopeIds());
      }

    };
  }
}
