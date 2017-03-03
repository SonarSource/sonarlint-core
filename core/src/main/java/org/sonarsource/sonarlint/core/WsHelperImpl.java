/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core;

import com.google.gson.Gson;

import java.util.Map;
import org.sonarsource.sonarlint.core.client.api.common.TelemetryClientConfig;
import org.sonarsource.sonarlint.core.client.api.common.TelemetryData;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;
import org.sonarsource.sonarlint.core.client.api.connected.WsHelper;
import org.sonarsource.sonarlint.core.client.api.exceptions.SonarLintWrappedException;
import org.sonarsource.sonarlint.core.client.api.exceptions.UnsupportedServerException;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.validate.AuthenticationChecker;
import org.sonarsource.sonarlint.core.container.connected.validate.DefaultValidationResult;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.container.connected.validate.ServerVersionAndStatusChecker;
import org.sonarsource.sonarlint.core.telemetry.TelemetryPayload;
import org.sonarsource.sonarlint.core.util.ws.HttpConnector;
import org.sonarsource.sonarlint.core.util.ws.PostRequest;
import org.sonarsource.sonarlint.core.util.ws.WsResponse;

import static com.google.common.base.Preconditions.checkNotNull;

public class WsHelperImpl implements WsHelper {
  private static final String TELEMETRY_ENDPOINT = "";
  private static final String TELEMETRY_PATH = "";
  private static final int TELEMETRY_TIMEOUT = 30_000;

  @Override
  public ValidationResult validateConnection(ServerConfiguration serverConfig) {
    SonarLintWsClient client = createClient(serverConfig);
    return validateConnection(new ServerVersionAndStatusChecker(client), new PluginVersionChecker(client), new AuthenticationChecker(client));
  }

  static ValidationResult validateConnection(ServerVersionAndStatusChecker serverChecker, PluginVersionChecker pluginsChecker, AuthenticationChecker authChecker) {
    try {
      serverChecker.checkVersionAndStatus();
      pluginsChecker.checkPlugins();
      return authChecker.validateCredentials();
    } catch (UnsupportedServerException e) {
      return new DefaultValidationResult(false, e.getMessage());
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    }
  }

  private static SonarLintWsClient createClient(ServerConfiguration serverConfig) {
    checkNotNull(serverConfig);
    return new SonarLintWsClient(serverConfig);
  }

  static String generateAuthenticationToken(ServerVersionAndStatusChecker serverChecker, SonarLintWsClient client, String name, boolean force) {
    try {
      // in 5.3 login is mandatory and user needs admin privileges
      serverChecker.checkVersionAndStatus("5.4");

      if (force) {
        // revoke
        client.post("api/user_tokens/revoke?name=" + name);
      }

      // create
      try (WsResponse response = client.post("api/user_tokens/generate?name=" + name)) {
        Map<?, ?> javaRootMapObject = new Gson().fromJson(response.content(), Map.class);
        return (String) javaRootMapObject.get("token");
      }
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    }
  }

  @Override
  public String generateAuthenticationToken(ServerConfiguration serverConfig, String name, boolean force) {
    SonarLintWsClient client = createClient(serverConfig);
    return generateAuthenticationToken(new ServerVersionAndStatusChecker(client), client, name, force);
  }

  @Override
  public boolean sendTelemetryData(TelemetryClientConfig clientConfig, TelemetryData data) {
    try {
      TelemetryPayload payload = new TelemetryPayload(data.daysSinceInstallation(), data.daysOfUse(), data.product(), data.version(), data.connectedMode());
      String json = payload.toJson();

      HttpConnector httpConnector = buildTelemetryClient(clientConfig);
      PostRequest post = new PostRequest(TELEMETRY_PATH);
      post.setMediaType("application/json");
      httpConnector.post(post, json).failIfNotSuccessful();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private static HttpConnector buildTelemetryClient(TelemetryClientConfig clientConfig) {
    return HttpConnector.newBuilder().url(TELEMETRY_ENDPOINT)
      .userAgent(clientConfig.userAgent())
      .proxy(clientConfig.proxy())
      .proxyCredentials(clientConfig.proxyLogin(), clientConfig.proxyPassword())
      .readTimeoutMilliseconds(TELEMETRY_TIMEOUT)
      .connectTimeoutMilliseconds(TELEMETRY_TIMEOUT)
      .setSSLSocketFactory(clientConfig.sslSocketFactory())
      .setTrustManager(clientConfig.trustManager())
      .build();
  }
}
