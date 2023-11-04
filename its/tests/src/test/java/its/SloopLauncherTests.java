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

import its.utils.PluginLocator;
import its.utils.SloopDistLocator;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.client.SloopLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.ClientInfoDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.DidChangeMatchedSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.MatchSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.MatchSonarProjectBranchResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerTaintVulnerabilityRaisedEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.FindFileByNamesInScopeResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.ShowHotspotParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.ProxyDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.SelectProxiesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.SelectProxiesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.info.GetClientInfoResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.ShowIssueParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowSoonUnsupportedMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sync.DidSynchronizeConfigurationScopeParams;

import static its.utils.UnArchiveUtils.unarchiveDistribution;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.GO;

class SloopLauncherTests {

  @TempDir
  private static Path sonarUserHome;

  @TempDir
  private static Path unarchiveTmpDir;

  private static SonarLintRpcServer server;

  @BeforeAll
  static void setup() {
    var sloopDistPath = SystemUtils.IS_OS_WINDOWS ? SloopDistLocator.getWindowsDistPath() : SloopDistLocator.getLinux64DistPath();
    var sloopOutDirPath = unarchiveTmpDir.resolve("sloopDistOut");
    unarchiveDistribution(sloopDistPath.toString(), sloopOutDirPath);
    server = SloopLauncher.startSonarLintRpcServer(sloopOutDirPath.toAbsolutePath().toString(), new DummySonarLintRpcClient());
  }

  @AfterAll
  static void tearDown() throws ExecutionException, InterruptedException {
    server.shutdown().get();
    var exitCode = SloopLauncher.waitFor();
    assertThat(exitCode).isZero();
  }

  @Test
  void test_all_rules_returns() throws Exception {
    var clientInfo = new ClientInfoDto("clientName", "integrationTests", "SonarLint ITs");
    var featureFlags = new FeatureFlagsDto(false, false, false, false, false, false);

    server.initialize(new InitializeParams(clientInfo, featureFlags, sonarUserHome.resolve("storage"), sonarUserHome.resolve("workDir"),
      Set.of(PluginLocator.getGoPluginPath().toAbsolutePath()), Collections.emptyMap(), Set.of(GO), Collections.emptySet(), Collections.emptyList(),
      Collections.emptyList(), sonarUserHome.toString(), Map.of(), false)).get();

    var result = server.getRulesService().listAllStandaloneRulesDefinitions().get();
    assertThat(result.getRulesByKey()).hasSize(36);
  }

  static class DummySonarLintRpcClient implements SonarLintRpcClient {
    final List<LogParams> logs = new ArrayList<>();

    public List<LogParams> getLogs() {
      return logs;
    }

    public void clearLogs(){
      logs.clear();
    }

    @Override
    public void suggestBinding(SuggestBindingParams params) {
    }

    @Override
    public CompletableFuture<FindFileByNamesInScopeResponse> findFileByNamesInScope(FindFileByNamesInScopeParams params) {
      return CompletableFuture.completedFuture(new FindFileByNamesInScopeResponse(Collections.emptyList()));
    }

    @Override
    public void openUrlInBrowser(OpenUrlInBrowserParams params) {

    }

    @Override
    public void showMessage(ShowMessageParams params) {

    }

    @Override
    public void log(LogParams params) {
      logs.add(params);
    }

    @Override
    public void showSoonUnsupportedMessage(ShowSoonUnsupportedMessageParams params) {

    }

    @Override
    public void showSmartNotification(ShowSmartNotificationParams params) {

    }

    @Override
    public CompletableFuture<GetClientInfoResponse> getClientInfo() {
      return CompletableFuture.completedFuture(new GetClientInfoResponse(""));
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
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void reportProgress(ReportProgressParams params) {

    }

    @Override
    public void didSynchronizeConfigurationScopes(DidSynchronizeConfigurationScopeParams params) {

    }

    public CompletableFuture<GetCredentialsResponse> getCredentials(GetCredentialsParams params) {
      return null;
    }

    @Override
    public CompletableFuture<SelectProxiesResponse> selectProxies(SelectProxiesParams params) {
      return CompletableFuture.completedFuture(new SelectProxiesResponse(List.of(ProxyDto.NO_PROXY)));
    }

    @Override
    public void didReceiveServerTaintVulnerabilityRaisedEvent(DidReceiveServerTaintVulnerabilityRaisedEvent params) {
    }

    @Override
    public CompletableFuture<MatchSonarProjectBranchResponse> matchSonarProjectBranch(MatchSonarProjectBranchParams params) {
      return null;
    }

    @Override
    public void didChangeMatchedSonarProjectBranch(DidChangeMatchedSonarProjectBranchParams params) {

    }
  }
}
