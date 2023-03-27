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
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.client.http.SelectProxiesParams;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

class ClientProxySelector extends ProxySelector {

  private final SonarLintLogger logger = SonarLintLogger.get();
  private final SonarLintClient client;

  public ClientProxySelector(SonarLintClient client) {
    this.client = client;
  }

  @Override
  public List<Proxy> select(URI uri) {
    try {
      return client.selectProxies(new SelectProxiesParams(uri.toString())).get().getProxies().stream()
        .map(p -> p.getType() == Proxy.Type.DIRECT ? Proxy.NO_PROXY
          : new Proxy(p.getType(), new InetSocketAddress(p.getHostname(),
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
}
