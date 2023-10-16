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

import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.GetProxyPasswordAuthenticationParams;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

/**
 * Inspired by {@link org.apache.hc.client5.http.impl.auth.SystemDefaultCredentialsProvider} but asking client instead of
 * asking JDK
 */
@Named
@Singleton
public class ClientProxyCredentialsProvider implements CredentialsProvider {

  private final SonarLintLogger logger = SonarLintLogger.get();
  private final SonarLintRpcClient client;

  public ClientProxyCredentialsProvider(SonarLintRpcClient client) {
    this.client = client;
  }

  @Override
  public Credentials getCredentials(AuthScope authScope, @Nullable HttpContext context) {
    var host = authScope.getHost();
    if (host == null) {
      return null;
    }
    var targetHostURL = getTargetHostURL(context);
    var protocol = getProtocol(authScope);
    try {
      var response = client.getProxyPasswordAuthentication(
        new GetProxyPasswordAuthenticationParams(authScope.getHost(), authScope.getPort(), protocol,
          authScope.getRealm(), authScope.getSchemeName(), targetHostURL))
        .get();
      var proxyUser = response.getProxyUser();
      if (proxyUser != null) {
        var proxyPassword = response.getProxyPassword();
        return new UsernamePasswordCredentials(proxyUser, proxyPassword != null ? proxyPassword.toCharArray() : null);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.warn("Interrupted!", e);
    } catch (ExecutionException e) {
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

  @CheckForNull
  private static String getTargetHostURL(@Nullable HttpContext context) {
    var clientContext = context != null ? HttpClientContext.adapt(context) : null;
    var request = context != null ? clientContext.getRequest() : null;
    String targetHostURL;
    try {
      var uri = request != null ? request.getUri() : null;
      targetHostURL = uri != null ? uri.toString() : null;
    } catch (final URISyntaxException ignore) {
      targetHostURL = null;
    }
    return targetHostURL;
  }
}
