/*
 * SonarLint Core - HTTP
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
package org.sonarsource.sonarlint.core.http;

import java.net.ProxySelector;
import javax.annotation.Nullable;
import javax.net.ssl.X509TrustManager;
import nl.altindag.ssl.SSLFactory;
import nl.altindag.ssl.util.TrustManagerUtils;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;

public class HttpClientProvider {

  private static final Timeout CONNECTION_TIMEOUT = Timeout.ofSeconds(30);
  private static final Timeout RESPONSE_TIMEOUT = Timeout.ofMinutes(10);
  private final CloseableHttpAsyncClient sharedClient;

  /**
   * Return an {@link HttpClientProvider} made for testing, with a dummy user agent, and basic configuration regarding proxy/SSL
   */
  public static HttpClientProvider forTesting() {
    return new HttpClientProvider("SonarLint tests", TrustManagerUtils.createDummyTrustManager(), ProxySelector.getDefault(), new BasicCredentialsProvider());
  }

  public HttpClientProvider(String userAgent, X509TrustManager confirmingTrustManager, ProxySelector proxySelector,
    CredentialsProvider proxyCredentialsProvider) {
    var sslFactory = SSLFactory.builder()
      .withDefaultTrustMaterial()
      .withSystemTrustMaterial()
      .withTrustMaterial(confirmingTrustManager)
      .build();
    var asyncConnectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
      .setTlsStrategy(new DefaultClientTlsStrategy(sslFactory.getSslContext()))
      .setDefaultTlsConfig(TlsConfig.custom()
        // Force HTTP/1 since we know SQ/SC don't support HTTP/2 ATM
        .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)
        .build())
      .build();
    this.sharedClient = HttpAsyncClients.custom()
      .setConnectionManager(asyncConnectionManager)
      .addResponseInterceptorFirst(new RedirectInterceptor())
      .setUserAgent(userAgent)
      // proxy settings
      .setRoutePlanner(new SystemDefaultRoutePlanner(proxySelector))
      .setDefaultCredentialsProvider(proxyCredentialsProvider)
      .setDefaultRequestConfig(
        RequestConfig.copy(RequestConfig.DEFAULT)
          .setConnectionRequestTimeout(CONNECTION_TIMEOUT)
          .setResponseTimeout(RESPONSE_TIMEOUT)
          .build())
      .build();

    sharedClient.start();
  }

  public HttpClient getHttpClient() {
    return new ApacheHttpClientAdapter(sharedClient, null, null);
  }

  public HttpClient getHttpClientWithPreemptiveAuth(String usernameOrToken, @Nullable String password) {
    return new ApacheHttpClientAdapter(sharedClient, usernameOrToken, password);
  }

  public void close() {
    sharedClient.close(CloseMode.IMMEDIATE);
  }

}
