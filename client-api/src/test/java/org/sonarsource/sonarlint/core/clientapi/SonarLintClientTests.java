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

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.clientapi.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.clientapi.client.binding.SuggestBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.clientapi.client.connection.GetCredentialsParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.GetCredentialsResponse;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeResponse;
import org.sonarsource.sonarlint.core.clientapi.client.host.GetHostInfoResponse;
import org.sonarsource.sonarlint.core.clientapi.client.hotspot.ShowHotspotParams;
import org.sonarsource.sonarlint.core.clientapi.client.http.GetProxyPasswordAuthenticationParams;
import org.sonarsource.sonarlint.core.clientapi.client.http.ProxyDto;
import org.sonarsource.sonarlint.core.clientapi.client.http.SelectProxiesParams;
import org.sonarsource.sonarlint.core.clientapi.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.clientapi.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.clientapi.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.clientapi.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.clientapi.client.sync.DidSynchronizeConfigurationScopeParams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class SonarLintClientTests {

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

  SonarLintClient underTest = new DefaultSonarLintClient();

  @Test
  void testDefaultProxyBehavior() throws ExecutionException, InterruptedException {
    ProxySelector.setDefault(new ProxySelector() {
      @Override
      public List<Proxy> select(URI uri) {
        if (uri.equals(URI.create("http://foo"))) {
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

    var selectProxiesResponse = underTest.selectProxies(new SelectProxiesParams("http://foo")).get();

    assertThat(selectProxiesResponse.getProxies()).extracting(ProxyDto::getType, ProxyDto::getHostname, ProxyDto::getPort)
      .containsExactly(tuple(Proxy.Type.HTTP, "http://myproxy", 8085),
        tuple(Proxy.Type.HTTP, "http://myproxy2", 8086));

    var selectProxiesResponseDirectProxy = underTest.selectProxies(new SelectProxiesParams("http://foo2")).get();

    assertThat(selectProxiesResponseDirectProxy.getProxies()).extracting(ProxyDto::getType, ProxyDto::getHostname, ProxyDto::getPort)
      .containsExactlyInAnyOrder(tuple(Proxy.Type.DIRECT, null, 0));
  }

  @Test
  void testDefaultAuthenticatorBehavior() throws ExecutionException, InterruptedException {

    Authenticator.setDefault(new Authenticator() {
      @Override
      protected PasswordAuthentication getPasswordAuthentication() {
        assertThat(getRequestingHost()).isEqualTo("http://foo");
        assertThat(getRequestingURL().toString()).isEqualTo("http://targethost");
        assertThat(getRequestingPort()).isEqualTo(8085);
        assertThat(getRequestingProtocol()).isEqualTo("protocol");
        assertThat(getRequestingScheme()).isEqualTo("scheme");
        assertThat(getRequestingPrompt()).isEqualTo("prompt");
        assertThat(getRequestorType()).isEqualTo(RequestorType.PROXY);
        return new PasswordAuthentication("username", "password".toCharArray());
      }
    });

    var response = underTest.getProxyPasswordAuthentication(new GetProxyPasswordAuthenticationParams("http://foo", 8085, "protocol", "prompt", "scheme", "http://targethost"))
      .get();
    assertThat(response.getProxyUser()).isEqualTo("username");
    assertThat(response.getProxyPassword()).isEqualTo("password");

  }

  @Test
  void failIfInvalidURL() throws ExecutionException, InterruptedException {
    var future = underTest.getProxyPasswordAuthentication(new GetProxyPasswordAuthenticationParams("http://foo", 8085, "protocol", "prompt", "scheme", "invalid:url"));
    assertThat(future).failsWithin(Duration.ofMillis(50))
      .withThrowableOfType(ExecutionException.class)
      .withMessage("java.net.MalformedURLException: unknown protocol: invalid");
  }

  private static class DefaultSonarLintClient implements SonarLintClient {
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
    public void showSmartNotification(ShowSmartNotificationParams params) {

    }

    @Override
    public CompletableFuture<GetHostInfoResponse> getHostInfo() {
      return null;
    }

    @Override
    public void showHotspot(ShowHotspotParams params) {

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
}
