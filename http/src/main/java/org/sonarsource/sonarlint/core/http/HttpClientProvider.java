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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import nl.altindag.ssl.SSLFactory;
import nl.altindag.ssl.model.TrustManagerParameters;
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

public class HttpClientProvider {

  private final CloseableHttpAsyncClient sharedClient;

  /**
   * Return an {@link HttpClientProvider} made for testing, with a dummy user agent, and basic configuration regarding proxy/SSL
   */
  public static HttpClientProvider forTesting() {
    return new HttpClientProvider("SonarLint tests", null, null, ProxySelector.getDefault(), new BasicCredentialsProvider());
  }

  public HttpClientProvider(String userAgent, @Nullable Path sonarlintUserHome,
    @Nullable Predicate<TrustManagerParameters> trustManagerParametersPredicate, ProxySelector proxySelector, CredentialsProvider proxyCredentialsProvider) {
    var sslFactoryBuilder = SSLFactory.builder()
      .withDefaultTrustMaterial()
      .withSystemTrustMaterial();
    var sonarlintUserHomeOpt = Optional.ofNullable(sonarlintUserHome);
    configureKeyStore(sslFactoryBuilder, sonarlintUserHomeOpt);
    configureTrustStore(trustManagerParametersPredicate, sslFactoryBuilder, sonarlintUserHomeOpt);
    var connectionConfigBuilder = ConnectionConfig.custom();
    getTimeoutFromSystemProp("sonarlint.http.connectTimeout").ifPresent(connectionConfigBuilder::setConnectTimeout);
    getTimeoutFromSystemProp("sonarlint.http.socketTimeout").ifPresent(connectionConfigBuilder::setSocketTimeout);
    var asyncConnectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
      .setTlsStrategy(new DefaultClientTlsStrategy(sslFactoryBuilder.build().getSslContext()))
      .setDefaultTlsConfig(TlsConfig.custom()
        // Force HTTP/1 since we know SQ/SC don't support HTTP/2 ATM
        .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)
        .build())
      .setDefaultConnectionConfig(connectionConfigBuilder.build())
      .build();
    var requestConfigBuilder = RequestConfig.custom();
    getTimeoutFromSystemProp("sonarlint.http.connectionRequestTimeout").ifPresent(requestConfigBuilder::setConnectionRequestTimeout);
    getTimeoutFromSystemProp("sonarlint.http.responseTimeout").ifPresent(requestConfigBuilder::setResponseTimeout);
    this.sharedClient = HttpAsyncClients.custom()
      .setConnectionManager(asyncConnectionManager)
      .addResponseInterceptorFirst(new RedirectInterceptor())
      .setUserAgent(userAgent)
      // proxy settings
      .setRoutePlanner(new SystemDefaultRoutePlanner(proxySelector))
      .setDefaultCredentialsProvider(proxyCredentialsProvider)
      .setDefaultRequestConfig(
        requestConfigBuilder
          .build())
      .build();

    sharedClient.start();
  }

  private static void configureTrustStore(@Nullable Predicate<TrustManagerParameters> trustManagerParametersPredicate, SSLFactory.Builder sslFactoryBuilder,
    Optional<Path> sonarlintUserHomeOpt) {
    if (trustManagerParametersPredicate != null) {
      var truststorePath = Optional.ofNullable(System.getProperty("sonarlint.ssl.trustStorePath")).map(Paths::get)
        .orElse(sonarlintUserHomeOpt.map(p -> p.resolve("ssl/truststore.p12")).orElse(null));
      if (truststorePath != null) {
        var trustStorePwd = System.getProperty("sonarlint.ssl.trustStorePassword", "sonarlint").toCharArray();
        var trustStoreType = System.getProperty("sonarlint.ssl.trustStoreType", "PKCS12");
        sslFactoryBuilder.withInflatableTrustMaterial(truststorePath, trustStorePwd, trustStoreType, trustManagerParametersPredicate);
      }
    }
  }

  private static void configureKeyStore(SSLFactory.Builder sslFactoryBuilder, Optional<Path> sonarlintUserHomeOpt) {
    var keystorePath = Optional.ofNullable(System.getProperty("sonarlint.ssl.keyStorePath")).map(Paths::get)
      .orElse(sonarlintUserHomeOpt.map(p -> p.resolve("ssl/keystore.p12")).orElse(null));
    if (keystorePath != null && Files.exists(keystorePath)) {
      var keyStorePwd = System.getProperty("sonarlint.ssl.keyStorePassword", "sonarlint").toCharArray();
      var keyStoreType = System.getProperty("sonarlint.ssl.keyStoreType", "PKCS12");
      sslFactoryBuilder.withIdentityMaterial(keystorePath, keyStorePwd, keyStoreType);
    }
  }

  private static Optional<Timeout> getTimeoutFromSystemProp(String key) {
    return Optional.ofNullable(System.getProperty(key))
      .map(s -> {
        try {
          return Duration.ofMinutes(Integer.parseInt(s));
        } catch (NumberFormatException e) {
          return Duration.parse(s);
        }
      })
      .map(d -> Timeout.of(d.toMillis(), TimeUnit.MILLISECONDS));
  }

  public HttpClient getHttpClient() {
    return new ApacheHttpClientAdapter(sharedClient, null, null);
  }

  public HttpClient getHttpClientWithPreemptiveAuth(String usernameOrToken, @Nullable String password) {
    return new ApacheHttpClientAdapter(sharedClient, usernameOrToken, password);
  }

  @PreDestroy
  public void close() {
    sharedClient.close(CloseMode.IMMEDIATE);
  }

}
