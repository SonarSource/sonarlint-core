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
package mediumtest.sloop;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.client.ConnectionNotFoundException;
import org.sonarsource.sonarlint.core.rpc.client.Sloop;
import org.sonarsource.sonarlint.core.rpc.client.SloopLauncher;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.ClientConstantInfoDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.HttpConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.TelemetryClientConstantAttributesDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetEffectiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerHotspotEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.HotspotDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.GetProxyPasswordAuthenticationResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.ProxyDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.X509CertificateDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.IssueDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowSoonUnsupportedMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryClientLiveAttributesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;
import testutils.PluginLocator;

import static mediumtest.sloop.UnArchiveUtils.unarchiveDistribution;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.PHP;

class SloopLauncherTests {

  @TempDir
  private static Path sonarUserHome;

  @TempDir
  private static Path unarchiveTmpDir;

  private static Sloop sloop;
  private static SonarLintRpcServer server;
  private static Path sloopOutDirPath;
  private Integer exitValue;
  private boolean shutdownRequested;

  @BeforeAll
  static void setup() {
    var sloopDistPath = SystemUtils.IS_OS_WINDOWS ? SloopDistLocator.getWindowsDistPath() : SloopDistLocator.getLinux64DistPath();
    sloopOutDirPath = unarchiveTmpDir.resolve("sloopDistOut");
    unarchiveDistribution(sloopDistPath.toString(), sloopOutDirPath);
  }

  @BeforeEach
  void start() {
    shutdownRequested = false;
    exitValue = null;
    var sloopLauncher = new SloopLauncher(new DummySonarLintRpcClient());
    sloop = sloopLauncher.start(sloopOutDirPath.toAbsolutePath());
    server = sloop.getRpcServer();
  }

  @AfterEach
  void tearDown() {
    if (!shutdownRequested) {
      sloop.shutdown().join();
    }
  }

  @Test
  void test_all_rules_returns() throws Exception {
    var telemetryInitDto = new TelemetryClientConstantAttributesDto("SonarLint ITs", "SonarLint ITs",
      "1.2.3", "4.5.6", Collections.emptyMap());
    var clientInfo = new ClientConstantInfoDto("clientName", "integrationTests");
    var featureFlags = new FeatureFlagsDto(false, false, false, false, false, false, false, false, false);

    server.initialize(new InitializeParams(clientInfo, telemetryInitDto, HttpConfigurationDto.defaultConfig(), null, featureFlags, sonarUserHome.resolve("storage"), sonarUserHome.resolve("workDir"),
      Set.of(PluginLocator.getPhpPluginPath().toAbsolutePath()), Collections.emptyMap(), Set.of(PHP), Collections.emptySet(), Collections.emptyList(),
      Collections.emptyList(), sonarUserHome.toString(), Map.of(), false, null)).get();

    var result = server.getRulesService().listAllStandaloneRulesDefinitions().get();
    assertThat(result.getRulesByKey()).hasSize(219);

    server.getConfigurationService()
      .didAddConfigurationScopes(new DidAddConfigurationScopesParams(List.of(new ConfigurationScopeDto("myConfigScope", null, true, "My Config Scope", null))));

    var result2 = server.getRulesService().getEffectiveRuleDetails(new GetEffectiveRuleDetailsParams("myConfigScope", "php:S100", null)).join();
    assertThat(result2.details().getName()).isEqualTo("Method and function names should comply with a naming convention");
  }

  @Test
  void it_should_complete_onExit_future_when_process_exits() {
    var telemetryInitDto = new TelemetryClientConstantAttributesDto("SonarLint ITs", "SonarLint ITs",
      "1.2.3", "4.5.6", Collections.emptyMap());
    var clientInfo = new ClientConstantInfoDto("clientName", "integrationTests");
    var featureFlags = new FeatureFlagsDto(false, false, false, false, false, false, false, false, false);
    server.initialize(new InitializeParams(clientInfo, telemetryInitDto, HttpConfigurationDto.defaultConfig(), null, featureFlags, sonarUserHome.resolve("storage"), sonarUserHome.resolve("workDir"),
      Set.of(PluginLocator.getPhpPluginPath().toAbsolutePath()), Collections.emptyMap(), Set.of(PHP), Collections.emptySet(), Collections.emptyList(),
      Collections.emptyList(), sonarUserHome.toString(), Map.of(), false, null)).join();
    sloop.onExit().thenAccept(exitValue -> this.exitValue = exitValue);

    shutdownRequested = true;
    sloop.shutdown().join();

    // it can take some time for the process to finish
    await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(exitValue).isZero());
  }

  static class DummySonarLintRpcClient implements SonarLintRpcClientDelegate {
    final Queue<LogParams> logs = new ConcurrentLinkedQueue<>();

    public Queue<LogParams> getLogs() {
      return logs;
    }

    @Override
    public void suggestBinding(Map<String, List<BindingSuggestionDto>> suggestionsByConfigScope) {

    }

    @Override
    public void openUrlInBrowser(URL url) {

    }

    @Override
    public void showMessage(MessageType type, String text) {

    }

    @Override
    public void log(LogParams params) {
      logs.add(params);
      var log = new StringBuilder();
      log.append("[").append(params.getThreadName()).append("] ");
      log.append(params.getLevel()).append(" ").append(params.getMessage());
      if (params.getConfigScopeId() != null) {
        log.append(" [").append(params.getConfigScopeId()).append("]");
      }
      System.out.println(log);
    }

    @Override
    public void showSoonUnsupportedMessage(ShowSoonUnsupportedMessageParams params) {

    }

    @Override
    public void showSmartNotification(ShowSmartNotificationParams params) {

    }

    @Override
    public String getClientLiveDescription() {
      return "";
    }

    @Override
    public void showHotspot(String configurationScopeId, HotspotDetailsDto hotspotDetails) {

    }

    @Override
    public void showIssue(String configurationScopeId, IssueDetailsDto issueDetails) {

    }

    @Override
    public AssistCreatingConnectionResponse assistCreatingConnection(AssistCreatingConnectionParams params, CancelChecker cancelChecker) throws CancellationException {
      throw new CancellationException();
    }

    @Override
    public AssistBindingResponse assistBinding(AssistBindingParams params, CancelChecker cancelChecker) throws CancellationException {
      throw new CancellationException();
    }

    @Override
    public void startProgress(StartProgressParams params) throws UnsupportedOperationException {

    }

    @Override
    public void reportProgress(ReportProgressParams params) {

    }

    @Override
    public void didSynchronizeConfigurationScopes(Set<String> configurationScopeIds) {

    }

    @Override
    public Either<TokenDto, UsernamePasswordDto> getCredentials(String connectionId) throws ConnectionNotFoundException {
      throw new ConnectionNotFoundException();
    }

    @Override
    public List<ProxyDto> selectProxies(URI uri) {
      return List.of(ProxyDto.NO_PROXY);
    }

    @Override
    public GetProxyPasswordAuthenticationResponse getProxyPasswordAuthentication(String host, int port, String protocol, String prompt, String scheme, URL targetHost) {
      return new GetProxyPasswordAuthenticationResponse(null, null);
    }

    @Override
    public boolean checkServerTrusted(List<X509CertificateDto> chain, String authType) {
      return false;
    }

    @Override
    public void didReceiveServerHotspotEvent(DidReceiveServerHotspotEvent params) {

    }

    @Override
    public String matchSonarProjectBranch(String configurationScopeId, String mainBranchName, Set<String> allBranchesNames, CancelChecker cancelChecker) {
      return null;
    }

    @Override
    public void didChangeMatchedSonarProjectBranch(String configScopeId, String newMatchedBranchName) {

    }

    @Override
    public TelemetryClientLiveAttributesResponse getTelemetryLiveAttributes() {
      System.err.println("Telemetry should be disabled in tests");
      throw new CancellationException("Telemetry should be disabled in tests");
    }

    @Override
    public void didChangeTaintVulnerabilities(String configurationScopeId, Set<UUID> closedTaintVulnerabilityIds, List<TaintVulnerabilityDto> addedTaintVulnerabilities,
      List<TaintVulnerabilityDto> updatedTaintVulnerabilities) {
    }

    @Override
    public List<ClientFileDto> listFiles(String configScopeId) {
      return List.of();
    }

    @Override
    public void noBindingSuggestionFound(String projectKey) {

    }

    @Override
    public void didChangeAnalysisReadiness(Set<String> configurationScopeIds, boolean areReadyForAnalysis) {

    }
  }
}
