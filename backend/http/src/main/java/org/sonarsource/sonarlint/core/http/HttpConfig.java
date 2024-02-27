/*
 * SonarLint Core - HTTP
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
package org.sonarsource.sonarlint.core.http;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.hc.core5.util.Timeout;
import org.sonarsource.sonarlint.core.http.ssl.SslConfig;

public class HttpConfig {

  private final SslConfig sslConfig;
  private final Timeout connectTimeout;
  private final Timeout socketTimeout;
  private final Timeout connectionRequestTimeout;
  private final Timeout responseTimeout;

  public HttpConfig(SslConfig sslConfig, @Nullable Timeout connectTimeout, @Nullable Timeout socketTimeout, @Nullable Timeout connectionRequestTimeout,
    @Nullable Timeout responseTimeout) {
    this.sslConfig = sslConfig;
    this.connectTimeout = connectTimeout;
    this.socketTimeout = socketTimeout;
    this.connectionRequestTimeout = connectionRequestTimeout;
    this.responseTimeout = responseTimeout;
  }

  public SslConfig getSslConfig() {
    return sslConfig;
  }

  @CheckForNull
  public Timeout getConnectTimeout() {
    return connectTimeout;
  }

  @CheckForNull
  public Timeout getSocketTimeout() {
    return socketTimeout;
  }

  @CheckForNull
  public Timeout getConnectionRequestTimeout() {
    return connectionRequestTimeout;
  }

  @CheckForNull
  public Timeout getResponseTimeout() {
    return responseTimeout;
  }
}
