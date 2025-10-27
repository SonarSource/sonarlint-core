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
package org.sonarsource.sonarlint.core.serverapi.users;

import com.google.gson.Gson;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

public class UsersApi {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ServerApiHelper helper;

  public UsersApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  /**
   * Fetch the current user info on SonarQube Cloud (SQC) using api/users/current.
   * Returns null on SonarQube Server or if the response cannot be parsed.
   */
  public String getCurrentUserId(SonarLintCancelMonitor cancelMonitor) {
    if (!helper.isSonarCloud()) {
      return null;
    }
    try (var response = helper.get("/api/users/current", cancelMonitor)) {
      var body = response.bodyAsString();
      try {
        var userResponse = new Gson().fromJson(body, CurrentUserResponse.class);
        return userResponse == null ? null : userResponse.id;
      } catch (Exception e) {
        LOG.error("Error while parsing /api/users/current response", e);
        return null;
      }
    }
  }

  private static class CurrentUserResponse {
    String id;
  }
}


