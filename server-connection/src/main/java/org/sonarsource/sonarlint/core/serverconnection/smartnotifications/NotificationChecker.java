/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection.smartnotifications;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.developers.DevelopersApi;

class NotificationChecker {
  private final DevelopersApi developersApi;

  NotificationChecker(ServerApiHelper serverApiHelper) {
    this.developersApi = new ServerApi(serverApiHelper).developers();
  }

  /**
   * Get all notification events for a set of projects after a given timestamp.
   * Returns an empty list if an error occurred making the request or parsing the response.
   */
  public List<ServerNotification> request(Map<String, ZonedDateTime> projectTimestamps) {
    return developersApi.getEvents(projectTimestamps)
      .stream().map(e -> new ServerNotification(
        e.getCategory(),
        e.getMessage(),
        e.getLink(),
        e.getProjectKey(),
        e.getTime()))
      .collect(Collectors.toList());
  }

  /**
   * Checks whether a server supports notifications. Throws exception is the server can't be contacted.
   */
  public boolean isSupported() {
    return developersApi.isSupported();
  }
}
