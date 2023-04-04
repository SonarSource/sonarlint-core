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
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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

  private final SonarLintLogger logger = SonarLintLogger.get();

  private final SonarLintClient client;
  private final java.net.http.HttpClient sharedClient;

  public HttpClientManager(SonarLintClient client) {
    this.client = client;
    this.sharedClient = java.net.http.HttpClient.newBuilder()
      .proxy(new ProxySelector() {
        @Override
        public List<Proxy> select(URI uri) {
          try {
            return client.selectProxies(new SelectProxiesParams(uri.toString())).get().getProxies().stream()
              .map(p -> p.getType() == Proxy.Type.DIRECT ? Proxy.NO_PROXY : new Proxy(p.getType(), new InetSocketAddress(p.getHostname(), p.getPort())))
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
      })
      .authenticator(new Authenticator() {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
          if (getRequestorType() != RequestorType.PROXY) {
            // We only handle proxy authentication here
            return null;
          }
          try {
            var response = client.getProxyPasswordAuthentication(
              new GetProxyPasswordAuthenticationParams(getRequestingHost(), getRequestingPort(), getRequestingProtocol(), getRequestingPrompt(), getRequestingScheme())).get();
            if (response.getProxyUser() != null || response.getProxyPassword() != null) {
              return new PasswordAuthentication(response.getProxyUser(), response.getProxyPassword().toCharArray());
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted!", e);
          } catch (ExecutionException e) {
            logger.warn("Unable to get proxy", e);
          }
          return null;
        }
      })
      .build();
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

}
