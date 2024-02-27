/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize;

import java.time.Duration;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class HttpConfigurationDto {

  public static HttpConfigurationDto defaultConfig() {
    return new HttpConfigurationDto(SslConfigurationDto.defaultConfig(), null, null, null, null);
  }

  private final SslConfigurationDto sslConfiguration;
  private final Duration connectTimeout;
  private final Duration socketTimeout;
  private final Duration connectionRequestTimeout;
  private final Duration responseTimeout;

  public HttpConfigurationDto(SslConfigurationDto sslConfiguration, @Nullable Duration connectTimeout, @Nullable Duration socketTimeout,
    @Nullable Duration connectionRequestTimeout, @Nullable Duration responseTimeout) {
    this.sslConfiguration = sslConfiguration;
    this.connectTimeout = connectTimeout;
    this.socketTimeout = socketTimeout;
    this.connectionRequestTimeout = connectionRequestTimeout;
    this.responseTimeout = responseTimeout;
  }

  public SslConfigurationDto getSslConfiguration() {
    return sslConfiguration;
  }

  @CheckForNull
  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  @CheckForNull
  public Duration getSocketTimeout() {
    return socketTimeout;
  }

  @CheckForNull
  public Duration getConnectionRequestTimeout() {
    return connectionRequestTimeout;
  }

  @CheckForNull
  public Duration getResponseTimeout() {
    return responseTimeout;
  }
}
