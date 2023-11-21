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

import its.utils.PluginLocator;
import its.utils.SloopDistLocator;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.client.SloopLauncher;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.ClientInfoDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.DidChangeMatchedSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.MatchSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.MatchSonarProjectBranchResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerHotspotEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerTaintVulnerabilityChangedOrClosedEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerTaintVulnerabilityRaisedEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.FindFileByNamesInScopeResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.ListAllFilePathsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.ListAllFilePathsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.ShowHotspotParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.ProxyDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.SelectProxiesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.SelectProxiesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.info.GetClientInfoResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.ShowIssueParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowSoonUnsupportedMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.plugin.DidUpdatePluginsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryConstantAttributesDto;

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
    var telemetryInitDto = new TelemetryConstantAttributesDto("SonarLint ITs", "SonarLint ITs",
      "1.2.3", "4.5.6", "linux", "x64", Collections.emptyMap());
    var clientInfo = new ClientInfoDto("clientName", "integrationTests", telemetryInitDto);
    var featureFlags = new FeatureFlagsDto(false, false, false, false, false, false, false, false);

    server.initialize(new InitializeParams(clientInfo, featureFlags, sonarUserHome.resolve("storage"), sonarUserHome.resolve("workDir"),
      Set.of(PluginLocator.getGoPluginPath().toAbsolutePath()), Collections.emptyMap(), Set.of(GO), Collections.emptySet(), Collections.emptyList(),
      Collections.emptyList(), sonarUserHome.toString(), Map.of(), false)).get();

    var result = server.getRulesService().listAllStandaloneRulesDefinitions().get();
    assertThat(result.getRulesByKey()).hasSize(36);
  }

  static class DummySonarLintRpcClient implements SonarLintRpcClientDelegate {
    final List<LogParams> logs = new ArrayList<>();

    public List<LogParams> getLogs() {
      return logs;
    }

    public void clearLogs() {
      logs.clear();
    }

    @Override
    public void suggestBinding(SuggestBindingParams params) {
    }

    @Override
    public FindFileByNamesInScopeResponse findFileByNamesInScope(FindFileByNamesInScopeParams params, CancelChecker cancelChecker) {
      return new FindFileByNamesInScopeResponse(Collections.emptyList());
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
    public GetClientInfoResponse getClientInfo(CancelChecker cancelChecker) {
      return new GetClientInfoResponse("");
    }

    @Override
    public void showHotspot(ShowHotspotParams params) {

    }

    @Override
    public void showIssue(ShowIssueParams params) {

    }

    @Override
    public void startProgress(StartProgressParams params, CancelChecker cancelChecker) {
    }

    @Override
    public void reportProgress(ReportProgressParams params) {

    }

    @Override
    public void didSynchronizeConfigurationScopes(DidSynchronizeConfigurationScopeParams params) {

    }

    @Override
    public GetCredentialsResponse getCredentials(GetCredentialsParams params, CancelChecker cancelChecker) {
      return null;
    }

    @Override
    public SelectProxiesResponse selectProxies(SelectProxiesParams params, CancelChecker cancelChecker) {
      return new SelectProxiesResponse(List.of(ProxyDto.NO_PROXY));
    }

    @Override
    public void didReceiveServerTaintVulnerabilityRaisedEvent(DidReceiveServerTaintVulnerabilityRaisedEvent params) {
    }

    @Override
    public void didReceiveServerTaintVulnerabilityChangedOrClosedEvent(DidReceiveServerTaintVulnerabilityChangedOrClosedEvent params) {

    }

    @Override
    public void didReceiveServerHotspotEvent(DidReceiveServerHotspotEvent params) {

    }

    @Override
    public MatchSonarProjectBranchResponse matchSonarProjectBranch(MatchSonarProjectBranchParams params, CancelChecker cancelChecker) {
      return new MatchSonarProjectBranchResponse(null);
    }

    @Override
    public void didChangeMatchedSonarProjectBranch(DidChangeMatchedSonarProjectBranchParams params) {

    }

    @Override
    public void didUpdatePlugins(DidUpdatePluginsParams params) {

    }

    @Override
    public ListAllFilePathsResponse listAllFilePaths(ListAllFilePathsParams params) {
      return null;
    }
  }
}
