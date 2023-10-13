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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config;

import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

public class SonarCloudConnectionConfigurationDto {

  /**
   * The id of the connection on the client side
   */
  private final String connectionId;
  private final String organization;
  private final boolean disableNotifications;

  public SonarCloudConnectionConfigurationDto(@NonNull String connectionId, @NonNull String organization, boolean disableNotifications) {
    this.connectionId = connectionId;
    this.organization = organization;
    this.disableNotifications = disableNotifications;
  }

  public String getConnectionId() {
    return connectionId;
  }

  public String getOrganization() {
    return organization;
  }

  public boolean getDisableNotifications() {
    return disableNotifications;
  }
}
