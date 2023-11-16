/*
 * SonarLint Core - RPC Java Client
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
package org.sonarsource.sonarlint.core.rpc.client;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.ClientInfoDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.ClientErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingResponse;
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
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.FoundFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.ListAllFilePathsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.ListAllFilePathsResponse;
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
import org.sonarsource.sonarlint.core.rpc.protocol.client.plugin.DidUpdatePluginsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sync.DidSynchronizeConfigurationScopeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryLiveAttributesResponse;

public interface SonarLintRpcClientDelegate {

  default void suggestBinding(Map<String, List<BindingSuggestionDto>> suggestionsByConfigScope) {
    // Do nothing by default
  }

  default List<FoundFileDto> findFileByNamesInScope(String configScopeId, List<String> filenames, CancelChecker cancelChecker) {
    return List.of();
  }

  void openUrlInBrowser(OpenUrlInBrowserParams params);

  /**
   * Display a message to the user, usually in a small notification.
   * The message is informative and does not imply applying an action.
   */
  void showMessage(ShowMessageParams params);

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
   * Ask the client to provide the dynamic information that can change during the runtime. Static information are provided during {@link SonarLintRpcServer#initialize(InitializeParams)}
   * in {@link ClientInfoDto}
   */
  GetClientInfoResponse getClientInfo(CancelChecker cancelChecker);

  void showHotspot(ShowHotspotParams params);

  /**
   * Sends a notification to the client to show a specific issue (specified by {@link ShowIssueParams}) in the IDE
   */
  void showIssue(ShowIssueParams params);

  /**
   * Can be triggered by the backend when trying to handle a feature that needs a connection, e.g. open hotspot.
   * @return the response to this connection creation assist request, that contains the new connection. The client can cancel the request if the user stops the creation process.
   * @throws java.util.concurrent.CancellationException if the client cancels the process
   */
  default AssistCreatingConnectionResponse assistCreatingConnection(AssistCreatingConnectionParams params, CancelChecker cancelChecker) {
    throw new CancellationException("Not implemented");
  }

  /**
   * Can be triggered by the backend when trying to handle a feature that needs a bound project, e.g. open hotspot.
   * @return the response to this binding assist request, that contains the bound project. The client can cancel the request if the user stops the binding process.
   * @throws java.util.concurrent.CancellationException if the client cancels the process
   */
  default AssistBindingResponse assistBinding(AssistBindingParams params, CancelChecker cancelChecker) {
    throw new CancellationException("Not implemented");
  }

  /**
   * Requests the client to start showing progress to users.
   * @throws org.eclipse.lsp4j.jsonrpc.ResponseErrorException with {@link ClientErrorCode#PROGRESS_CREATION_FAILED} if there is an error while creating the corresponding UI
   */
  default void startProgress(StartProgressParams params, CancelChecker cancelChecker) {
    throw new ResponseErrorException(new ResponseError(ClientErrorCode.PROGRESS_CREATION_FAILED, "Progress not supported", null));
  }

  /**
   * Reports progress to the client.
   */
  void reportProgress(ReportProgressParams params);

  void didSynchronizeConfigurationScopes(DidSynchronizeConfigurationScopeParams params);

  /**
   * @throws org.eclipse.lsp4j.jsonrpc.ResponseErrorException with {@link ClientErrorCode#CONNECTION_NOT_FOUND} if the connection doesn't exist on the client side
   */
  GetCredentialsResponse getCredentials(GetCredentialsParams params, CancelChecker cancelChecker);

  default SelectProxiesResponse selectProxies(SelectProxiesParams params, CancelChecker cancelChecker) {
    var proxies = ProxySelector.getDefault().select(URI.create(params.getUri()));
    return new SelectProxiesResponse(proxies.stream().map(SonarLintRpcClientDelegate::convert).collect(Collectors.toList()));
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

  /**
   * @throws org.eclipse.lsp4j.jsonrpc.ResponseErrorException with {@link ResponseErrorCode#InvalidParams} if the targetHostUrl is not a valid URL
   */
  default GetProxyPasswordAuthenticationResponse getProxyPasswordAuthentication(GetProxyPasswordAuthenticationParams params, CancelChecker cancelChecker) {
    // use null addr, because the authentication fails if it does not exactly match the expected realm's host
    URL targetHostUrl;
    try {
      targetHostUrl = new URL(params.getTargetHostURL());
    } catch (MalformedURLException e) {
      throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, "targetHostUrl is not a valid URL: " + params.getTargetHostURL(), null));
    }
    var passwordAuthentication = Authenticator.requestPasswordAuthentication(params.getHost(), null, params.getPort(), params.getProtocol(), params.getPrompt(), params.getScheme(),
      targetHostUrl, Authenticator.RequestorType.PROXY);
    return new GetProxyPasswordAuthenticationResponse(passwordAuthentication != null ? passwordAuthentication.getUserName() : null,
      passwordAuthentication != null ? new String(passwordAuthentication.getPassword()) : null);
  }

  default CheckServerTrustedResponse checkServerTrusted(CheckServerTrustedParams params, CancelChecker cancelChecker) {
    return new CheckServerTrustedResponse(false);
  }

  void didReceiveServerTaintVulnerabilityRaisedEvent(DidReceiveServerTaintVulnerabilityRaisedEvent params);

  void didReceiveServerTaintVulnerabilityChangedOrClosedEvent(DidReceiveServerTaintVulnerabilityChangedOrClosedEvent params);

  void didReceiveServerHotspotEvent(DidReceiveServerHotspotEvent params);

  MatchSonarProjectBranchResponse matchSonarProjectBranch(MatchSonarProjectBranchParams params, CancelChecker cancelChecker);

  void didChangeMatchedSonarProjectBranch(DidChangeMatchedSonarProjectBranchParams params);
  void didUpdatePlugins(DidUpdatePluginsParams params);
  ListAllFilePathsResponse listAllFilePaths(ListAllFilePathsParams params);

  default TelemetryLiveAttributesResponse getTelemetryLiveAttributes() {
    throw new CancellationException("Not implemented");
  }
}
