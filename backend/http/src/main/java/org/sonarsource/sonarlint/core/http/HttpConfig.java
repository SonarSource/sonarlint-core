/*
 * SonarLint Core - HTTP
 * Copyright (C) 2016-2025 SonarSource SA
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

import javax.annotation.Nullable;
import org.apache.hc.core5.util.Timeout;
import org.sonarsource.sonarlint.core.http.ssl.SslConfig;

public record HttpConfig(SslConfig sslConfig, @Nullable Timeout connectTimeout, @Nullable Timeout socketTimeout,
                         @Nullable Timeout connectionRequestTimeout, @Nullable Timeout responseTimeout) {

  private static final Timeout DEFAULT_CONNECT_TIMEOUT = Timeout.ofSeconds(60);
  private static final Timeout DEFAULT_RESPONSE_TIMEOUT = Timeout.ofMinutes(10);

  @Override
  public Timeout connectionRequestTimeout() {
    if (connectionRequestTimeout == null) {
      return DEFAULT_CONNECT_TIMEOUT;
    }
    return connectionRequestTimeout;
  }

  @Override
  public Timeout responseTimeout() {
    if (responseTimeout == null) {
      return DEFAULT_RESPONSE_TIMEOUT;
    }
    return responseTimeout;
  }

  @Override
  public Timeout connectTimeout() {
    if (connectTimeout == null) {
      return DEFAULT_CONNECT_TIMEOUT;
    }
    return connectTimeout;
  }

}
