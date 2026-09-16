/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.ai;

import javax.annotation.Nullable;

public class PrepareAuthenticateCliCommandParams {
  @Nullable
  private final String serverUrl;
  @Nullable
  private final String organization;
  @Nullable
  private final String connectionId;

  public PrepareAuthenticateCliCommandParams(@Nullable String serverUrl, @Nullable String organization) {
    this(serverUrl, organization, null);
  }

  public PrepareAuthenticateCliCommandParams(@Nullable String serverUrl, @Nullable String organization,
    @Nullable String connectionId) {
    this.serverUrl = serverUrl;
    this.organization = organization;
    this.connectionId = connectionId;
  }

  @Nullable
  public String getServerUrl() {
    return serverUrl;
  }

  @Nullable
  public String getOrganization() {
    return organization;
  }

  /**
   * IDE connection id from {@link GetAiIntegrationStateResponse#getConnectionChoices()}.
   * When set, it takes precedence over {@link #getServerUrl()} and {@link #getOrganization()}.
   */
  @Nullable
  public String getConnectionId() {
    return connectionId;
  }
}
