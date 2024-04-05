/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.system;

import com.google.gson.Gson;
import java.util.concurrent.CompletableFuture;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

public class SystemApi {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ServerApiHelper helper;

  public SystemApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public CompletableFuture<ServerInfo> getStatus() {
    return ServerApiHelper.processTimed(
      helper.getAsync("api/system/status"),
      response -> {
        var responseStr = response.bodyAsString();
        try {
          var status = new Gson().fromJson(responseStr, SystemStatus.class);
          return new ServerInfo(status.id, status.status, status.version);
        } catch (Exception e) {
          throw new IllegalStateException("Unable to parse server infos from: " + responseStr, e);
        }
      },
      duration -> LOG.debug("Downloaded server infos in {}ms", duration));
  }

  public ServerInfo getStatusSync() {
    try {
      return getStatus().get();
    } catch (Exception e) {
      throw new IllegalStateException("Unable to get server status", e);
    }
  }

  private static class SystemStatus {
    String id;
    String version;
    String status;
  }
}
