/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2024 SonarSource SA
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

import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;

/**
 * @deprecated since 7.0. Use helpGenerateUserToken RPC method instead.
 */
@Deprecated(since = "10.0")
public class ServerPathProvider {

  private ServerPathProvider() {
  }

  private static final String MIN_SQ_VERSION = "9.7";

  public static String getServerUrlForTokenGeneration(EndpointParams endpoint, HttpClient client, int port, String ideName, SonarLintCancelMonitor cancelMonitor) {
    var serverApi = new ServerApi(endpoint, client);
    var serverStatus = serverApi.system().getStatus(cancelMonitor);
    return buildServerPath(endpoint.getBaseUrl(), serverStatus.getVersion(), port, ideName, endpoint.isSonarCloud());
  }

  public static String getFallbackServerUrlForTokenGeneration(EndpointParams endpoint, HttpClient client, String ideName, SonarLintCancelMonitor cancelMonitor) {
    var serverApi = new ServerApi(endpoint, client);
    var serverStatus = serverApi.system().getStatus(cancelMonitor);
    return buildServerPath(endpoint.getBaseUrl(), serverStatus.getVersion(), null, ideName, endpoint.isSonarCloud());
  }

  static String buildServerPath(String baseUrl, String serverVersionStr, @Nullable Integer port, String ideName, boolean isSonarCloud) {
    var minVersion = Version.create(MIN_SQ_VERSION);
    var serverVersion = Version.create(serverVersionStr);
    var relativePath = new StringBuilder();
    var portParameter = getPortParameter(port);
    if (isSonarCloud || !serverVersion.satisfiesMinRequirement(minVersion)) {
      relativePath.append("/account/security");
    } else {
      relativePath.append("/sonarlint/auth?ideName=").append(UrlUtils.urlEncode(ideName)).append(portParameter);
    }
    return ServerApiHelper.concat(baseUrl, relativePath.toString());
  }

  private static String getPortParameter(@Nullable Integer port) {
    if (port == null) {
      return "";
    }
    return "&port=" + port;
  }

}
