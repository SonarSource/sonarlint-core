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
package org.sonarsource.sonarlint.core.container.connected.validate;

import com.google.gson.Gson;
import org.apache.commons.lang.StringUtils;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.common.Version;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;
import org.sonarsource.sonarlint.core.client.api.exceptions.UnsupportedServerException;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;

public class ServerVersionAndStatusChecker {

  private static final Logger LOG = Loggers.get(ServerVersionAndStatusChecker.class);

  private static final String MIN_SQ_VERSION = "6.7";
  private final SonarLintWsClient wsClient;

  public ServerVersionAndStatusChecker(SonarLintWsClient wsClient) {
    this.wsClient = wsClient;
  }

  /**
   * Checks SonarQube version against the minimum version supported by the library
   * @return ServerInfos
   * @throws UnsupportedServerException if version &lt; minimum supported version
   * @throws IllegalStateException If server is not ready
   */
  public ServerInfos checkVersionAndStatus() {
    return checkVersionAndStatus(MIN_SQ_VERSION);
  }

  /**
   * Checks SonarQube version against a provided minimum version
   * @return ServerInfos
   * @throws UnsupportedServerException if version &lt; minimum supported version
   * @throws IllegalStateException If server is not ready
   */
  public ServerInfos checkVersionAndStatus(String minVersion) {
    ServerInfos serverStatus = fetchServerInfos();
    if (!"UP".equals(serverStatus.getStatus())) {
      throw new IllegalStateException(serverNotReady(serverStatus));
    }
    Version serverVersion = Version.create(serverStatus.getVersion());
    if (serverVersion.compareToIgnoreQualifier(Version.create(minVersion)) < 0) {
      throw new UnsupportedServerException(unsupportedVersion(serverStatus, minVersion));
    }
    return serverStatus;
  }

  private static String unsupportedVersion(ServerInfos serverStatus, String minVersion) {
    return "SonarQube server has version " + serverStatus.getVersion() + ". Version should be greater or equal to " + minVersion;
  }

  private static String serverNotReady(ServerInfos serverStatus) {
    return "Server not ready (" + serverStatus.getStatus() + ")";
  }

  public ValidationResult validateStatusAndVersion() {
    return validateStatusAndVersion(MIN_SQ_VERSION);
  }

  public ValidationResult validateStatusAndVersion(String minVersion) {
    ServerInfos serverStatus = fetchServerInfos();
    if (!"UP".equals(serverStatus.getStatus())) {
      return new DefaultValidationResult(false, serverNotReady(serverStatus));
    }
    Version serverVersion = Version.create(serverStatus.getVersion());
    if (serverVersion.compareToIgnoreQualifier(Version.create(minVersion)) < 0) {
      return new DefaultValidationResult(false, unsupportedVersion(serverStatus, minVersion));
    }
    return new DefaultValidationResult(true, "Compatible and ready");
  }

  private ServerInfos fetchServerInfos() {
    return SonarLintWsClient.processTimed(
      () -> wsClient.get("api/system/status"),
      response -> {
        String responseStr = response.content();
        try {
          SystemStatus status = new Gson().fromJson(responseStr, SystemStatus.class);
          ServerInfos.Builder builder = ServerInfos.newBuilder();
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

  static class SystemStatus {
    String id;
    String version;
    String status;
  }

}
