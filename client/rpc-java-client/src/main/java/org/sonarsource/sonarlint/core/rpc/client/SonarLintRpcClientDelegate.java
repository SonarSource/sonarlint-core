/*
 * SonarLint Core - RPC Java Client
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
package org.sonarsource.sonarlint.core.rpc.client;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.NoBindingSuggestionFoundParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.ConnectionSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerHotspotEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fix.FixSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.HotspotDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.GetProxyPasswordAuthenticationResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.ProxyDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.X509CertificateDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.IssueDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowSoonUnsupportedMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.plugin.DidSkipLoadingPluginParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryClientLiveAttributesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

/**
 * This is the interface that should be implemented by Java clients. We are trying to decouple from the RPC framework as much as possible,
 * but most of those methods should be pretty similar to {@link org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient}.
 * The "delegation" is made in {@link SonarLintRpcClientImpl}
 */
public interface SonarLintRpcClientDelegate {

  /**
   * Suggest a list of binding suggestions for each eligible configuration scope,
   * based on registered connections, config scope, binding clues, and git remote URL.
   * Scopes without any available suggestions are automatically excluded from the results.
   */
  void suggestBinding(Map<String, List<BindingSuggestionDto>> suggestionsByConfigScope);

  void suggestConnection(Map<String, List<ConnectionSuggestionDto>> suggestionsByConfigScope);

  void openUrlInBrowser(URL url);

  /**
   * Display a message to the user, usually in a small notification.
   * The message is informative and does not imply applying an action.
   */
  void showMessage(MessageType type, String text);

  void log(LogParams params);

  /**
   * Display a one-time message to the user as a small notification.
   * The message is informative and a link to the documentation should be available.
   * The one-time mechanism should be handled on the client side (via a "Don't show again" button for example).
   * There is an in-memory cache for the pair of connection ID + version that were already seen on the core side, but it is cleared after each restart.
   */
  void showSoonUnsupportedMessage(ShowSoonUnsupportedMessageParams params);

  void showSmartNotification(ShowSmartNotificationParams params);

  /**
   * Return the client dynamic description.
   * @see SonarLintRpcClient#getClientLiveInfo()
   */
  String getClientLiveDescription();

  void showHotspot(String configurationScopeId, HotspotDetailsDto hotspotDetails);

  /**
   * Sends a notification to the client to show a specific issue in the IDE
   */
  void showIssue(String configurationScopeId, IssueDetailsDto issueDetails);

  /**
   * Sends a notification to the client to show a fix suggestion for a specific issue in the IDE
   * The fix is only on a single files, but it may contain different locations
   */
  default void showFixSuggestion(String configurationScopeId, String issueKey, FixSuggestionDto fixSuggestion) {

  }

  /**
   * Can be triggered by the backend when trying to handle a feature that needs a connection, e.g. open hotspot.
   * @return the response to this connection creation assist request, that contains the new connection. The client can cancel the request if the user stops the creation process.
   * @throws java.util.concurrent.CancellationException if the client cancels the process
   */
  AssistCreatingConnectionResponse assistCreatingConnection(AssistCreatingConnectionParams params, SonarLintCancelChecker cancelChecker) throws CancellationException;

  /**
   * Can be triggered by the backend when trying to handle a feature that needs a bound project, e.g. open hotspot.
   * @return the response to this binding assist request, that contains the bound project. The client can cancel the request if the user stops the binding process.
   * @throws java.util.concurrent.CancellationException if the client cancels the process
   */
  AssistBindingResponse assistBinding(AssistBindingParams params, SonarLintCancelChecker cancelChecker) throws CancellationException;

  /**
   * Requests the client to start showing progress to users.
   * @throws UnsupportedOperationException if there is an error while creating the corresponding UI
   */
  void startProgress(StartProgressParams params) throws UnsupportedOperationException;

  /**
   * Reports progress to the client.
   */
  void reportProgress(ReportProgressParams params);

  void didSynchronizeConfigurationScopes(Set<String> configurationScopeIds);

  /**
   * @throws ConnectionNotFoundException if the connection doesn't exist on the client side
   * @return null if no credentials are available for this connection (backend may use unauthenticated HTTP requests)
   */
  @CheckForNull
  Either<TokenDto, UsernamePasswordDto> getCredentials(String connectionId) throws ConnectionNotFoundException;

  List<ProxyDto> selectProxies(URI uri);

  GetProxyPasswordAuthenticationResponse getProxyPasswordAuthentication(String host, int port, String protocol, String prompt, String scheme, URL targetHost);

  /**
   * @param chain the peer certificate chain
   * @param authType the key exchange algorithm used
   */
  boolean checkServerTrusted(List<X509CertificateDto> chain, String authType);

  @Deprecated(since = "10.3")
  default void didReceiveServerHotspotEvent(DidReceiveServerHotspotEvent params) {
    // no-op
  }

  /**
   * @return null if the client is unable to match the branch
   */
  @CheckForNull
  String matchSonarProjectBranch(String configurationScopeId, String mainBranchName, Set<String> allBranchesNames,
    SonarLintCancelChecker cancelChecker) throws ConfigScopeNotFoundException;

  @Deprecated(since = "10.23", forRemoval = true)
  default boolean matchProjectBranch(String configurationScopeId, String branchNameToMatch, SonarLintCancelChecker cancelChecker) {
    return true;
  }

  void didChangeMatchedSonarProjectBranch(String configScopeId, String newMatchedBranchName);

  TelemetryClientLiveAttributesResponse getTelemetryLiveAttributes();

  void didChangeTaintVulnerabilities(String configurationScopeId, Set<UUID> closedTaintVulnerabilityIds, List<TaintVulnerabilityDto> addedTaintVulnerabilities,
    List<TaintVulnerabilityDto> updatedTaintVulnerabilities);

  default void didChangeDependencyRisks(String configurationScopeId, Set<UUID> closedDependencyRiskIds, List<DependencyRiskDto> addedDependencyRisks,
    List<DependencyRiskDto> updatedDependencyRisks) {
  }

  default Path getBaseDir(String configurationScopeId) throws ConfigScopeNotFoundException {
    return null;
  }

  List<ClientFileDto> listFiles(String configScopeId) throws ConfigScopeNotFoundException;

  void noBindingSuggestionFound(NoBindingSuggestionFoundParams params);

  void didChangeAnalysisReadiness(Set<String> configurationScopeIds, boolean areReadyForAnalysis);

  default void raiseIssues(String configurationScopeId, Map<URI, List<RaisedIssueDto>> issuesByFileUri, boolean isIntermediatePublication, @Nullable UUID analysisId) {
  }

  default void raiseHotspots(String configurationScopeId, Map<URI, List<RaisedHotspotDto>> hotspotsByFileUri, boolean isIntermediatePublication, @Nullable UUID analysisId) {
  }

  default void didSkipLoadingPlugin(String configurationScopeId, Language language, DidSkipLoadingPluginParams.SkipReason reason, String minVersion,
    @Nullable String currentVersion) {
  }

  default void didDetectSecret(String configurationScopeId) {
  }

  default void promoteExtraEnabledLanguagesInConnectedMode(String configurationScopeId, Set<Language> languagesToPromote) {
  }

  default Map<String, String> getInferredAnalysisProperties(String configurationScopeId, List<URI> filesToAnalyze) throws ConfigScopeNotFoundException {
    return Map.of();
  }

  default Set<String> getFileExclusions(String configurationScopeId) throws ConfigScopeNotFoundException {
    return Collections.emptySet();
  }

  default void invalidToken(String connectionId) {
  }
}
