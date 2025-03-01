/*
 * SonarLint Core - Implementation
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

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.GetProxyPasswordAuthenticationParams;

/**
 * Inspired by {@link org.apache.hc.client5.http.impl.auth.SystemDefaultCredentialsProvider} but asking client instead of
 * asking JDK
 */
public class ClientProxyCredentialsProvider implements CredentialsProvider {

  private final SonarLintLogger logger = SonarLintLogger.get();
  private final SonarLintRpcClient client;

  public ClientProxyCredentialsProvider(SonarLintRpcClient client) {
    this.client = client;
  }

  @Override
  public Credentials getCredentials(AuthScope authScope, @Nullable HttpContext context) {
    var host = authScope.getHost();
    if (host == null || context == null) {
      return null;
    }
    try {
      var targetHostURI = HttpClientContext.adapt(context).getRequest().getUri();
      var protocol = getProtocol(authScope);
      var response = client.getProxyPasswordAuthentication(
        new GetProxyPasswordAuthenticationParams(host, authScope.getPort(), protocol,
          authScope.getRealm(), authScope.getSchemeName(), targetHostURI.toURL()))
        .get();
      var proxyUser = response.getProxyUser();
      if (proxyUser != null) {
        var proxyPassword = response.getProxyPassword();
        return new UsernamePasswordCredentials(proxyUser, proxyPassword != null ? proxyPassword.toCharArray() : null);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.warn("Interrupted!", e);
    } catch (URISyntaxException | MalformedURLException | ExecutionException e) {
      logger.warn("Unable to get proxy credentials from the client", e);
    }
    return null;
  }

  private static String getProtocol(AuthScope authScope) {
    String protocol;
    if (authScope.getProtocol() != null) {
      protocol = authScope.getProtocol();
    } else {
      protocol = (authScope.getPort() == 443 ? URIScheme.HTTPS.id : URIScheme.HTTP.id);
    }
    return protocol;
  }
}
