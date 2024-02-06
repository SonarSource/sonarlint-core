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
package org.sonarsource.sonarlint.core.rpc.protocol.client.connection;

import javax.annotation.Nullable;

public class AssistCreatingConnectionParams {
  private final String serverUrl;
  @Nullable
  private final String tokenName;
  @Nullable
  private final String tokenValue;

  public AssistCreatingConnectionParams(String serverUrl, @Nullable String tokenName, @Nullable String tokenValue) {
    this.serverUrl = serverUrl;
    this.tokenName = tokenName;
    this.tokenValue = tokenValue;
  }

  public String getServerUrl() {
    return serverUrl;
  }

  @Nullable
  public String getTokenName() {
    return tokenName;
  }

  @Nullable
  public String getTokenValue() {
    return tokenValue;
  }
}
