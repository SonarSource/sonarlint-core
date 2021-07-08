/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
import org.apache.commons.lang.StringUtils;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

public class SystemApi {
  private static final Logger LOG = Loggers.get(SystemApi.class);

  private final ServerApiHelper helper;

  public SystemApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public CompletableFuture<Sonarlint.ServerInfos> getStatus() {
    return ServerApiHelper.processTimed(
      helper.getAsync("api/system/status"),
      response -> {
        String responseStr = response.bodyAsString();
        try {
          SystemStatus status = new Gson().fromJson(responseStr, SystemStatus.class);
          Sonarlint.ServerInfos.Builder builder = Sonarlint.ServerInfos.newBuilder();
          builder.setId(status.id);
          builder.setStatus(status.status);
          builder.setVersion(status.version);
          return builder.build();
        } catch (Exception e) {
          throw new IllegalStateException("Unable to parse server infos from: " + StringUtils.abbreviate(responseStr, 100), e);
        }
      },
      duration -> LOG.debug("Downloaded server infos in {}ms", duration));
  }

  private static class SystemStatus {
    String id;
    String version;
    String status;
  }
}
