/*
 * SonarLint Core - Implementation
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.util.Timeout;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.client.connection.GetCredentialsParams;
import org.sonarsource.sonarlint.core.clientapi.client.connection.TokenDto;
import org.sonarsource.sonarlint.core.clientapi.client.connection.UsernamePasswordDto;
import org.sonarsource.sonarlint.core.clientapi.client.http.GetProxyPasswordAuthenticationParams;
import org.sonarsource.sonarlint.core.clientapi.client.http.SelectProxiesParams;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.commons.http.JavaHttpClientAdapter;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class HttpClientManager {


  private static final Timeout CONNECTION_TIMEOUT = Timeout.ofSeconds(30);
  private static final Timeout RESPONSE_TIMEOUT = Timeout.ofMinutes(10);
  private final SonarLintLogger logger = SonarLintLogger.get();

  private final SonarLintClient client;
  private final CloseableHttpAsyncClient sharedClient;

  public HttpClientManager(SonarLintClient client) {
    this.client = client;
    sharedClient = HttpAsyncClients.custom()
      .setConnectionManager(
        PoolingAsyncClientConnectionManagerBuilder.create()
          .setTlsStrategy(ClientTlsStrategyBuilder.create()
            .setTlsDetailsFactory(parameter -> new TlsDetails(parameter.getSession(), parameter.getApplicationProtocol())).build())
          .build())
      .addResponseInterceptorFirst(new RedirectInterceptor())
      .setUserAgent("SonarLint")
      // SLI-629 - Force HTTP/1
      .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)
      // proxy settings
      .setRoutePlanner(new SystemDefaultRoutePlanner(new ProxySelector() {
        @Override
        public List<Proxy> select(URI uri) {
          try {
            return client.selectProxies(new SelectProxiesParams(uri.toString())).get().getProxies().stream()
              .map(p -> p.getType() == Proxy.Type.DIRECT ? Proxy.NO_PROXY : new Proxy(p.getType(), new InetSocketAddress(p.getHostname(),
                p.getPort())))
              .collect(Collectors.toList());
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted!", e);
          } catch (ExecutionException e) {
            logger.warn("Unable to get proxy", e);
          }
          return List.of();
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {

        }
      }))
      .setDefaultCredentialsProvider((authScope, httpContext) -> {
        try {
          var response = client.getProxyPasswordAuthentication(
            new GetProxyPasswordAuthenticationParams(authScope.getHost(), authScope.getPort(), authScope.getProtocol(),
              authScope.getRealm(), authScope.getSchemeName())).get();
          if (response.getProxyUser() != null || response.getProxyPassword() != null) {
            return new UsernamePasswordCredentials(response.getProxyUser(), response.getProxyPassword().toCharArray());
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          logger.warn("Interrupted!", e);
        } catch (ExecutionException e) {
          logger.warn("Unable to get proxy", e);
        }
        return null;
      })

      .setDefaultRequestConfig(
        RequestConfig.copy(RequestConfig.DEFAULT)
          .setConnectionRequestTimeout(CONNECTION_TIMEOUT)
          .setResponseTimeout(RESPONSE_TIMEOUT)
          .build()
      )
      .build();

    sharedClient.start();
  }

  public HttpClient getHttpClient() {
    return new JavaHttpClientAdapter(sharedClient, null, null);
  }

  public HttpClient getHttpClient(String connectionId) {
    try {
      var creds = client.getCredentials(new GetCredentialsParams(connectionId)).get(1, TimeUnit.MINUTES);
      return new JavaHttpClientAdapter(sharedClient,
        creds.getCredentials().map(TokenDto::getToken, UsernamePasswordDto::getUsername),
        creds.getCredentials().map(t -> null, UsernamePasswordDto::getPassword));
    } catch (Exception e) {
      logger.error("Unable to get credentials for connection {}", connectionId);
      return new JavaHttpClientAdapter(sharedClient, null, null);
    }
  }

  private static class RedirectInterceptor implements HttpResponseInterceptor {

    @Override
    public void process(HttpResponse response, EntityDetails entity, HttpContext context) throws HttpException, IOException {
      alterResponseCodeIfNeeded(context, response);
    }

    private void alterResponseCodeIfNeeded(HttpContext context, HttpResponse response) {
      if (isPost(context)) {
        // Apache handles some redirect statuses by transforming the POST into a GET
        // we force a different status to keep the request a POST
        var code = response.getCode();
        if (code == HttpStatus.SC_MOVED_PERMANENTLY) {
          response.setCode(HttpStatus.SC_PERMANENT_REDIRECT);
        } else if (code == HttpStatus.SC_MOVED_TEMPORARILY || code == HttpStatus.SC_SEE_OTHER) {
          response.setCode(HttpStatus.SC_TEMPORARY_REDIRECT);
        }
      }
    }

    private boolean isPost(HttpContext context) {
      var request = (HttpRequest) context.getAttribute(HttpCoreContext.HTTP_REQUEST);
      return request != null && Method.POST.isSame(request.getMethod());
    }
  }


}
