/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.repository.connection;

import java.util.Objects;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;

public abstract class AbstractConnectionConfiguration {

  /**
   * The id of the connection on the client side
   */
  private final String connectionId;
  private final boolean disableNotifications;
  private final ConnectionKind kind;

  protected AbstractConnectionConfiguration(String connectionId, ConnectionKind kind, boolean disableNotifications) {
    Objects.requireNonNull(connectionId, "Connection id is mandatory");
    this.connectionId = connectionId;
    this.kind = kind;
    this.disableNotifications = disableNotifications;
  }

  public String getConnectionId() {
    return connectionId;
  }

  public ConnectionKind getKind() {
    return kind;
  }

  public boolean isDisableNotifications() {
    return disableNotifications;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    var that = (AbstractConnectionConfiguration) o;
    return Objects.equals(connectionId, that.connectionId)
      && Objects.equals(disableNotifications, that.disableNotifications);
  }

  @Override
  public int hashCode() {
    return Objects.hash(connectionId);
  }
}
