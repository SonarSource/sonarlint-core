/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.system;

public class ServerStatusInfo {
  private final String id;
  private final String status;
  private final String version;

  public ServerStatusInfo(String id, String status, String version) {
    this.id = id;
    this.status = status;
    this.version = version;
  }

  public String getId() {
    return id;
  }

  public String getStatus() {
    return status;
  }

  public String getVersion() {
    return version;
  }

  public boolean isUp() {
    return "UP".equals(status);
  }
}
