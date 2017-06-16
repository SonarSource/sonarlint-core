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
package org.sonarsource.sonarlint.core.telemetry;

import org.sonarsource.sonarlint.core.client.api.common.TelemetryClientConfig;
import org.sonarsource.sonarlint.core.util.ws.HttpConnector;

public class TelemetryHttpFactory {
  private static final String TELEMETRY_ENDPOINT = "https://chestnutsl.sonarsource.com";
  private static final int TELEMETRY_TIMEOUT = 30_000;

  public HttpConnector buildClient(TelemetryClientConfig clientConfig) {
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
