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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import nl.altindag.ssl.SSLFactory;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.io.CloseMode;

import static java.util.Objects.requireNonNull;

public class HttpClientProvider {

  private static final char[] TRUSTSTORE_PWD = System.getProperty("sonarlint.ssl.trustStorePassword", "sonarlint").toCharArray();
  private final CloseableHttpAsyncClient sharedClient;

  /**
   * Return an {@link HttpClientProvider} made for testing, with a dummy user agent, and basic configuration regarding proxy/SSL
   */
  public static HttpClientProvider forTesting() {
    return new HttpClientProvider("SonarLint tests", null, null, ProxySelector.getDefault(), new BasicCredentialsProvider());
  }

  public HttpClientProvider(String userAgent, @Nullable Path sonarlintUserHome,
    @Nullable BiPredicate<X509Certificate[], String> certificateAndAuthTypeTrustPredicate, ProxySelector proxySelector, CredentialsProvider proxyCredentialsProvider) {
    var sslFactoryBuilder = SSLFactory.builder()
      .withDefaultTrustMaterial()
      .withSystemTrustMaterial();
    if (certificateAndAuthTypeTrustPredicate != null) {
      var truststorePath = System.getProperty("sonarlint.ssl.trustStorePath", requireNonNull(sonarlintUserHome).resolve("ssl/truststore.p12").toString());
      sslFactoryBuilder.withInflatableTrustMaterial(Paths.get(truststorePath), TRUSTSTORE_PWD, "PKCS12", certificateAndAuthTypeTrustPredicate);
    }
    var asyncConnectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
      .setTlsStrategy(new DefaultClientTlsStrategy(sslFactoryBuilder.build().getSslContext()))
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
      .build();

    sharedClient.start();
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
