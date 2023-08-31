/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.clientapi;

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
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.clientapi.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.clientapi.client.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.clientapi.client.connection.GetCredentialsParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.GetCredentialsResponse;
import org.sonarsource.sonarlint.core.clientapi.client.event.DidReceiveServerEventParams;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeResponse;
import org.sonarsource.sonarlint.core.clientapi.client.hotspot.ShowHotspotParams;
import org.sonarsource.sonarlint.core.clientapi.client.http.CheckServerTrustedParams;
import org.sonarsource.sonarlint.core.clientapi.client.http.CheckServerTrustedResponse;
import org.sonarsource.sonarlint.core.clientapi.client.http.GetProxyPasswordAuthenticationParams;
import org.sonarsource.sonarlint.core.clientapi.client.http.GetProxyPasswordAuthenticationResponse;
import org.sonarsource.sonarlint.core.clientapi.client.http.ProxyDto;
import org.sonarsource.sonarlint.core.clientapi.client.http.SelectProxiesParams;
import org.sonarsource.sonarlint.core.clientapi.client.http.SelectProxiesResponse;
import org.sonarsource.sonarlint.core.clientapi.client.info.GetClientInfoResponse;
import org.sonarsource.sonarlint.core.clientapi.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.clientapi.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.clientapi.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.clientapi.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.clientapi.client.sync.DidSynchronizeConfigurationScopeParams;

public interface SonarLintClient {

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
  void showSmartNotification(ShowSmartNotificationParams params);

  /**
   * Ask the client to provide the dynamic information that can change during the runtime. Static information are provided during {@link SonarLintBackend#initialize(InitializeParams)}
   * in {@link org.sonarsource.sonarlint.core.clientapi.backend.initialize.ClientInfoDto}
   */
  @JsonRequest
  CompletableFuture<GetClientInfoResponse> getClientInfo();

  @JsonNotification
  void showHotspot(ShowHotspotParams params);

  /**
   * Can be triggered by the backend when trying to handle a feature that needs a connection, e.g. open hotspot.
   * @return the response to this connection creation assist request, that contains the new connection. The future can be canceled if the user stops the creation process
   */
  CompletableFuture<AssistCreatingConnectionResponse> assistCreatingConnection(AssistCreatingConnectionParams params);

  /**
   * Can be triggered by the backend when trying to handle a feature that needs a bound project, e.g. open hotspot.
   * @return the response to this binding assist request, that contains the bound project. The future can be canceled if the user stops the binding process
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
    var response = new SelectProxiesResponse(proxies.stream().map(SonarLintClient::convert).collect(Collectors.toList()));
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
  default void didReceiveServerEvent(DidReceiveServerEventParams params) {
    // not implemented
  }
}
