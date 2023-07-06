/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.clientapi.client.info;

public class GetClientInfoResponse {

  /**
   * The description of the client. For clients that support multiple instances, the description should be specific enough to identify the instance
   * (example: Eclipse Workspace, IntelliJ flavor, ...). Still be careful to not expose sensitive data, as the content may be accessed externally.
   */
  private final String description;

  public GetClientInfoResponse(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}
