/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.serverconnection;

import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;

public class UserSynchronizer {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ConnectionStorage storage;

  public UserSynchronizer(ConnectionStorage storage) {
    this.storage = storage;
  }

  /**
   * Fetches and stores the user id from the server.
   * Available on SonarQube Cloud and SonarQube Server 2025.6+.
   */
  public void synchronize(ServerApi serverApi, SonarLintCancelMonitor cancelMonitor) {
    try {
      var userId = serverApi.users().getCurrentUserId(cancelMonitor);
      if (userId != null && !userId.trim().isEmpty()) {
        storage.user().store(userId.trim());
      }
    } catch (Exception e) {
      LOG.warn("Failed to synchronize user id from server: {}", e.getMessage());
    }
  }
}


