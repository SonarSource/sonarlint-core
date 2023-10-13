/*
 * SonarLint Core - Medium Tests
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
package mediumTests;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintBackend;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.ClientInfoDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.FindFileByNamesInScopeResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.ShowHotspotParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.info.GetClientInfoResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.ShowIssueParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowSoonUnsupportedMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

import static org.assertj.core.api.Assertions.assertThat;

class SampleMediumTests {

  private ClientJsonRpcLauncher clientLauncher;
  private SonarLintBackend serverProxy;
  private PipedOutputStream clientToServerOutputStream;
  private PipedOutputStream serverToClientOutputStream;
  private final AtomicReference<Throwable> serverException = new AtomicReference<>();
  private BackendJsonRpcLauncher serverLauncher;

  @BeforeEach
  void startBackend() throws IOException, ExecutionException, InterruptedException {
    clientToServerOutputStream = new PipedOutputStream();
    var clientToServerInputStream = new PipedInputStream(clientToServerOutputStream);

    serverToClientOutputStream = new PipedOutputStream();
    var serverToClientInputStream = new PipedInputStream(serverToClientOutputStream);

    serverLauncher = new BackendJsonRpcLauncher(clientToServerInputStream, serverToClientOutputStream);


    clientLauncher = new ClientJsonRpcLauncher(serverToClientInputStream, clientToServerOutputStream, new FakeSonarLintClient());
    serverProxy = clientLauncher.getServerProxy();
  }

  private static class FakeSonarLintClient implements SonarLintClient {

    @Override
    public void suggestBinding(SuggestBindingParams params) {

    }

    @Override
    public CompletableFuture<FindFileByNamesInScopeResponse> findFileByNamesInScope(FindFileByNamesInScopeParams params) {
      return null;
    }

    @Override
    public void openUrlInBrowser(OpenUrlInBrowserParams params) {

    }

    @Override
    public void showMessage(ShowMessageParams params) {

    }

    @Override
    public void showSoonUnsupportedMessage(ShowSoonUnsupportedMessageParams params) {

    }

    @Override
    public void showSmartNotification(ShowSmartNotificationParams params) {

    }

    @Override
    public CompletableFuture<GetClientInfoResponse> getClientInfo() {
      return null;
    }

    @Override
    public void showHotspot(ShowHotspotParams params) {

    }

    @Override
    public void showIssue(ShowIssueParams params) {

    }

    @Override
    public CompletableFuture<AssistCreatingConnectionResponse> assistCreatingConnection(AssistCreatingConnectionParams params) {
      return null;
    }

    @Override
    public CompletableFuture<AssistBindingResponse> assistBinding(AssistBindingParams params) {
      return null;
    }

    @Override
    public CompletableFuture<Void> startProgress(StartProgressParams params) {
      return null;
    }

    @Override
    public void reportProgress(ReportProgressParams params) {

    }

    @Override
    public void didSynchronizeConfigurationScopes(DidSynchronizeConfigurationScopeParams params) {

    }

    @Override
    public CompletableFuture<GetCredentialsResponse> getCredentials(GetCredentialsParams params) {
      return null;
    }
  }


  @AfterEach
  void tearDown() throws Exception {
    clientLauncher.getServerProxy().shutdown().get();

    serverLauncher.close();
    clientLauncher.close();

    Thread.getAllStackTraces().keySet().stream().filter(t -> !t.isDaemon()).forEach(t -> System.out.println(t.getName()));

    assertThat(serverException).hasValue(null);
  }

  @Test
  void it_should_return_only_embedded_rules_of_enabled_languages(@TempDir Path storageRoot) throws ExecutionException, InterruptedException {
    serverProxy.initialize(new InitializeParams(new ClientInfoDto("mediumTests", "mediumTests", "mediumTests"),
      new FeatureFlagsDto(false, false, false, true, false, false, false),
      storageRoot, null, Set.of(getPluginPath("sonar-python-plugin-4.1.0.11333.jar")),
      Map.of(), Set.of(Language.PYTHON), Set.of(), List.of(), List.of(), null, Map.of(), false)).get();

    var allRules = serverProxy.getRulesService().listAllStandaloneRulesDefinitions().get().getRulesByKey().values();

    assertThat(allRules).isNotEmpty();

    assertThat(serverLauncher.getJavaImpl().getEmbeddedServerPort()).isNotZero();
  }

  private static Path getPluginPath(String file) {
    var path = Paths.get("target/plugins/").resolve(file);
    if (!Files.isRegularFile(path)) {
      throw new IllegalStateException("Unable to find file " + path);
    }
    return path;
  }

}
