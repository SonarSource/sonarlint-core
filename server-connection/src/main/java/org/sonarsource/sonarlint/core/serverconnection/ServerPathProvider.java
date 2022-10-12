/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.util.concurrent.ExecutionException;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverapi.system.ServerInfo;

public class ServerPathProvider {

  private ServerPathProvider() {
  }

  private static final String MIN_SQ_VERSION = "9.7";

  public static String getServerUrlForTokenGeneration(EndpointParams endpoint, HttpClient client,
    int port, String ideName) {
    var serverApi = new ServerApi(endpoint, client);
    ServerInfo systemInfo;
    try {
      systemInfo = serverApi.system().getStatus().get();
    } catch (InterruptedException | IllegalStateException e) {
      Thread.currentThread().interrupt();
      throw new DownloadException("Failed to get server status for " + endpoint.getBaseUrl() + " due to thread interruption", e);
    } catch (ExecutionException e) {
      throw new DownloadException("Failed to get server status for " + endpoint.getBaseUrl(), e);
    }
    return buildServerPath(endpoint.getBaseUrl(), systemInfo.getVersion(), port, ideName, endpoint.isSonarCloud());
  }

  static String buildServerPath(String baseUrl, String serverVersionStr, int port, String ideName, boolean isSonarCloud) {
    var minVersion = Version.create(MIN_SQ_VERSION);
    var serverVersion = Version.create(serverVersionStr);
    var path = new StringBuilder(baseUrl);
    var portParameter = getPortParameter(port);
    if (isSonarCloud || !serverVersion.satisfiesMinRequirement(minVersion)) {
      path.append("/account/security");
    } else {
      path.append("/sonarlint/auth").append("?ideName=").append(UrlUtils.urlEncode(ideName)).append(portParameter);
    }
    return path.toString();
  }

  private static String getPortParameter(int port) {
    if (port == -1) {
      return "";
    }
    return "&port=" + port;
  }

}
