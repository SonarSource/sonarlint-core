/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import java.net.HttpURLConnection;
import org.sonarqube.ws.client.WsResponse;
import org.sonarsource.sonarlint.core.client.api.connected.UnsupportedServerException;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.plugin.Version;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;

import static org.sonar.api.internal.apachecommons.lang.StringUtils.trimToEmpty;

public class ServerVersionAndStatusChecker {

  private static final String MIN_SQ_VERSION = "5.2";
  private final SonarLintWsClient wsClient;

  public ServerVersionAndStatusChecker(SonarLintWsClient wsClient) {
    this.wsClient = wsClient;
  }

  public ServerInfos checkVersionAndStatus() {
    ServerInfos serverStatus = fetchServerInfos();
    if (!"UP".equals(serverStatus.getStatus())) {
      throw new IllegalStateException(serverNotReady(serverStatus));
    }
    Version serverVersion = Version.create(serverStatus.getVersion());
    if (serverVersion.compareTo(Version.create(MIN_SQ_VERSION)) < 0) {
      throw new UnsupportedServerException(unsupportedVersion(serverStatus));
    }
    return serverStatus;
  }

  private static String unsupportedVersion(ServerInfos serverStatus) {
    return "SonarQube server has version " + serverStatus.getVersion() + ". Version should be greater or equal to " + MIN_SQ_VERSION;
  }

  private static String serverNotReady(ServerInfos serverStatus) {
    return "Server not ready (" + serverStatus.getStatus() + ")";
  }

  public ValidationResult validateStatusAndVersion() {
    ServerInfos serverStatus = fetchServerInfos();
    if (!"UP".equals(serverStatus.getStatus())) {
      return new DefaultValidationResult(false, serverNotReady(serverStatus));
    }
    Version serverVersion = Version.create(serverStatus.getVersion());
    if (serverVersion.compareTo(Version.create(MIN_SQ_VERSION)) < 0) {
      return new DefaultValidationResult(false, unsupportedVersion(serverStatus));
    }
    return new DefaultValidationResult(true, "Compatible and ready");
  }

  private ServerInfos fetchServerInfos() {
    WsResponse response = wsClient.rawGet("api/system/status");
    if (!response.isSuccessful()) {
      if (response.code() == HttpURLConnection.HTTP_NOT_FOUND) {
        return tryFromDeprecatedApi(response);
      } else {
        throw SonarLintWsClient.handleError(response);
      }
    } else {
      String responseStr = response.content();
      try {
        ServerInfos.Builder builder = ServerInfos.newBuilder();
        JsonFormat.parser().merge(responseStr, builder);
        return builder.build();
      } catch (InvalidProtocolBufferException e) {
        throw new IllegalStateException("Unable to parse server infos from: " + response.content(), e);
      }
    }
  }

  private ServerInfos tryFromDeprecatedApi(WsResponse originalReponse) {
    // Maybe a server version prior to 5.2. Fallback on deprecated api/server/version
    WsResponse responseFallback = wsClient.rawGet("api/server/version");
    if (!responseFallback.isSuccessful()) {
      // We prefer to report original error
      throw SonarLintWsClient.handleError(originalReponse);
    }
    String responseStr = responseFallback.content();
    ServerInfos.Builder builder = ServerInfos.newBuilder();
    builder.setStatus("UP");
    builder.setVersion(trimToEmpty(responseStr));
    return builder.build();
  }

}
