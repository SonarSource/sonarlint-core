/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.ClientConstantInfoDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.DidChangeNodeJsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.DidChangeAnalysisReadinessParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.NoBindingSuggestionFoundParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.DidChangeMatchedSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.MatchSonarProjectBranchParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.MatchSonarProjectBranchResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.GetCredentialsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerHotspotEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.ListFilesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.ListFilesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.ShowHotspotParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.CheckServerTrustedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.CheckServerTrustedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.GetProxyPasswordAuthenticationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.GetProxyPasswordAuthenticationResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.SelectProxiesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.SelectProxiesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.info.GetClientLiveInfoResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.ShowIssueParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowSoonUnsupportedMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.plugin.DidUpdatePluginsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.taint.vulnerability.DidChangeTaintVulnerabilitiesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryClientLiveAttributesResponse;

/**
 * This interface defines the RPC requests or notifications the backend can call on the client.
 */
public interface SonarLintRpcClient {

  /**
   * Suggest some bindings to the client, based on registered connections, config scope, and binding clues.
   */
  @JsonNotification
  void suggestBinding(SuggestBindingParams params);

  @JsonNotification
  void openUrlInBrowser(OpenUrlInBrowserParams params);

  /**
   * Display a message to the user, usually in a small notification.
   * The message is informative and does not imply applying an action.
   */
  @JsonNotification
  void showMessage(ShowMessageParams params);

  @JsonNotification
  void log(LogParams params);

  /**
   * Display a one-time message to the user as a small notification.
   * The message is informative and a link to the documentation should be available.
   * The one-time mechanism should be handled on the client side (via a "Don't show again" button for example).
   * There is an in-memory cache for the pair of connection ID + version that were already seen on the core side, but it is cleared after each restart.
   */
  @JsonNotification
  void showSoonUnsupportedMessage(ShowSoonUnsupportedMessageParams params);

  @JsonNotification
  void showSmartNotification(ShowSmartNotificationParams params);

  /**
   * Ask the client to provide its dynamic info that can change during the runtime. This is used as a complement to
   * static information provided during {@link SonarLintRpcServer#initialize(InitializeParams)}
   * in {@link ClientConstantInfoDto}.
   */
  @JsonRequest
  CompletableFuture<GetClientLiveInfoResponse> getClientLiveInfo();

  @JsonNotification
  void showHotspot(ShowHotspotParams params);

  /**
   * Sends a notification to the client to show a specific issue (specified by {@link ShowIssueParams}) in the IDE
   */
  @JsonNotification
  void showIssue(ShowIssueParams params);

  /**
   * Can be triggered by the backend when trying to handle a feature that needs a connection, e.g. open hotspot.
   *
   * @return the response to this connection creation assist request, that contains the new connection. The client can cancel the request if the user stops the creation process.
   * When cancelling the request from the client side, the error code should be {@link org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode#ServerCancelled}
   */
  @JsonRequest
  CompletableFuture<AssistCreatingConnectionResponse> assistCreatingConnection(AssistCreatingConnectionParams params);

  /**
   * Can be triggered by the backend when trying to handle a feature that needs a bound project, e.g. open hotspot.
   *
   * @return the response to this binding assist request, that contains the bound project. The client can cancel the request if the user stops the binding process.
   * When cancelling the request from the client side, the error code should be {@link org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode#ServerCancelled}
   */
  @JsonRequest
  CompletableFuture<AssistBindingResponse> assistBinding(AssistBindingParams params);

  /**
   * Requests the client to start showing progress to users.
   * If there is an error while creating the corresponding UI, clients can fail the returned future.
   * Tasks requesting the start of the progress should wait for the client to answer before continuing.
   */
  @JsonRequest
  CompletableFuture<Void> startProgress(StartProgressParams params);

  /**
   * Reports progress to the client.
   */
  @JsonNotification
  void reportProgress(ReportProgressParams params);

  @JsonNotification
  void didSynchronizeConfigurationScopes(DidSynchronizeConfigurationScopeParams params);

  /**
   * @throws org.eclipse.lsp4j.jsonrpc.ResponseErrorException with {@link org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode#CONNECTION_NOT_FOUND} if the connection doesn't exist on the client side
   */
  @JsonRequest
  CompletableFuture<GetCredentialsResponse> getCredentials(GetCredentialsParams params);

  @JsonRequest
  CompletableFuture<TelemetryClientLiveAttributesResponse> getTelemetryLiveAttributes();

  @JsonRequest
  CompletableFuture<SelectProxiesResponse> selectProxies(SelectProxiesParams params);

  /**
   * @throws org.eclipse.lsp4j.jsonrpc.ResponseErrorException with {@link ResponseErrorCode#InvalidParams} if the targetHostUrl is not a valid URL
   */
  @JsonRequest
  CompletableFuture<GetProxyPasswordAuthenticationResponse> getProxyPasswordAuthentication(GetProxyPasswordAuthenticationParams params);

  @JsonRequest
  CompletableFuture<CheckServerTrustedResponse> checkServerTrusted(CheckServerTrustedParams params);

  @JsonNotification
  void didReceiveServerHotspotEvent(DidReceiveServerHotspotEvent params);

  @JsonRequest
  CompletableFuture<MatchSonarProjectBranchResponse> matchSonarProjectBranch(MatchSonarProjectBranchParams params);

  @JsonNotification
  void didChangeMatchedSonarProjectBranch(DidChangeMatchedSonarProjectBranchParams params);

  /**
   * Called when at least one plugin has been downloaded during the full synchronization
   */
  @JsonNotification
  void didUpdatePlugins(DidUpdatePluginsParams params);

  /**
   * Must return all file paths for the given configuration scope.
   */
  @JsonRequest
  CompletableFuture<ListFilesResponse> listFiles(ListFilesParams params);

  /**
   * Called whenever there is a change in the list of taint vulnerabilities of a configuration scope. The change can be caused by:
   * <ul>
   *   <li>a synchronization</li>
   *   <li>a server event</li>
   *   <li>a taint vulnerability has been resolved by the client</li>
   *   <li>a vulnerability was on new code and is not anymore</li>
   * </ul>
   */
  @JsonNotification
  void didChangeTaintVulnerabilities(DidChangeTaintVulnerabilitiesParams params);

  @JsonNotification
  void didChangeNodeJs(DidChangeNodeJsParams params);

  @JsonNotification
  void noBindingSuggestionFound(NoBindingSuggestionFoundParams params);

  /**
   * Called when the backend is ready for analyzes to be triggered. The client is supposed to start analyzes only after receiving this notification.
   * The backend can also notify clients if analyzes become un-ready to be triggered. It can be the case when changing the binding and conditions are not met yet (e.g. no storage)
   */
  @JsonNotification
  void didChangeAnalysisReadiness(DidChangeAnalysisReadinessParams params);
}
