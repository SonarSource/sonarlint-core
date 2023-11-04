/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.ClientInfoDto;
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
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerHotspotEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerTaintVulnerabilityChangedOrClosedEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerTaintVulnerabilityRaisedEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.FindFileByNamesInScopeResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.ShowHotspotParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.CheckServerTrustedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.CheckServerTrustedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.GetProxyPasswordAuthenticationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.GetProxyPasswordAuthenticationResponse;
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

public interface SonarLintRpcClient {

  @JsonNotification
  void suggestBinding(SuggestBindingParams params);

  @JsonRequest
  CompletableFuture<FindFileByNamesInScopeResponse> findFileByNamesInScope(FindFileByNamesInScopeParams params);

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
   * Ask the client to provide the dynamic information that can change during the runtime. Static information are provided during {@link SonarLintRpcServer#initialize(InitializeParams)}
   * in {@link ClientInfoDto}
   */
  @JsonRequest
  CompletableFuture<GetClientInfoResponse> getClientInfo();

  @JsonNotification
  void showHotspot(ShowHotspotParams params);

  /**
   * Sends a notification to the client to show a specific issue (specified by {@link ShowIssueParams}) in the IDE
   */
  @JsonNotification
  void showIssue(ShowIssueParams params);

  /**
   * Can be triggered by the backend when trying to handle a feature that needs a connection, e.g. open hotspot.
   * @return the response to this connection creation assist request, that contains the new connection. The client can cancel the request if the user stops the creation process.
   * When cancelling the request from the client side, the error code should be {@link org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode#ServerCancelled}
   */
  @JsonRequest
  CompletableFuture<AssistCreatingConnectionResponse> assistCreatingConnection(AssistCreatingConnectionParams params);

  /**
   * Can be triggered by the backend when trying to handle a feature that needs a bound project, e.g. open hotspot.
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

  @JsonRequest
  CompletableFuture<GetCredentialsResponse> getCredentials(GetCredentialsParams params);

  @JsonRequest
  default CompletableFuture<SelectProxiesResponse> selectProxies(SelectProxiesParams params) {
    var proxies = ProxySelector.getDefault().select(URI.create(params.getUri()));
    var response = new SelectProxiesResponse(proxies.stream().map(SonarLintRpcClient::convert).collect(Collectors.toList()));
    return CompletableFuture.completedFuture(response);
  }

  private static ProxyDto convert(Proxy proxy) {
    if (proxy.type() == Proxy.Type.DIRECT) {
      return ProxyDto.NO_PROXY;
    }
    var address = (InetSocketAddress) proxy.address();
    var server = address.getHostString();
    var port = address.getPort();
    return new ProxyDto(proxy.type(), server, port);
  }

  @JsonRequest
  default CompletableFuture<GetProxyPasswordAuthenticationResponse> getProxyPasswordAuthentication(GetProxyPasswordAuthenticationParams params) {
    // use null addr, because the authentication fails if it does not exactly match the expected realm's host
    URL targetHostUrl;
    try {
      targetHostUrl = new URL(params.getTargetHostURL());
    } catch (MalformedURLException e) {
      return CompletableFuture.failedFuture(e);
    }
    var passwordAuthentication = Authenticator.requestPasswordAuthentication(params.getHost(), null, params.getPort(), params.getProtocol(), params.getPrompt(), params.getScheme(),
      targetHostUrl, Authenticator.RequestorType.PROXY);
    var response = new GetProxyPasswordAuthenticationResponse(passwordAuthentication != null ? passwordAuthentication.getUserName() : null,
      passwordAuthentication != null ? new String(passwordAuthentication.getPassword()) : null);
    return CompletableFuture.completedFuture(response);
  }

  @JsonRequest
  default CompletableFuture<CheckServerTrustedResponse> checkServerTrusted(CheckServerTrustedParams params) {
    return CompletableFuture.completedFuture(new CheckServerTrustedResponse(false));
  }

  @JsonNotification
  default void didReceiveServerTaintVulnerabilityRaisedEvent(DidReceiveServerTaintVulnerabilityRaisedEvent params) {
    // not implemented
  }


  @JsonNotification
  default void didReceiveServerTaintVulnerabilityChangedOrClosedEvent(DidReceiveServerTaintVulnerabilityChangedOrClosedEvent params) {
    // not implemented
  }

  @JsonNotification
  default void didReceiveServerHotspotEvent(DidReceiveServerHotspotEvent params) {
    // not implemented
  }

  @JsonRequest
  CompletableFuture<MatchSonarProjectBranchResponse> matchSonarProjectBranch(MatchSonarProjectBranchParams params);

  @JsonNotification
  void didChangeMatchedSonarProjectBranch(DidChangeMatchedSonarProjectBranchParams params);
}
