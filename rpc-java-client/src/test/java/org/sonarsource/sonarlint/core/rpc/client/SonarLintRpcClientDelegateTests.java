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

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionDto;
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
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.ListAllFilePathsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fs.ListAllFilePathsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.ShowHotspotParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.GetProxyPasswordAuthenticationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.ProxyDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.SelectProxiesParams;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SonarLintRpcClientDelegateTests {

  private ProxySelector defaultProxySelector;
  private Authenticator defaultAuthenticator;

  @BeforeEach
  void saveDefaults() {
    this.defaultProxySelector = ProxySelector.getDefault();
    this.defaultAuthenticator = Authenticator.getDefault();
  }

  @AfterEach
  void restoreDefaults() {
    ProxySelector.setDefault(defaultProxySelector);
    Authenticator.setDefault(defaultAuthenticator);
  }

  SonarLintRpcClientDelegate underTest = new DefaultSonarLintRpcClientDelegate();

  @Test
  void testDefaultProxyBehavior() throws ExecutionException, InterruptedException {
    ProxySelector.setDefault(new ProxySelector() {
      @Override
      public List<Proxy> select(URI uri) {
        if (uri.equals(URI.create("https://foo"))) {
          return List.of(
            new Proxy(Proxy.Type.HTTP, new InetSocketAddress("http://myproxy", 8085)),
            new Proxy(Proxy.Type.HTTP, new InetSocketAddress("http://myproxy2", 8086)));
        }
        return List.of(Proxy.NO_PROXY);
      }

      @Override
      public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {

      }
    });

    var selectProxiesResponse = underTest.selectProxies(new SelectProxiesParams("https://foo"), null);

    assertThat(selectProxiesResponse.getProxies()).extracting(ProxyDto::getType, ProxyDto::getHostname, ProxyDto::getPort)
      .containsExactly(tuple(Proxy.Type.HTTP, "http://myproxy", 8085),
        tuple(Proxy.Type.HTTP, "http://myproxy2", 8086));

    var selectProxiesResponseDirectProxy = underTest.selectProxies(new SelectProxiesParams("http://foo2"), null);

    assertThat(selectProxiesResponseDirectProxy.getProxies()).extracting(ProxyDto::getType, ProxyDto::getHostname, ProxyDto::getPort)
      .containsExactlyInAnyOrder(tuple(Proxy.Type.DIRECT, null, 0));
  }

  @Test
  void testDefaultAuthenticatorBehavior() throws ExecutionException, InterruptedException {

    Authenticator.setDefault(new Authenticator() {
      @Override
      protected PasswordAuthentication getPasswordAuthentication() {
        assertThat(getRequestingHost()).isEqualTo("https://foo");
        assertThat(getRequestingURL()).hasToString("http://targethost");
        assertThat(getRequestingPort()).isEqualTo(8085);
        assertThat(getRequestingProtocol()).isEqualTo("protocol");
        assertThat(getRequestingScheme()).isEqualTo("scheme");
        assertThat(getRequestingPrompt()).isEqualTo("prompt");
        assertThat(getRequestorType()).isEqualTo(RequestorType.PROXY);
        return new PasswordAuthentication("username", "password".toCharArray());
      }
    });

    var response = underTest.getProxyPasswordAuthentication(new GetProxyPasswordAuthenticationParams("https://foo", 8085, "protocol", "prompt", "scheme", "http://targethost"),
      null);
    assertThat(response.getProxyUser()).isEqualTo("username");
    assertThat(response.getProxyPassword()).isEqualTo("password");

  }

  @Test
  void failIfInvalidURL() throws ExecutionException, InterruptedException {
    var params = new GetProxyPasswordAuthenticationParams("https://foo", 8085, "protocol", "prompt", "scheme", "invalid:url");
    var e = assertThrows(ResponseErrorException.class, () -> underTest.getProxyPasswordAuthentication(params, null));
    assertThat(e).hasMessage("targetHostUrl is not a valid URL: invalid:url");
  }

  private static class DefaultSonarLintRpcClientDelegate implements SonarLintRpcClientDelegate {

    @Override
    public void showMessage(ShowMessageParams params) {

    }

    @Override
    public void log(LogParams params) {

    }

    @Override
    public void showSoonUnsupportedMessage(ShowSoonUnsupportedMessageParams params) {

    }

    @Override
    public void showSmartNotification(ShowSmartNotificationParams params) {

    }

    @Override
    public GetClientInfoResponse getClientInfo(CancelChecker cancelChecker) {
      return null;
    }

    @Override
    public void showHotspot(ShowHotspotParams params) {

    }

    @Override
    public void showIssue(ShowIssueParams params) {

    }

    @Override
    public AssistCreatingConnectionResponse assistCreatingConnection(AssistCreatingConnectionParams params, CancelChecker cancelChecker) {
      return null;
    }

    @Override
    public AssistBindingResponse assistBinding(AssistBindingParams params, CancelChecker cancelChecker) {
      return null;
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
    public void didReceiveServerTaintVulnerabilityRaisedEvent(DidReceiveServerTaintVulnerabilityRaisedEvent params) {

    }

    public void didReceiveServerTaintVulnerabilityChangedOrClosedEvent(DidReceiveServerTaintVulnerabilityChangedOrClosedEvent params) {

    }

    @Override
    public void didReceiveServerHotspotEvent(DidReceiveServerHotspotEvent params) {

    }

    @Override
    public MatchSonarProjectBranchResponse matchSonarProjectBranch(MatchSonarProjectBranchParams params, CancelChecker cancelChecker) {
      return null;
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
