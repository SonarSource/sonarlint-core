/*
 * SonarLint Core - HTTP
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
package org.sonarsource.sonarlint.core.http;

import com.google.common.util.concurrent.MoreExecutors;
import java.net.ProxySelector;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import javax.net.ssl.SSLContext;
import nl.altindag.ssl.SSLFactory;
import nl.altindag.ssl.model.TrustManagerParameters;
import org.apache.commons.lang3.SystemUtils;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.config.ConnectionConfig;
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
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.http.ssl.SslConfig;

import static org.sonarsource.sonarlint.core.http.ThreadFactories.threadWithNamePrefix;

public class HttpClientProvider {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final CloseableHttpAsyncClient sharedClient;
  private final ExecutorService webSocketThreadPool;
  private final String userAgent;

  /**
   * Return an {@link HttpClientProvider} made for testing, with a dummy user agent, and basic configuration regarding proxy/SSL
   */
  public static HttpClientProvider forTesting() {
    return new HttpClientProvider("SonarLint tests", new HttpConfig(new SslConfig(null, null), null, null, null, null), null, ProxySelector.getDefault(),
      new BasicCredentialsProvider());
  }

  public HttpClientProvider(String userAgent, HttpConfig httpConfig, @Nullable Predicate<TrustManagerParameters> trustManagerParametersPredicate, ProxySelector proxySelector,
    CredentialsProvider proxyCredentialsProvider) {
    this.userAgent = userAgent;
    this.webSocketThreadPool = Executors.newCachedThreadPool(threadWithNamePrefix("sonarcloud-websocket-"));
    var asyncConnectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
      .setTlsStrategy(new DefaultClientTlsStrategy(configureSsl(httpConfig.getSslConfig(), trustManagerParametersPredicate)))
      .setDefaultTlsConfig(TlsConfig.custom()
        // Force HTTP/1 since we know SQ/SC don't support HTTP/2 ATM
        .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)
        .build())
      .setDefaultConnectionConfig(buildConnectionConfig(httpConfig.getConnectTimeout(), httpConfig.getSocketTimeout()))
      .build();
    this.sharedClient = HttpAsyncClients.custom()
      .setConnectionManager(asyncConnectionManager)
      .addResponseInterceptorFirst(new RedirectInterceptor())
      .setUserAgent(userAgent)
      // proxy settings
      .setRoutePlanner(new SystemDefaultRoutePlanner(proxySelector))
      .setDefaultCredentialsProvider(proxyCredentialsProvider)
      .setDefaultRequestConfig(buildRequestConfig(httpConfig.getConnectionRequestTimeout(), httpConfig.getResponseTimeout()))
      .build();

    sharedClient.start();
  }

  private static SSLContext configureSsl(SslConfig sslConfig, @Nullable Predicate<TrustManagerParameters> trustManagerParametersPredicate) {
    var sslFactoryBuilder = SSLFactory.builder()
      .withDefaultTrustMaterial();
    // SLCORE-686; SLCORE-669
    if (isNotWindows()) {
      sslFactoryBuilder.withSystemTrustMaterial();
    }
    var keyStore = sslConfig.getKeyStore();
    if (keyStore != null && Files.exists(keyStore.getPath())) {
      sslFactoryBuilder.withIdentityMaterial(keyStore.getPath(), keyStore.getKeyStorePassword().toCharArray(), keyStore.getKeyStoreType());
    }
    var trustStore = sslConfig.getTrustStore();
    if (trustStore != null) {
      sslFactoryBuilder.withInflatableTrustMaterial(trustStore.getPath(), trustStore.getKeyStorePassword().toCharArray(), trustStore.getKeyStoreType(),
        trustManagerParametersPredicate);
    }
    return sslFactoryBuilder.build().getSslContext();
  }

  private static boolean isNotWindows() {
    return !SystemUtils.IS_OS_WINDOWS;
  }

  private static ConnectionConfig buildConnectionConfig(@Nullable Timeout connectTimeout, @Nullable Timeout socketTimeout) {
    var connectionConfig = ConnectionConfig.custom();
    if (connectTimeout != null) {
      connectionConfig.setConnectTimeout(connectTimeout);
    }
    if (socketTimeout != null) {
      connectionConfig.setSocketTimeout(socketTimeout);
    }
    return connectionConfig.build();
  }

  private static RequestConfig buildRequestConfig(@Nullable Timeout connectionRequestTimeout, @Nullable Timeout responseTimeout) {
    var requestConfig = RequestConfig.custom();
    if (connectionRequestTimeout != null) {
      requestConfig.setConnectionRequestTimeout(connectionRequestTimeout);
    }
    if (responseTimeout != null) {
      requestConfig.setResponseTimeout(responseTimeout);
    }
    return requestConfig.build();
  }

  public HttpClient getHttpClient() {
    return ApacheHttpClientAdapter.withoutCredentials(sharedClient);
  }

  public HttpClient getHttpClientWithPreemptiveAuth(String username, @Nullable String password) {
    return ApacheHttpClientAdapter.withUsernamePassword(sharedClient, username, password);
  }

  public HttpClient getHttpClientWithPreemptiveAuth(String token, boolean shouldUseBearer) {
    return ApacheHttpClientAdapter.withToken(sharedClient, token, shouldUseBearer);
  }

  public WebSocketClient getWebSocketClient(String token) {
    return new WebSocketClient(userAgent, token, webSocketThreadPool);
  }

  @PreDestroy
  public void close() {
    sharedClient.close(CloseMode.IMMEDIATE);
    if (!MoreExecutors.shutdownAndAwaitTermination(webSocketThreadPool, 1, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop web socket executor service in a timely manner");
    }
  }
}
